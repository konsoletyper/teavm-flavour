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
package org.teavm.flavour.templates.test;

import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.NodeHolder;
import org.teavm.flavour.templates.Slot;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

@BindDirective(name = "value-copy")
public class ValueCopyComponent extends AbstractComponent {
    private Supplier<String> value;
    private HTMLElement elem;

    public ValueCopyComponent(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "value")
    public void setValue(Supplier<String> value) {
        this.value = value;
    }

    @Override
    public void render() {
        if (elem == null) {
            elem = Window.current().getDocument().createElement("div");
            elem.setAttribute("id", "value-copy");
            getSlot().append(new NodeHolder(elem));
        }
        String text = value.get();
        elem.setAttribute("class", text != null ? text : "");
    }
}
