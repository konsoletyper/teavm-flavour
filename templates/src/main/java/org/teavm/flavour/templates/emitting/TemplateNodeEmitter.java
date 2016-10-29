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
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectMethod;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;

class TemplateNodeEmitter implements TemplateNodeVisitor {
    private EmitContext context;
    private Value<DomBuilder> builder;

    TemplateNodeEmitter(EmitContext context, Value<DomBuilder> builder) {
        this.context = context;
        this.builder = builder;
    }

    @Override
    public void visit(DOMElement node) {
        context.location(node.getLocation());

        boolean hasInnerDirectives = node.getChildNodes().stream()
                .anyMatch(child -> child instanceof DirectiveBinding);
        String tagName = node.getName();
        {
            Value<DomBuilder> tmpBuilder = builder;
            if (hasInnerDirectives) {
                builder = emit(() -> tmpBuilder.get().openSlot(tagName));
            } else {
                builder = emit(() -> tmpBuilder.get().open(tagName));
            }
        }

        for (DOMAttribute attr : node.getAttributes()) {
            String attrName = attr.getName();
            String attrValue = attr.getValue();
            Value<DomBuilder> tmpBuilder = builder;
            context.location(attr.getLocation());
            builder = emit(() -> tmpBuilder.get().attribute(attrName, attrValue));
        }

        for (AttributeDirectiveBinding binding : node.getAttributeDirectives()) {
            Value<DomBuilder> tmpBuilder = builder;
            Value<Modifier> modifier = emitAttributeDirective(binding);
            builder = emit(() -> tmpBuilder.get().add(modifier.get()));
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        {
            Value<DomBuilder> tmpBuilder = builder;
            context.endLocation(node.getLocation());
            builder = emit(() -> tmpBuilder.get().close());
        }
    }

    @Override
    public void visit(DOMText node) {
        context.location(node.getLocation());
        String value = node.getValue();
        Value<DomBuilder> tmpBuilder = builder;
        builder = emit(() -> tmpBuilder.get().text(value));
    }

    @Override
    public void visit(DirectiveBinding node) {
        context.location(node.getLocation());
        ReflectClass<?> componentType = findClass(node.getClassName());
        ReflectMethod ctor = componentType.getJMethod("<init>", Slot.class);
        Value<Component> component = emit(() -> (Component) ctor.construct(Slot.create()));
        List<NestedComponentInstance> nestedInstances = emitDirective(node, component, component);

        context.pushBoundVars();
        List<TemplateVariable> variables = new ArrayList<>();
        Map<DirectiveBinding, Value<?>> instances = nestedInstances.stream()
                .collect(Collectors.toMap(nested -> nested.node, nested -> nested.instance));
        instances.put(node, component);
        emitVariables(node, variables, instances);
        emitDirectiveContent(node, node, component, variables);
        for (NestedComponentInstance nestedInstance : nestedInstances) {
            emitDirectiveContent(node, nestedInstance.node, nestedInstance.instance, variables);
        }
        context.popBoundVars();

        Value<DomBuilder> tmpBuilder = builder;
        builder = emit(() -> tmpBuilder.get().add(component.get()));
    }

    private List<NestedComponentInstance> emitDirective(DirectiveBinding node, Value<?> component, Value<?> root) {
        ReflectClass<?> componentType = findClass(node.getClassName());

        for (DirectiveFunctionBinding computation : node.getComputations()) {
            emitFunction(computation, component);
        }

        if (node.getDirectiveNameMethodName() != null) {
            context.location(node.getLocation());
            emitDirectiveName(node.getDirectiveNameMethodName(), node.getName(), component, componentType);
        }

        List<NestedComponentInstance> nestedInstances = new ArrayList<>();
        for (NestedDirectiveBinding nestedDirective : node.getNestedDirectives()) {
            nestedInstances.addAll(emitNestedDirective(nestedDirective, component, root));
        }
        return nestedInstances;
    }

    private void emitDirectiveContent(DirectiveBinding rootNode, DirectiveBinding node,  Value<?> component,
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

    private void emitVariables(DirectiveBinding directive, List<TemplateVariable> variables,
            Map<DirectiveBinding, Value<?>> instances) {
        Value<?> instance = instances.get(directive);
        ReflectClass<?> componentType = findClass(directive.getClassName());
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            Value<VariableImpl> variableImpl = emit(() -> new VariableImpl());
            ReflectMethod getter = componentType.getMethod(varBinding.getMethodName());
            Value<Object> source = lazy(() -> getter.invoke(instance.get()));
            variables.add(new TemplateVariable(variableImpl, source));
            context.addVariable(varBinding.getName(), () -> {
                Value<VariableImpl> tmp = variableImpl;
                return emit(() -> tmp.get().value);
            });
        }
        for (NestedDirectiveBinding nestedBinding : directive.getNestedDirectives()) {
            for (DirectiveBinding nestedDirective : nestedBinding.getDirectives()) {
                emitVariables(nestedDirective, variables, instances);
            }
        }
    }

    private Value<Modifier> emitAttributeDirective(AttributeDirectiveBinding binding) {
        context.location(binding.getLocation());
        ReflectClass<Renderable> directiveClass = findClass(binding.getClassName()).asSubclass(Renderable.class);
        ReflectMethod ctor = directiveClass.getJMethod("<init>", HTMLElement.class);

        return proxy(Modifier.class, (instance, method, args) -> {
            Value<HTMLElement> elem = emit(() -> (HTMLElement) args[0]);
            Value<Renderable> result = emit(() -> (Renderable) ctor.construct(elem.get()));

            for (DirectiveFunctionBinding function : binding.getFunctions()) {
                emitFunction(function, result);
            }
            if (binding.getDirectiveNameMethodName() != null) {
                context.location(binding.getLocation());
                emitDirectiveName(binding.getDirectiveNameMethodName(), binding.getName(), result, directiveClass);
            }

            context.location(binding.getLocation());
            exit(() -> result.get());
        });
    }

    private void emitFunction(DirectiveFunctionBinding function, Value<?> component) {
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

    private List<NestedComponentInstance> emitNestedDirective(NestedDirectiveBinding nested, Value<?> component,
            Value<?> root) {
        List<NestedComponentInstance> instancesToFill = new ArrayList<>();
        ReflectClass<?> cls = findClass(nested.getMethodOwner());
        if (nested.isMultiple()) {
            ReflectMethod setter = cls.getJMethod(nested.getMethodName(), List.class);
            int capacity = nested.getDirectives().size();
            Value<List<Object>> list = emit(() -> new ArrayList<>(capacity));
            for (DirectiveBinding nestedDirective : nested.getDirectives()) {
                Value<Object> nestedComponent = emitNestedComponent(nestedDirective, root, instancesToFill);
                emit(() -> list.get().add(nestedComponent));
                instancesToFill.add(new NestedComponentInstance(nestedDirective, nestedComponent, root));
            }
            emit(() -> setter.invoke(component, list));
        } else {
            ReflectClass<?> directiveType = findClass(nested.getDirectiveType());
            ReflectMethod setter = cls.getMethod(nested.getMethodName(), directiveType);
            Value<Object> nestedComponent = emitNestedComponent(nested.getDirectives().get(0), root, instancesToFill);
            emit(() -> setter.invoke(component, nestedComponent));
            instancesToFill.add(new NestedComponentInstance(nested.getDirectives().get(0), nestedComponent, root));
        }
        return instancesToFill;
    }

    private Value<Object> emitNestedComponent(DirectiveBinding node, Value<?> root,
            List<NestedComponentInstance> instances) {
        context.location(node.getLocation());
        ReflectClass<?> componentType = findClass(node.getClassName());
        ReflectMethod ctor = componentType.getMethod("<init>");
        Value<Object> component = emit(() -> ctor.construct());
        instances.addAll(emitDirective(node, component, root));
        return component;
    }

    private void emitDirectiveName(String methodName, String directiveName, Value<?> component,
            ReflectClass<?> componentType) {
        ReflectMethod setter = componentType.getJMethod(methodName, String.class);
        emit(() -> setter.invoke(component.get(), directiveName));
    }

    static class NestedComponentInstance {
        DirectiveBinding node;
        Value<?> instance;
        Value<?> root;

        NestedComponentInstance(DirectiveBinding node, Value<?> instance, Value<?> root) {
            this.node = node;
            this.instance = instance;
            this.root = root;
        }
    }
}
