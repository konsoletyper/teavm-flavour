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
package org.teavm.flavour.components.html;

import java.util.Objects;
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttributeComponent;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.Renderable;
import org.teavm.flavour.templates.ValueChangeListener;
import org.teavm.jso.dom.html.HTMLInputElement;

@BindAttributeComponent(name = "bidir-value")
public class BidirectionalValueBinder<T> implements Renderable {
    private ModifierTarget target;
    private HTMLInputElement element;
    private Supplier<T> value;
    private Object cachedValue;
    private ValueChangeListener<String> listener;
    private boolean bound;

    public BidirectionalValueBinder(ModifierTarget target) {
        this.target = target;
        this.element = (HTMLInputElement) target.getElement();
    }

    @BindContent
    public void setValue(Supplier<T> value) {
        this.value = value;
    }

    @BindContent
    public void setListener(ValueChangeListener<String> listener) {
        this.listener = listener;
    }

    @Override
    public void render() {
        Object newValue = value.get();
        if (!Objects.equals(newValue, cachedValue)) {
            cachedValue = newValue;
            element.setValue(String.valueOf(newValue));
        }

        if (!bound) {
            bound = true;
            target.addValueChangeListener(listener);
        }
    }

    @Override
    public void destroy() {
        if (bound) {
            bound = false;
            target.removeValueChangeListener(listener);
        }
    }
}
