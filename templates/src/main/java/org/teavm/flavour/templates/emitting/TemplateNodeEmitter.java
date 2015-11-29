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

import org.teavm.flavour.expr.plan.LambdaPlan;
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
        context.location(pe, node.getLocation());
        ValueEmitter componentVar = emitComponentInstance(node);
        builderVar = builderVar.invokeVirtual("add", DomBuilder.class, componentVar.cast(Component.class));
    }

    private void emitAttributeDirective(AttributeDirectiveBinding binding) {
        String className = emitModifierClass(binding);
        context.location(pe, binding.getLocation());
        ValueEmitter directive = pe.construct(className, thisVar);
        builderVar = builderVar.invokeVirtual("add", DomBuilder.class, directive.cast(Modifier.class));
    }

    private ValueEmitter emitComponentInstance(DirectiveBinding node) {
        ValueEmitter componentVar = pe.construct(node.getClassName(), pe.invoke(Slot.class, "create", Slot.class));

        for (DirectiveFunctionBinding computation : node.getComputations()) {
            emitFunction(context.currentTypeName(), computation, pe, thisVar, componentVar);
        }

        if (node.getDirectiveNameMethodName() != null) {
            emitDirectiveName(node.getClassName(), node.getDirectiveNameMethodName(), node.getName(),
                    pe, componentVar);
        }

        if (node.getContentMethodName() != null) {
            emitContent(node, pe, thisVar, componentVar);
        }

        return componentVar;
    }

    private String emitModifierClass(AttributeDirectiveBinding node) {
        String className = context.generateTypeName("Modifier");
        ClassHolder fragmentCls = new ClassHolder(className);
        fragmentCls.setParent(Object.class.getName());
        fragmentCls.getInterfaces().add(Modifier.class.getName());

        context.addConstructor(fragmentCls, node.getLocation());
        emitAttributeDirectiveWorker(fragmentCls, node);

        //context.dependencyAgent.submitClass(fragmentCls);
        return className;
    }

    private void emitAttributeDirectiveWorker(ClassHolder cls, AttributeDirectiveBinding directive) {
        MethodHolder method = new MethodHolder("apply", ValueType.parse(HTMLElement.class),
                ValueType.parse(Renderable.class));
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, context.getClassSource());
        context.location(pe, directive.getLocation());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter elemVar = pe.var(1, HTMLElement.class);
        ValueEmitter componentVar = pe.construct(directive.getClassName(), elemVar.cast(HTMLElement.class));

        context.classStack.add(cls.getName());
        for (DirectiveFunctionBinding function : directive.getFunctions()) {
            emitFunction(cls.getName(), function, pe, thisVar, componentVar);
        }

        if (directive.getDirectiveNameMethodName() != null) {
            emitDirectiveName(directive.getClassName(), directive.getDirectiveNameMethodName(), directive.getName(),
                    pe, componentVar);
        }

        componentVar.returnValue();

        context.classStack.remove(context.classStack.size() - 1);
        cls.addMethod(method);
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            context.removeVariable(varBinding.getName());
        }
    }

    private void emitFunction(String className, DirectiveFunctionBinding function, ProgramEmitter pe,
            ValueEmitter thisVar, ValueEmitter componentVar) {
        ExprPlanEmitter exprEmitter = new ExprPlanEmitter(context);
        exprEmitter.thisClassName = className;
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
        String contentClass = new FragmentEmitter(context).emitTemplate(directive, directive.getContentNodes());
        ValueEmitter fragmentVar = pe.construct(contentClass, thisVar);
        fragmentVar.setField("component", componentVar);
        componentVar.invokeVirtual(directive.getContentMethodName(), fragmentVar.cast(Fragment.class));
    }

    private void emitDirectiveName(String className, String methodName, String directiveName, ProgramEmitter pe,
            ValueEmitter componentVar) {
        MethodReference setter = new MethodReference(className, methodName, ValueType.parse(String.class),
                ValueType.VOID);
        componentVar.invokeVirtual(setter, pe.constant(directiveName));
    }
}
