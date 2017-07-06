/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.templates.test;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.NodeHolder;
import org.teavm.flavour.templates.Slot;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

@BindElement(name = "with-nested-wildcard")
public class ComponentWithNestedWildcard extends AbstractComponent {
    private List<Nested<?>> components;
    private HTMLElement elem;

    public ComponentWithNestedWildcard(Slot slot) {
        super(slot);
    }

    @BindElement(name = "component")
    public void setComponents(List<Nested<?>> components) {
        this.components = components;
    }

    @Override
    public void render() {
        if (elem == null) {
            elem = Window.current().getDocument().createElement("div");
            elem.setAttribute("id", "test-component");
            getSlot().append(new NodeHolder(elem));
        }

        StringBuilder sb = new StringBuilder();
        for (Nested<?> nested : components) {
            @SuppressWarnings("unchecked")
            Function<Object, String> second = (Function<Object, String>) nested.second;
            sb.append(second.apply(nested.first.get())).append(";");
        }
        elem.setAttribute("class", sb.toString());
    }

    public static class Nested<T> {
        Supplier<T> first;
        Function<T, String> second;

        @BindAttribute(name = "first")
        public void setFirst(Supplier<T> first) {
            this.first = first;
        }

        @BindAttribute(name = "second")
        public void setSecond(Function<T, String> second) {
            this.second = second;
        }
    }
}
