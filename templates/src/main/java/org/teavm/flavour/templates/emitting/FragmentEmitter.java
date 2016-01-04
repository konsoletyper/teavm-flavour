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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomComponentHandler;
import org.teavm.flavour.templates.DomComponentTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.tree.DirectiveBinding;
import org.teavm.flavour.templates.tree.DirectiveVariableBinding;
import org.teavm.flavour.templates.tree.TemplateNode;

/**
 *
 * @author Alexey Andreev
 */
class FragmentEmitter {
    EmitContext context;

    public FragmentEmitter(EmitContext context) {
        this.context = context;
    }

    public Value<Fragment> emitTemplate(Emitter<?> em, DirectiveBinding directive, Value<Component> component,
            List<TemplateNode> fragment) {
        if (directive != null) {
            context.location(em, directive.getLocation());
        }

        @SuppressWarnings("unchecked")
        ReflectClass<Component> componentType = directive != null
                ? (ReflectClass<Component>) em.getContext().findClass(directive.getClassName())
                : null;

        Value<Fragment> fragmentResult = em.proxy(Fragment.class, (fem, fProxy, fMethod, fArgs) -> {
            context.pushBoundVars();
            Map<String, Value<VariableImpl>> variables = new HashMap<>();
            if (directive != null) {
                for (DirectiveVariableBinding varBinding : directive.getVariables()) {
                    Value<VariableImpl> variableImpl = fem.emit(() -> new VariableImpl());
                    variables.put(varBinding.getName(), variableImpl);
                    context.addVariable(varBinding.getName(), innerEm -> {
                        Value<VariableImpl> tmp = variableImpl;
                        return innerEm.emit(() -> tmp.get().value);
                    });
                }
            }

            Value<DomComponentHandler> handler = fem.proxy(DomComponentHandler.class, (bodyEm, proxy, method, args) -> {
                switch (method.getName()) {
                    case "update":
                        if (componentType != null) {
                            emitUpdateMethod(bodyEm, directive, componentType, component, variables);
                        }
                        break;
                    case "buildDom":
                        emitBuildDomMethod(bodyEm, bodyEm.emit(() -> (DomBuilder) args[0]), fragment);
                        break;
                }
            });

            Value<Component> result = fem.emit(() -> new DomComponentTemplate(handler.get()));
            fem.returnValue(result);
            context.popBoundVars();
        });

        return fragmentResult;
    }

    private void emitBuildDomMethod(Emitter<?> em, Value<DomBuilder> builder, List<TemplateNode> fragment) {
        TemplateNodeEmitter nodeEmitter = new TemplateNodeEmitter(context, em, builder);
        for (TemplateNode node : fragment) {
            context.location(em, node.getLocation());
            node.acceptVisitor(nodeEmitter);
        }
    }

    private void emitUpdateMethod(Emitter<?> em, DirectiveBinding directive, ReflectClass<?> componentType,
            Value<Component> component, Map<String, Value<VariableImpl>> variables) {
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            Value<VariableImpl> var = variables.get(varBinding.getName());
            ReflectMethod getter = componentType.getMethod(varBinding.getMethodName());
            em.emit(() -> var.get().value = getter.invoke(component));
        }
    }
}
