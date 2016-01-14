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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.teavm.flavour.templates.tree.DirectiveVariableBinding;
import org.teavm.flavour.templates.tree.NestedDirectiveBinding;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.flavour.templates.tree.TemplateNodeVisitor;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;

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
        context.location(em, node.getLocation());

        boolean hasInnerDirectives = node.getChildNodes().stream()
                .anyMatch(child -> child instanceof DirectiveBinding);
        String tagName = node.getName();
        {
            Value<DomBuilder> tmpBuilder = builder;
            if (hasInnerDirectives) {
                builder = em.emit(() -> tmpBuilder.get().openSlot(tagName));
            } else {
                builder = em.emit(() -> tmpBuilder.get().open(tagName));
            }
        }

        for (DOMAttribute attr : node.getAttributes()) {
            String attrName = attr.getName();
            String attrValue = attr.getValue();
            Value<DomBuilder> tmpBuilder = builder;
            context.location(em, attr.getLocation());
            builder = em.emit(() -> tmpBuilder.get().attribute(attrName, attrValue));
        }

        for (AttributeDirectiveBinding binding : node.getAttributeDirectives()) {
            Value<DomBuilder> tmpBuilder = builder;
            Value<Modifier> modifier = emitAttributeDirective(binding);
            builder = em.emit(() -> tmpBuilder.get().add(modifier.get()));
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        {
            Value<DomBuilder> tmpBuilder = builder;
            context.endLocation(em, node.getLocation());
            builder = em.emit(() -> tmpBuilder.get().close());
        }
    }

    @Override
    public void visit(DOMText node) {
        context.location(em, node.getLocation());
        String value = node.getValue();
        Value<DomBuilder> tmpBuilder = builder;
        builder = em.emit(() -> tmpBuilder.get().text(value));
    }

    @Override
    public void visit(DirectiveBinding node) {
        context.location(em, node.getLocation());
        ReflectClass<?> componentType = em.getContext().findClass(node.getClassName());
        ReflectMethod ctor = componentType.getJMethod("<init>", Slot.class);
        Value<Component> component = em.emit(() -> (Component) ctor.construct(Slot.create()));
        List<NestedComponentInstance> nestedInstances = emitDirective(node, component, component);

        context.pushBoundVars();
        Map<String, Value<VariableImpl>> variables = new HashMap<>();
        emitVariables(node, em, variables);
        emitDirectiveContent(node, node, component, component, variables);
        for (NestedComponentInstance nestedInstance : nestedInstances) {
            emitDirectiveContent(node, nestedInstance.node, component, nestedInstance.instance, variables);
        }
        context.popBoundVars();

        Value<DomBuilder> tmpBuilder = builder;
        builder = em.emit(() -> tmpBuilder.get().add(component.get()));
    }

    private List<NestedComponentInstance> emitDirective(DirectiveBinding node, Value<? extends Object> component,
            Value<? extends Object> root) {
        ReflectClass<?> componentType = em.getContext().findClass(node.getClassName());

        for (DirectiveFunctionBinding computation : node.getComputations()) {
            emitFunction(computation, em, component);
        }

        if (node.getDirectiveNameMethodName() != null) {
            context.location(em, node.getLocation());
            emitDirectiveName(node.getDirectiveNameMethodName(), node.getName(), em, component, componentType);
        }

        List<NestedComponentInstance> nestedInstances = new ArrayList<>();
        for (NestedDirectiveBinding nestedDirective : node.getNestedDirectives()) {
            nestedInstances.addAll(emitNestedDirective(nestedDirective, em, component, root));
        }
        return nestedInstances;
    }

    private void emitDirectiveContent(DirectiveBinding rootNode, DirectiveBinding node,
            Value<? extends Object> component, Value<? extends Object> root, Map<String,
            Value<VariableImpl>> variables) {
        context.location(em, node.getLocation());
        if (node.getContentMethodName() != null) {
            ReflectClass<?> componentType = em.getContext().findClass(node.getClassName());
            Value<Fragment> contentFragment = new FragmentEmitter(context)
                    .emitTemplate(em, rootNode, root, node.getContentNodes(), variables);
            ReflectMethod setter = componentType.getJMethod(node.getContentMethodName(), Fragment.class);
            context.location(em, node.getLocation());
            em.emit(() -> setter.invoke(component, contentFragment));
        }
    }

    private void emitVariables(DirectiveBinding directive, Emitter<?> em, Map<String, Value<VariableImpl>> variables) {
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            Value<VariableImpl> variableImpl = em.emit(() -> new VariableImpl());
            variables.put(varBinding.getName(), variableImpl);
            context.addVariable(varBinding.getName(), innerEm -> {
                Value<VariableImpl> tmp = variableImpl;
                return innerEm.emit(() -> tmp.get().value);
            });
        }
        for (NestedDirectiveBinding nestedBinding : directive.getNestedDirectives()) {
            for (DirectiveBinding nestedDirective : nestedBinding.getDirectives()) {
                emitVariables(nestedDirective, em, variables);
            }
        }
    }

    private Value<Modifier> emitAttributeDirective(AttributeDirectiveBinding binding) {
        context.location(em, binding.getLocation());
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
                context.location(em, binding.getLocation());
                emitDirectiveName(binding.getDirectiveNameMethodName(), binding.getName(), proxyEm, result,
                        directiveClass);
            }

            context.location(em, binding.getLocation());
            proxyEm.returnValue(result);
        });
    }

    private void emitFunction(DirectiveFunctionBinding function, Emitter<?> em, Value<? extends Object> component) {
        ExprPlanEmitter exprEmitter = new ExprPlanEmitter(context, em);
        LambdaPlan plan = function.getPlan();
        ValueType[] signature = MethodDescriptor.parseSignature(function.getPlan().getMethodDesc());
        exprEmitter.emit(plan, signature[signature.length - 1] == ValueType.VOID);
        Value<Object> functionInstance = exprEmitter.var;

        ReflectClass<?> cls = em.getContext().findClass(function.getMethodOwner());
        ReflectClass<?> lambdaCls = em.getContext().findClass(function.getLambdaType());
        ReflectMethod setter = cls.getMethod(function.getMethodName(), lambdaCls);
        em.emit(() -> setter.invoke(component, functionInstance));
    }

    private List<NestedComponentInstance> emitNestedDirective(NestedDirectiveBinding nested, Emitter<?> em,
            Value<? extends Object> component, Value<? extends Object> root) {
        List<NestedComponentInstance> instancesToFill = new ArrayList<>();
        ReflectClass<?> cls = em.getContext().findClass(nested.getMethodOwner());
        if (nested.isMultiple()) {
            ReflectMethod setter = cls.getJMethod(nested.getMethodName(), List.class);
            int capacity = nested.getDirectives().size();
            Value<List<Object>> list = em.emit(() -> new ArrayList<>(capacity));
            for (DirectiveBinding nestedDirective : nested.getDirectives()) {
                Value<Object> nestedComponent = emitNestedComponent(nestedDirective, em, root);
                em.emit(() -> list.get().add(nestedComponent));
                instancesToFill.add(new NestedComponentInstance(nestedDirective, nestedComponent, root));
            }
            em.emit(() -> setter.invoke(component, list));
        } else {
            ReflectClass<?> directiveType = em.getContext().findClass(nested.getDirectiveType());
            ReflectMethod setter = cls.getMethod(nested.getMethodName(), directiveType);
            Value<Object> nestedComponent = emitNestedComponent(nested.getDirectives().get(0), em, root);
            em.emit(() -> setter.invoke(component, nestedComponent));
            instancesToFill.add(new NestedComponentInstance(nested.getDirectives().get(0), nestedComponent, root));
        }
        return instancesToFill;
    }

    private Value<Object> emitNestedComponent(DirectiveBinding node, Emitter<?> em, Value<? extends Object> root) {
        context.location(em, node.getLocation());
        ReflectClass<?> componentType = em.getContext().findClass(node.getClassName());
        ReflectMethod ctor = componentType.getMethod("<init>");
        Value<Object> component = em.emit(() -> ctor.construct());
        emitDirective(node, component, root);
        return component;
    }

    private void emitDirectiveName(String methodName, String directiveName, Emitter<?> em,
            Value<? extends Object> component, ReflectClass<?> componentType) {
        ReflectMethod setter = componentType.getJMethod(methodName, String.class);
        em.emit(() -> setter.invoke(component.get(), directiveName));
    }

    static class NestedComponentInstance {
        DirectiveBinding node;
        Value<? extends Object> instance;
        Value<? extends Object> root;

        public NestedComponentInstance(DirectiveBinding node, Value<? extends Object> instance,
                Value<? extends Object> root) {
            this.node = node;
            this.instance = instance;
            this.root = root;
        }
    }
}
