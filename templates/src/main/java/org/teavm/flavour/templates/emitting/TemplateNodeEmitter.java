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
import org.teavm.flavour.expr.plan.LambdaPlan;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Modifier;
import org.teavm.flavour.templates.Renderable;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.tree.AttributeDirectiveBinding;
import org.teavm.flavour.templates.tree.DOMAttribute;
import org.teavm.flavour.templates.tree.DOMElement;
import org.teavm.flavour.templates.tree.DOMText;
import org.teavm.flavour.templates.tree.DirectiveBinding;
import org.teavm.flavour.templates.tree.DirectiveFunctionBinding;
import org.teavm.flavour.templates.tree.DirectiveVariableBinding;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.flavour.templates.tree.TemplateNodeVisitor;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class TemplateNodeEmitter implements TemplateNodeVisitor {
    private EmitContext context;
    ProgramEmitter pe;
    private ValueEmitter thisVar;
    private ValueEmitter builderVar;

    public TemplateNodeEmitter(EmitContext context, ProgramEmitter pe, ValueEmitter thisVar, ValueEmitter builderVar) {
        this.context = context;
        this.pe = pe;
        this.thisVar = thisVar;
        this.builderVar = builderVar;
    }

    @Override
    public void visit(DOMElement node) {
        boolean hasInnerDirectives = false;
        for (TemplateNode child : node.getChildNodes()) {
            if (child instanceof DirectiveBinding) {
                hasInnerDirectives = true;
            }
        }

        context.location(pe, node.getLocation());
        builderVar = builderVar.invokeVirtual(!hasInnerDirectives ? "open" : "openSlot", DomBuilder.class,
                pe.constant(node.getName()));

        for (DOMAttribute attr : node.getAttributes()) {
            context.location(pe, attr.getLocation());
            builderVar = builderVar.invokeVirtual("attribute", DomBuilder.class, pe.constant(attr.getName()),
                    pe.constant(attr.getValue()));
        }

        for (AttributeDirectiveBinding binding : node.getAttributeDirectives()) {
            context.location(pe, node.getLocation());
            emitAttributeDirective(binding);
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        builderVar = builderVar.invokeVirtual("close", DomBuilder.class);
    }

    @Override
    public void visit(DOMText node) {
        context.location(pe, node.getLocation());
        builderVar = builderVar.invokeVirtual(new MethodReference(DomBuilder.class, "text", String.class,
                DomBuilder.class), pe.constant(node.getValue()));
    }

    @Override
    public void visit(DirectiveBinding node) {
        String className = emitComponentFragmentClass(node);
        context.location(pe, node.getLocation());
        ValueEmitter fragment = pe.construct(className, thisVar);
        builderVar = builderVar.invokeVirtual("add", DomBuilder.class, fragment.cast(Fragment.class));
    }

    private void emitAttributeDirective(AttributeDirectiveBinding binding) {
        String className = emitModifierClass(binding);
        context.location(pe, binding.getLocation());
        ValueEmitter directive = pe.construct(className, thisVar);
        builderVar = builderVar.invokeVirtual("add", DomBuilder.class, directive.cast(Modifier.class));
    }

    private String emitComponentFragmentClass(DirectiveBinding node) {
        String className = context.dependencyAgent.generateClassName();
        ClassHolder fragmentCls = new ClassHolder(className);
        fragmentCls.setParent(Object.class.getName());
        fragmentCls.getInterfaces().add(Fragment.class.getName());

        emitDirectiveFields(fragmentCls, node.getVariables());
        context.addConstructor(fragmentCls, node.getLocation());
        emitDirectiveWorker(fragmentCls, node);

        context.dependencyAgent.submitClass(fragmentCls);
        return className;
    }

    private String emitModifierClass(AttributeDirectiveBinding node) {
        String className = context.dependencyAgent.generateClassName();
        ClassHolder fragmentCls = new ClassHolder(className);
        fragmentCls.setParent(Object.class.getName());
        fragmentCls.getInterfaces().add(Modifier.class.getName());

        emitDirectiveFields(fragmentCls, node.getVariables());
        context.addConstructor(fragmentCls, node.getLocation());
        emitAttributeDirectiveWorker(fragmentCls, node);

        context.dependencyAgent.submitClass(fragmentCls);
        return className;
    }

    private void emitDirectiveFields(ClassHolder cls, List<DirectiveVariableBinding> vars) {
        for (DirectiveVariableBinding varBinding : vars) {
            FieldHolder field = new FieldHolder("var$" + varBinding.getName());
            field.setType(convertValueType(varBinding.getValueType()));
            field.setLevel(AccessLevel.PUBLIC);
            cls.addField(field);
        }
    }

    private void emitDirectiveWorker(ClassHolder cls, DirectiveBinding directive) {
        MethodHolder method = new MethodHolder("create", ValueType.parse(Component.class));
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, context.dependencyAgent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);

        context.location(pe, directive.getLocation());
        ValueEmitter componentVar = pe.construct(directive.getClassName(),
                pe.invoke(Slot.class, "create", Slot.class));

        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            emitVariable(cls, varBinding, pe, thisVar, componentVar);
        }

        for (DirectiveFunctionBinding computation : directive.getComputations()) {
            emitFunction(cls, computation, pe, thisVar, componentVar);
        }

        context.classStack.add(cls.getName());

        if (directive.getContentMethodName() != null) {
            emitContent(directive, pe, thisVar, componentVar);
        }

        if (directive.getDirectiveNameMethodName() != null) {
            emitDirectiveName(directive.getClassName(), directive.getDirectiveNameMethodName(), directive.getName(),
                    pe, componentVar);
        }

        componentVar.returnValue();

        cls.addMethod(method);
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            context.removeVariable(varBinding.getName());
        }
        context.classStack.remove(context.classStack.size() - 1);
    }

    private void emitAttributeDirectiveWorker(ClassHolder cls, AttributeDirectiveBinding directive) {
        MethodHolder method = new MethodHolder("apply", ValueType.parse(HTMLElement.class),
                ValueType.parse(Renderable.class));
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, context.dependencyAgent.getClassSource());
        context.location(pe, directive.getLocation());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter elemVar = pe.var(1, HTMLElement.class);
        ValueEmitter componentVar = pe.construct(directive.getClassName(), elemVar.cast(HTMLElement.class));

        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            emitVariable(cls, varBinding, pe, thisVar, componentVar);
        }

        for (DirectiveFunctionBinding function : directive.getFunctions()) {
            emitFunction(cls, function, pe, thisVar, componentVar);
        }

        if (directive.getDirectiveNameMethodName() != null) {
            emitDirectiveName(directive.getClassName(), directive.getDirectiveNameMethodName(), directive.getName(),
                    pe, componentVar);
        }

        componentVar.returnValue();

        cls.addMethod(method);
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            context.removeVariable(varBinding.getName());
        }
    }

    private void emitVariable(ClassHolder cls, DirectiveVariableBinding varBinding, ProgramEmitter pe,
            ValueEmitter thisVar, ValueEmitter componentVar) {
        context.classStack.add(cls.getName());
        String varClass = emitVariableClass(cls, varBinding);
        context.classStack.remove(context.classStack.size() - 1);

        componentVar.invokeVirtual(varBinding.getMethodName(), pe.construct(varClass, thisVar)
                .cast(org.teavm.flavour.templates.Variable.class));

        context.addVariable(varBinding.getName(), convertValueType(varBinding.getValueType()));
    }

    private void emitFunction(ClassHolder cls, DirectiveFunctionBinding function, ProgramEmitter pe,
            ValueEmitter thisVar, ValueEmitter componentVar) {
        ExprPlanEmitter exprEmitter = new ExprPlanEmitter(context);
        exprEmitter.thisClassName = cls.getName();
        exprEmitter.thisVar = thisVar;
        exprEmitter.pe = pe;
        LambdaPlan plan = function.getPlan();
        ValueType[] signature = MethodDescriptor.parseSignature(function.getPlan().getMethodDesc());
        exprEmitter.emit(plan, signature[signature.length - 1] == ValueType.VOID);

        MethodReference method = new MethodReference(function.getMethodOwner(), function.getMethodName(),
                ValueType.object(function.getLambdaType()), ValueType.VOID);
        componentVar.invokeVirtual(method, exprEmitter.var);
    }

    private void emitContent(DirectiveBinding directive, ProgramEmitter pe, ValueEmitter thisVar,
            ValueEmitter componentVar) {
        String contentClass = new FragmentEmitter(context).emitTemplate(directive.getContentNodes());
        componentVar.invokeVirtual(directive.getContentMethodName(), pe.construct(contentClass, thisVar)
                .cast(Fragment.class));
    }

    private void emitDirectiveName(String className, String methodName, String directiveName, ProgramEmitter pe,
            ValueEmitter componentVar) {
        MethodReference setter = new MethodReference(className, methodName, ValueType.parse(String.class),
                ValueType.VOID);
        componentVar.invokeVirtual(setter, pe.constant(directiveName));
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
