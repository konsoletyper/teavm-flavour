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

import static org.teavm.metaprogramming.Metaprogramming.emit;
import static org.teavm.metaprogramming.Metaprogramming.exit;
import static org.teavm.metaprogramming.Metaprogramming.findClass;
import static org.teavm.metaprogramming.Metaprogramming.lazy;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.plan.LambdaPlan;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Modifier;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.Renderable;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.tree.AttributeComponentBinding;
import org.teavm.flavour.templates.tree.ComponentBinding;
import org.teavm.flavour.templates.tree.ComponentFunctionBinding;
import org.teavm.flavour.templates.tree.ComponentVariableBinding;
import org.teavm.flavour.templates.tree.DOMAttribute;
import org.teavm.flavour.templates.tree.DOMElement;
import org.teavm.flavour.templates.tree.DOMText;
import org.teavm.flavour.templates.tree.NestedComponentBinding;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.flavour.templates.tree.TemplateNodeVisitor;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectMethod;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;

class TemplateNodeEmitter implements TemplateNodeVisitor {
    private static final int COMPLEXITY_THRESHOLD = 20;

    private EmitContext context;
    private Value<DomBuilder> builder;
    private Value<DomBuilder> initialBuilder;
    private int complexity;

    TemplateNodeEmitter(EmitContext context, Value<DomBuilder> builder) {
        this.context = context;
        this.builder = builder;
        initialBuilder = builder;
    }

