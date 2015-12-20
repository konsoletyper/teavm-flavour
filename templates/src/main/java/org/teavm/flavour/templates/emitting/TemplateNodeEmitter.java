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
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectMethod;
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
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.flavour.templates.tree.TemplateNodeVisitor;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class TemplateNodeEmitter implements TemplateNodeVisitor {
    private EmitContext context;
    Emitter<?> em;
    private Value<DomBuilder> builder;

    public TemplateNodeEmitter(EmitContext context, Emitter<?> em, Value<DomBuilder> builder) {
        this.context = context;
        this.em = em;
        this.builder = builder;
    }

    @Override
    public void visit(DOMElement node) {
        boolean hasInnerDirectives = false;
        for (TemplateNode child : node.getChildNodes()) {
            if (child instanceof DirectiveBinding) {
                hasInnerDirectives = true;
            }
        }

        String tagName = node.getName();
        if (hasInnerDirectives) {
            builder = em.emit(() -> builder.get().openSlot(tagName));
        } else {
            builder = em.emit(() -> builder.get().open(tagName));
        }

        for (DOMAttribute attr : node.getAttributes()) {
            String attrName = attr.getName();
            String attrValue = attr.getValue();
            builder = em.emit(() -> builder.get().attribute(attrName, attrValue));
        }

        for (AttributeDirectiveBinding binding : node.getAttributeDirectives()) {
            emitAttributeDirective(binding);
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        builder = em.emit(() -> builder.get().close());
    }

    @Override
    public void visit(DOMText node) {
        String value = node.getValue();
        builder = em.emit(() -> builder.get().text(value));
    }

    @Override
    public void visit(DirectiveBinding node) {
        ReflectClass<?> componentType = em.getContext().findClass(node.getClassName());
        ReflectMethod ctor = componentType.getJMethod("<init>", Slot.class);
        Value<Component> component = em.emit(() -> (Component) ctor.construct(Slot.create()));

        for (DirectiveFunctionBinding computation : node.getComputations()) {
            emitFunction(computation, em, component);
        }

        if (node.getDirectiveNameMethodName() != null) {
            emitDirectiveName(node.getDirectiveNameMethodName(), node.getName(), em, component, componentType);
        }

        if (node.getContentMethodName() != null) {
            emitContent(node, em, component);
        }

        builder = em.emit(() -> builder.get().add(component.get()));
    }

    private Value<Modifier> emitAttributeDirective(AttributeDirectiveBinding binding) {
        ReflectClass<Renderable> directiveClass = em.getContext()
                .findClass(binding.getClassName())
                .asSubclass(Renderable.class);
        ReflectMethod ctor = directiveClass.getJMethod("<init>", HTMLElement.class);

        return em.proxy(Modifier.class, (proxyEm, instance, method, args) -> {
            Value<HTMLElement> elem = proxyEm.emit(() -> (HTMLElement) args[0]);
            Value<Renderable> result = proxyEm.emit(() -> (Renderable) ctor.construct(elem.get()));

            for (DirectiveFunctionBinding function : binding.getFunctions()) {
                emitFunction(function, proxyEm, result);
            }
            if (binding.getDirectiveNameMethodName() != null) {
                emitDirectiveName(binding.getDirectiveNameMethodName(), binding.getName(), proxyEm, result,
                        directiveClass);
            }

            proxyEm.returnValue(result);
        });
    }

    private void emitFunction(DirectiveFunctionBinding function, Emitter<?> em, Value<? extends Object> component) {
        ExprPlanEmitter exprEmitter = new ExprPlanEmitter(context);
        LambdaPlan plan = function.getPlan();
        ValueType[] signature = MethodDescriptor.parseSignature(function.getPlan().getMethodDesc());
        exprEmitter.emit(plan, signature[signature.length - 1] == ValueType.VOID);
        Value<Object> functionInstance = exprEmitter.var;

        ReflectClass<?> cls = em.getContext().findClass(function.getMethodOwner());
        ReflectClass<?> lambdaCls = em.getContext().findClass(function.getLambdaType());
        ReflectMethod setter = cls.getMethod(function.getMethodName(), lambdaCls);
        em.emit(() -> setter.invoke(component, functionInstance));
    }

    private void emitContent(DirectiveBinding directive, Emitter<?> em, ValueEmitter componentVar) {
        String contentClass = new FragmentEmitter(context).emitTemplate(em, directive, directive.getContentNodes());
        ValueEmitter fragmentVar = pe.construct(contentClass, thisVar);
        fragmentVar.setField("component", componentVar);
        componentVar.invokeVirtual(directive.getContentMethodName(), fragmentVar.cast(Fragment.class));
    }

    private void emitDirectiveName(String methodName, String directiveName, Emitter<?> em,
            Value<? extends Object> component, ReflectClass<?> componentType) {
        ReflectMethod setter = componentType.getJMethod(methodName, String.class);
        em.emit(() -> setter.invoke(component.get(), directiveName));
    }
}
