/*
 *  Copyright 2017 konsoletyper.
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

import java.util.function.Function;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.NodeHolder;
import org.teavm.flavour.templates.Slot;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

@BindElement(name = "with-type-param")
public class ComponentWithTypeParameter<T> extends AbstractComponent {
    private Supplier<T> first;
    private Function<T, ?> second;
    private HTMLElement elem;

    public ComponentWithTypeParameter(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "first")
    public void setFirst(Supplier<T> first) {
        this.first = first;
    }

    @BindAttribute(name = "second")
    public void setSecond(Function<T, ?> second) {
        this.second = second;
    }

    @Override
    public void render() {
        if (elem == null) {
            elem = Window.current().getDocument().createElement("div");
            elem.setAttribute("id", "type-param-component");
            getSlot().append(new NodeHolder(elem));
        }

        elem.setAttribute("class", second.apply(first.get()).toString());
    }
}
