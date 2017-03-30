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
import org.teavm.flavour.templates.BindAttributeComponent;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.Renderable;
import org.teavm.jso.dom.html.HTMLInputElement;

@BindAttributeComponent(name = "value")
public class ValueBinder<T> implements Renderable {
    HTMLInputElement element;
    private Supplier<T> value;
    private Object cachedValue;

    public ValueBinder(ModifierTarget target) {
        this.element = (HTMLInputElement) target.getElement();
    }

    @BindContent
    public void setValue(Supplier<T> value) {
        this.value = value;
    }

    @Override
    public void render() {
        Object newValue = value.get();
        if (!Objects.equals(newValue, cachedValue)) {
            cachedValue = newValue;
            element.setValue(String.valueOf(newValue));
        }
    }

    @Override
    public void destroy() {
    }
}
