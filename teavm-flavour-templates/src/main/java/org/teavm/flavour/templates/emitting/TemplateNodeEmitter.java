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
import org.teavm.dom.html.HTMLElement;
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
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

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
        MethodReference openMethod = new MethodReference(DomBuilder.class, !hasInnerDirectives ? "open" : "openSlot",
                String.class, DomBuilder.class);
        MethodReference attrMethod = new MethodReference(DomBuilder.class, "attribute", String.class, String.class,
                DomBuilder.class);
        MethodReference closeMethod = new MethodReference(DomBuilder.class, "close", DomBuilder.class);

        context.location(pe, node.getLocation());
        builderVar = builderVar.invokeVirtual(openMethod, pe.constant(node.getName()));

        for (DOMAttribute attr : node.getAttributes()) {
            context.location(pe, attr.getLocation());
            builderVar = builderVar.invokeVirtual(attrMethod,
                    pe.constant(attr.getName()), pe.constant(attr.getValue()));
        }

        for (AttributeDirectiveBinding binding : node.getAttributeDirectives()) {
            context.location(pe, node.getLocation());
            emitAttributeDirective(binding);
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        builderVar = builderVar.invokeVirtual(closeMethod);
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
        String ownerType = context.classStack.get(context.classStack.size() - 1);

        context.location(pe, node.getLocation());
        ValueEmitter fragment = pe.construct(new MethodReference(className, "<init>",
                ValueType.object(ownerType), ValueType.VOID), thisVar);
        builderVar = builderVar.invokeVirtual(new MethodReference(DomBuilder.class, "add", Fragment.class,
                DomBuilder.class), fragment);
    }

    private void emitAttributeDirective(AttributeDirectiveBinding binding) {
        String className = emitModifierClass(binding);

        String ownerType = context.classStack.get(context.classStack.size() - 1);
        context.location(pe, binding.getLocation());
        ValueEmitter directive = pe.construct(new MethodReference(className, "<init>", ValueType.object(ownerType),
                ValueType.VOID), thisVar);
        builderVar = builderVar.invokeVirtual(new MethodReference(DomBuilder.class, "add", Modifier.class,
                DomBuilder.class), directive);
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
        ProgramEmitter pe = ProgramEmitter.create(method);
        ValueEmitter thisVar = pe.wrapNew();

        MethodReference createSlotMethod = new MethodReference(Slot.class, "create", Slot.class);
        context.location(pe, directive.getLocation());
        ValueEmitter componentVar = pe.construct(new MethodReference(directive.getClassName(), "<init>",
                ValueType.parse(Slot.class), ValueType.VOID), pe.invoke(createSlotMethod));

        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            emitVariable(cls, varBinding, pe, thisVar, componentVar);
        }

        for (DirectiveFunctionBinding computation : directive.getComputations()) {
            emitFunction(cls, computation, pe, thisVar, componentVar);
        }

        context.classStack.add(cls.getName());

        if (directive.getContentMethodName() != null) {
            emitContent(cls, directive, pe, thisVar, componentVar);
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
        ProgramEmitter pe = ProgramEmitter.create(method);
        context.location(pe, directive.getLocation());
        ValueEmitter thisVar = pe.wrapNew();
        ValueEmitter elemVar = pe.wrapNew();

        MethodReference constructor = new MethodReference(directive.getClassName(), "<init>",
                ValueType.parse(HTMLElement.class), ValueType.VOID);
        ValueEmitter componentVar = pe.construct(constructor, elemVar);

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

        MethodReference constructor = new MethodReference(varClass, "<init>", ValueType.object(cls.getName()),
                ValueType.VOID);
        MethodReference setMethod = new MethodReference(varBinding.getMethodOwner(), varBinding.getMethodName(),
                ValueType.parse(org.teavm.flavour.templates.Variable.class), ValueType.VOID);

        componentVar.invokeVirtual(setMethod, pe.construct(constructor, thisVar));

        context.addVariable(varBinding.getName(), convertValueType(varBinding.getValueType()));
    }

    private String emitVariableClass(ClassHolder owner, DirectiveVariableBinding varBinding) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(org.teavm.flavour.templates.Variable.class.getName());

        context.addConstructor(cls, null);

        MethodHolder setMethod = new MethodHolder("set", ValueType.parse(Object.class), ValueType.VOID);
        setMethod.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(setMethod);
        ValueEmitter thisVar = pe.wrapNew();
        ValueEmitter valueVar = pe.wrapNew();
        ValueType varType = convertValueType(varBinding.getValueType());

        FieldReference ownerField = new FieldReference(cls.getName(), "this$owner");
        ValueType ownerType = ValueType.object(owner.getName());
        FieldReference varField = new FieldReference(owner.getName(), "var$" + varBinding.getName());

        if (!varBinding.getValueType().equals(new GenericClass("java.lang.Object"))) {
            valueVar = valueVar.cast(varType);
        }
        thisVar.getField(ownerField, ownerType).setField(varField, varType, valueVar);
        pe.exit();

        cls.addMethod(setMethod);
        context.dependencyAgent.submitClass(cls);
        return cls.getName();
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

    private void emitContent(ClassHolder cls, DirectiveBinding directive, ProgramEmitter pe, ValueEmitter thisVar,
            ValueEmitter componentVar) {
        String contentClass = new FragmentEmitter(context).emitTemplate(directive.getContentNodes());

        MethodReference constructor = new MethodReference(contentClass, "<init>", ValueType.object(cls.getName()),
                ValueType.VOID);
        MethodReference setter = new MethodReference(directive.getClassName(), directive.getContentMethodName(),
                ValueType.parse(Fragment.class), ValueType.VOID);
        componentVar.invokeVirtual(setter, pe.construct(constructor, thisVar));
    }

    private void emitDirectiveName(String className, String methodName, String directiveName, ProgramEmitter pe,
            ValueEmitter componentVar) {
        MethodReference setter = new MethodReference(className, methodName, ValueType.parse(String.class),
                ValueType.VOID);
        componentVar.invokeVirtual(setter, pe.constant(directiveName));
    }

    private ValueType convertValueType(org.teavm.flavour.expr.type.ValueType type) {
        if (type instanceof Primitive) {
            switch (((Primitive)type).getKind()) {
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
            return ValueType.object(((GenericClass)type).getName());
        } else if (type instanceof GenericArray) {
            return ValueType.arrayOf(convertValueType(((GenericArray)type).getElementType()));
        } else if (type instanceof GenericReference) {
            TypeVar typeVar = ((GenericReference)type).getVar();
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
