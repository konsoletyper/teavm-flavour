/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.templates.emitting;

import java.util.List;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomComponentTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.tree.DirectiveBinding;
import org.teavm.flavour.templates.tree.DirectiveVariableBinding;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class FragmentEmitter {
    EmitContext context;

    public FragmentEmitter(EmitContext context) {
        this.context = context;
    }

    public String emitTemplate(DirectiveBinding directive, List<TemplateNode> fragment) {
        ValueType ownerType = context.currentType();
        ValueType componentType = directive != null ? ValueType.object(directive.getClassName()) : null;
        ClassHolder cls = new ClassHolder(context.generateTypeName("Fragment"));
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(Fragment.class.getName());

        if (directive != null) {
            FieldHolder componentField = new FieldHolder("component");
            componentField.setType(componentType);
            componentField.setLevel(AccessLevel.PUBLIC);
            cls.addField(componentField);
        }
        context.addConstructor(cls, null);

        MethodHolder method = new MethodHolder("create", ValueType.parse(Component.class));
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, context.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);

        String workerCls = emitWorkerClass(directive, fragment);
        ValueEmitter result = pe.construct(workerCls, thisVar.getField("this$owner", ownerType));
        if (directive != null) {
            result.setField("component", thisVar.getField("component", componentType));
        }
        result.returnValue();

        cls.addMethod(method);
        //context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private String emitWorkerClass(DirectiveBinding directive, List<TemplateNode> fragment) {
        ClassHolder cls = new ClassHolder(context.generateTypeName("DomComponent"));
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(DomComponentTemplate.class.getName());
        context.addConstructor(cls, null);
        context.pushBoundVars();

        context.classStack.add(cls.getName());

        if (directive != null) {
            for (DirectiveVariableBinding varBinding : directive.getVariables()) {
                context.addVariable(varBinding.getName(), convertValueType(varBinding.getValueType()));
            }
        }

        emitBuildDomMethod(cls, fragment);
        emitUpdateMethod(cls, directive);

        if (directive != null) {
            for (DirectiveVariableBinding varBinding : directive.getVariables()) {
                context.removeVariable(varBinding.getName());
            }
        }
        context.classStack.remove(context.classStack.size() - 1);
        context.popBoundVars();

        //context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void emitBuildDomMethod(ClassHolder cls, List<TemplateNode> fragment) {
        MethodHolder buildDomMethod = new MethodHolder("buildDom", ValueType.object(DomBuilder.class.getName()),
                ValueType.VOID);
        buildDomMethod.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(buildDomMethod, context.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter builderVar = pe.var(1, DomBuilder.class);

        TemplateNodeEmitter nodeEmitter = new TemplateNodeEmitter(context, pe, thisVar, builderVar);
        for (TemplateNode node : fragment) {
            node.acceptVisitor(nodeEmitter);
        }
        pe.exit();

        cls.addMethod(buildDomMethod);
    }

    private void emitUpdateMethod(ClassHolder cls, DirectiveBinding directive) {
        MethodHolder method = new MethodHolder("update", ValueType.VOID);
        method.setLevel(AccessLevel.PROTECTED);
        ProgramEmitter pe = ProgramEmitter.create(method, context.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);

        if (directive != null) {
            ValueType componentType = ValueType.object(directive.getClassName());
            FieldHolder componentField = new FieldHolder("component");
            componentField.setType(componentType);
            componentField.setLevel(AccessLevel.PUBLIC);
            cls.addField(componentField);

            ValueEmitter componentVar = thisVar.getField("component", componentType);
            for (DirectiveVariableBinding varBinding : directive.getVariables()) {
                FieldHolder varField = new FieldHolder("var$" + varBinding.getName());
                varField.setLevel(AccessLevel.PUBLIC);
                varField.setType(convertValueType(varBinding.getValueType()));
                cls.addField(varField);

                ValueEmitter varValue = componentVar.invokeVirtual(varBinding.getMethodName(),
                        convertValueType(varBinding.getRawValueType()))
                        .cast(varField.getType());
                thisVar.setField(varField.getName(), varValue);
            }
        }

        pe.exit();

        cls.addMethod(method);
    }

    private ValueType convertValueType(org.teavm.flavour.expr.type.ValueType type) {
        if (type instanceof Primitive) {
            switch (((Primitive) type).getKind()) {
                case BOOLEAN:
                    return ValueType.BOOLEAN;
                case CHAR:
                    return ValueType.CHARACTER;
                case BYTE:
                    return ValueType.BYTE;
                case SHORT:
                    return ValueType.SHORT;
                case INT:
                    return ValueType.INTEGER;
                case LONG:
                    return ValueType.LONG;
                case FLOAT:
                    return ValueType.FLOAT;
                case DOUBLE:
                    return ValueType.DOUBLE;
                default:
                    throw new AssertionError();
            }
        } else if (type instanceof GenericClass) {
            return ValueType.object(((GenericClass) type).getName());
        } else if (type instanceof GenericArray) {
            return ValueType.arrayOf(convertValueType(((GenericArray) type).getElementType()));
        } else if (type instanceof GenericReference) {
            TypeVar typeVar = ((GenericReference) type).getVar();
            if (typeVar.getUpperBound() == null) {
                return ValueType.object("java.lang.Object");
            } else {
                return convertValueType(typeVar.getUpperBound());
            }
        } else {
            throw new AssertionError("Unsupported type: " + type);
        }
    }
}