    @Override
    public void visit(DOMElement node) {
        context.location(node.getLocation());

        boolean hasInnerComponents = node.getChildNodes().stream()
                .anyMatch(child -> child instanceof ComponentBinding);
        String tagName = node.getName();
        {
            Value<DomBuilder> tmpBuilder = builder;
            if (hasInnerComponents) {
                updateBuilder(emit(() -> tmpBuilder.get().openSlot(tagName)));
            } else {
                updateBuilder(emit(() -> tmpBuilder.get().open(tagName)));
            }
        }

        for (DOMAttribute attr : node.getAttributes()) {
            String attrName = attr.getName();
            String attrValue = attr.getValue();
            Value<DomBuilder> tmpBuilder = builder;
            context.location(attr.getLocation());
            updateBuilder(emit(() -> tmpBuilder.get().attribute(attrName, attrValue)));
        }

        for (AttributeComponentBinding binding : node.getAttributeComponents()) {
            Value<DomBuilder> tmpBuilder = builder;
            Value<Modifier> modifier = emitAttributeComponent(binding);
            updateBuilder(emit(() -> tmpBuilder.get().add(modifier.get())));
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        {
            Value<DomBuilder> tmpBuilder = builder;
            context.endLocation(node.getLocation());
            updateBuilder(emit(() -> tmpBuilder.get().close()));
        }
    }

    @Override
    public void visit(DOMText node) {
        context.location(node.getLocation());
        String value = node.getValue();
        Value<DomBuilder> tmpBuilder = builder;
        updateBuilder(emit(() -> tmpBuilder.get().text(value)));
    }

    @Override
    public void visit(ComponentBinding node) {
        context.location(node.getLocation());
        ReflectClass<?> componentType = findClass(node.getClassName());
        ReflectMethod ctor = componentType.getJMethod("<init>", Slot.class);
        Value<Component> component = emit(() -> (Component) ctor.construct(Slot.create()));
        List<NestedComponentInstance> nestedInstances = emitElementComponent(node, component, component);

        context.pushBoundVars();
        List<TemplateVariable> variables = new ArrayList<>();
        Map<ComponentBinding, Value<?>> instances = nestedInstances.stream()
                .collect(Collectors.toMap(nested -> nested.node, nested -> nested.instance));
        instances.put(node, component);
        emitVariables(node, variables, instances);
        emitComponentContent(node, node, component, variables);
        for (NestedComponentInstance nestedInstance : nestedInstances) {
            emitComponentContent(node, nestedInstance.node, nestedInstance.instance, variables);
        }
        context.popBoundVars();

        Value<DomBuilder> tmpBuilder = builder;
        updateBuilder(emit(() -> tmpBuilder.get().add(component.get())));
    }

    private List<NestedComponentInstance> emitElementComponent(ComponentBinding node, Value<?> component,
            Value<?> root) {
        ReflectClass<?> componentType = findClass(node.getClassName());

        for (ComponentFunctionBinding computation : node.getComputations()) {
            emitFunction(computation, component);
        }

        if (node.getElementNameMethodName() != null) {
            context.location(node.getLocation());
            emitElementName(node.getElementNameMethodName(), node.getName(), component, componentType);
        }

        List<NestedComponentInstance> nestedInstances = new ArrayList<>();
        for (NestedComponentBinding nestedComponent : node.getNestedComponents()) {
            nestedInstances.addAll(emitNestedComponent(nestedComponent, component, root));
        }
        return nestedInstances;
    }

    private void emitComponentContent(ComponentBinding rootNode, ComponentBinding node, Value<?> component,
            List<TemplateVariable> variables) {
        context.location(node.getLocation());
        if (node.getContentMethodName() != null) {
            ReflectClass<?> componentType = findClass(node.getClassName());
            Value<Fragment> contentFragment = new FragmentEmitter(context)
                    .emitTemplate(rootNode, node.getContentNodes(), variables);
            ReflectMethod setter = componentType.getJMethod(node.getContentMethodName(), Fragment.class);
            context.location(node.getLocation());
            emit(() -> setter.invoke(component, contentFragment));
        }
    }

    private void emitVariables(ComponentBinding component, List<TemplateVariable> variables,
            Map<ComponentBinding, Value<?>> instances) {
        Value<?> instance = instances.get(component);
        ReflectClass<?> componentType = findClass(component.getClassName());
        for (ComponentVariableBinding varBinding : component.getVariables()) {
            ReflectMethod getter = componentType.getMethod(varBinding.getMethodName());
            Value<Object> source = lazy(() -> getter.invoke(instance.get()));
            variables.add(new TemplateVariable(varBinding.getName(), source));
        }
        for (NestedComponentBinding nestedBinding : component.getNestedComponents()) {
            for (ComponentBinding nestedComponent : nestedBinding.getComponents()) {
                emitVariables(nestedComponent, variables, instances);
            }
        }
    }

    private Value<Modifier> emitAttributeComponent(AttributeComponentBinding binding) {
        context.location(binding.getLocation());
        ReflectClass<Renderable> componentClass = findClass(binding.getClassName()).asSubclass(Renderable.class);
        ReflectMethod ctor = componentClass.getJMethod("<init>", ModifierTarget.class);

        return proxy(Modifier.class, (instance, method, args) -> {
            Value<ModifierTarget> elem = emit(() -> (ModifierTarget) args[0]);
            Value<Renderable> result = emit(() -> (Renderable) ctor.construct(elem.get()));

            for (ComponentFunctionBinding function : binding.getFunctions()) {
                emitFunction(function, result);
            }
            if (binding.getElementNameMethodName() != null) {
                context.location(binding.getLocation());
                emitElementName(binding.getElementNameMethodName(), binding.getName(), result, componentClass);
            }

            context.location(binding.getLocation());
            exit(() -> result.get());
        });
    }

    private void emitFunction(ComponentFunctionBinding function, Value<?> component) {
        ExprPlanEmitter exprEmitter = new ExprPlanEmitter(context);
        LambdaPlan plan = function.getPlan();
        ValueType[] signature = MethodDescriptor.parseSignature(function.getPlan().getMethodDesc());
        exprEmitter.emitLambda(plan, signature[signature.length - 1] == ValueType.VOID);
        Value<Object> functionInstance = exprEmitter.var;

        ReflectClass<?> cls = findClass(function.getMethodOwner());
        ReflectClass<?> lambdaCls = findClass(function.getLambdaType());
        ReflectMethod setter = cls.getMethod(function.getMethodName(), lambdaCls);
        emit(() -> setter.invoke(component, functionInstance));
    }

    private List<NestedComponentInstance> emitNestedComponent(NestedComponentBinding nested, Value<?> component,
            Value<?> root) {
        List<NestedComponentInstance> instancesToFill = new ArrayList<>();
        ReflectClass<?> cls = findClass(nested.getMethodOwner());
        if (nested.isMultiple()) {
            ReflectMethod setter = cls.getJMethod(nested.getMethodName(), List.class);
            int capacity = nested.getComponents().size();
            Value<List<Object>> list = emit(() -> new ArrayList<>(capacity));
            for (ComponentBinding nestedComponentBinding : nested.getComponents()) {
                Value<Object> nestedComponent = emitNestedComponent(nestedComponentBinding, root, instancesToFill);
                emit(() -> list.get().add(nestedComponent));
                instancesToFill.add(new NestedComponentInstance(nestedComponentBinding, nestedComponent, root));
            }
            emit(() -> setter.invoke(component, list));
        } else {
            ReflectClass<?> componentType = findClass(nested.getComponentType());
            ReflectMethod setter = cls.getMethod(nested.getMethodName(), componentType);
            Value<Object> nestedComponent = emitNestedComponent(nested.getComponents().get(0), root, instancesToFill);
            emit(() -> setter.invoke(component, nestedComponent));
            instancesToFill.add(new NestedComponentInstance(nested.getComponents().get(0), nestedComponent, root));
        }
        return instancesToFill;
    }

    private Value<Object> emitNestedComponent(ComponentBinding node, Value<?> root,
            List<NestedComponentInstance> instances) {
        context.location(node.getLocation());
        ReflectClass<?> componentType = findClass(node.getClassName());
        ReflectMethod ctor = componentType.getMethod("<init>");
        Value<Object> component = emit(() -> ctor.construct());
        instances.addAll(emitElementComponent(node, component, root));
        return component;
    }

    private void emitElementName(String methodName, String elementName, Value<?> component,
            ReflectClass<?> componentType) {
        ReflectMethod setter = componentType.getJMethod(methodName, String.class);
        emit(() -> setter.invoke(component.get(), elementName));
    }

    private void updateBuilder(Value<DomBuilder> newBuilder) {
        if (++complexity > COMPLEXITY_THRESHOLD) {
            complexity = 0;
            builder = initialBuilder;
        } else {
            builder = newBuilder;
        }
    }

    static class NestedComponentInstance {
        ComponentBinding node;
        Value<?> instance;
        Value<?> root;

        NestedComponentInstance(ComponentBinding node, Value<?> instance, Value<?> root) {
            this.node = node;
            this.instance = instance;
            this.root = root;
        }
    }
}
