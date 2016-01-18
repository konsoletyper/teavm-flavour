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
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomComponentHandler;
import org.teavm.flavour.templates.DomComponentTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.tree.DirectiveBinding;
import org.teavm.flavour.templates.tree.TemplateNode;

/**
 *
 * @author Alexey Andreev
 */
class FragmentEmitter {
    EmitContext context;

    FragmentEmitter(EmitContext context) {
        this.context = context;
    }

    public Value<Fragment> emitTemplate(Emitter<?> em, DirectiveBinding directive,
            List<TemplateNode> fragment, List<TemplateVariable> variables) {
        if (directive != null) {
            context.location(em, directive.getLocation());
        }

        ReflectClass<Component> componentType = directive != null
                ? em.getContext().findClass(directive.getClassName()).asSubclass(Component.class)
                : null;

        Value<Fragment> fragmentResult = em.proxy(Fragment.class, (fem, fProxy, fMethod, fArgs) -> {
            context.pushBoundVars();

            Value<DomComponentHandler> handler = fem.proxy(DomComponentHandler.class, (body, proxy, method, args) -> {
                switch (method.getName()) {
                    case "update":
                        if (componentType != null) {
                            emitUpdateMethod(body, variables);
                        }
                        break;
                    case "buildDom":
                        emitBuildDomMethod(body, body.emit(() -> (DomBuilder) args[0]), fragment);
                        break;
                }
            });

            Value<Component> result = fem.emit(() -> new DomComponentTemplate(handler.get()));
            fem.returnValue(result);
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

    private void emitUpdateMethod(Emitter<?> em, List<TemplateVariable> variables) {
        for (TemplateVariable var : variables) {
            Value<Object> source = var.source;
            Value<VariableImpl> dest = var.destination;
            em.emit(() -> dest.get().value = source.get());
        }
    }
}
