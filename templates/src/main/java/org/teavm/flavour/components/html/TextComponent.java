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
package org.teavm.flavour.components.html;

import java.util.Objects;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.IgnoreContent;
import org.teavm.flavour.templates.NodeHolder;
import org.teavm.flavour.templates.Slot;
import org.teavm.jso.browser.Window;

@BindElement(name = "text")
@IgnoreContent
public class TextComponent<T> extends AbstractComponent {
    private Supplier<T> value;
    private NodeHolder textSlot;
    private T cachedValue;
    private boolean cacheInitialized;

    public TextComponent(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "value")
    public void setValue(Supplier<T> value) {
        this.value = value;
    }

    @Override
    public void render() {
        T computedValue = value.get();
        if (cacheInitialized && Objects.equals(cachedValue, computedValue)) {
            return;
        }
        cacheInitialized = true;
        cachedValue = computedValue;
        if (textSlot != null) {
            textSlot.delete();
            textSlot = null;
        }
        textSlot = new NodeHolder(Window.current().getDocument().createTextNode(String.valueOf(computedValue)));
        getSlot().append(textSlot);
    }
}
