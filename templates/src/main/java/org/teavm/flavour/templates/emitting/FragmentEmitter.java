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
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import java.util.List;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomComponentHandler;
import org.teavm.flavour.templates.DomComponentTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.tree.ComponentBinding;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;

class FragmentEmitter {
    EmitContext context;

    FragmentEmitter(EmitContext context) {
        this.context = context;
    }

    public Value<Fragment> emitTemplate(ComponentBinding component, List<TemplateNode> fragment,
            List<TemplateVariable> variables) {
        if (component != null) {
            context.location(component.getLocation());
        }

        ReflectClass<Component> componentType = component != null
                ? findClass(component.getClassName()).asSubclass(Component.class)
                : null;

        return proxy(Fragment.class, (fProxy, fMethod, fArgs) -> {
            context.pushBoundVars();

            for (TemplateVariable variable : variables) {
                Value<VariableImpl> dest = emit(() -> new VariableImpl());
                context.addVariable(variable.name, () -> emit(() -> dest.get().value));
                variable.destination = dest;
            }

            Value<DomComponentHandler> handler = proxy(DomComponentHandler.class, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "update":
                        if (componentType != null) {
                            emitUpdateMethod(variables);
                        }
                        break;
                    case "buildDom":
                        emitBuildDomMethod(emit(() -> (DomBuilder) args[0]), fragment);
                        break;
                }
            });

            Value<Component> result = emit(() -> new DomComponentTemplate(handler.get()));
            exit(() -> result.get());
        });
    }

    private void emitBuildDomMethod(Value<DomBuilder> builder, List<TemplateNode> fragment) {
        TemplateNodeEmitter nodeEmitter = new TemplateNodeEmitter(context, builder);
        for (TemplateNode node : fragment) {
            context.location(node.getLocation());
            node.acceptVisitor(nodeEmitter);
        }
    }

    private void emitUpdateMethod(List<TemplateVariable> variables) {
        for (TemplateVariable var : variables) {
            Value<Object> source = var.source;
            Value<VariableImpl> dest = var.destination;
            emit(() -> dest.get().value = source.get());
        }
    }
}
