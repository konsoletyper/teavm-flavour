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
package org.teavm.flavour.directives.html;

import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.Renderable;
import org.teavm.jso.dom.html.HTMLInputElement;

@BindAttributeDirective(name = "enabled")
public class EnabledBinder implements Renderable {
    private HTMLInputElement element;
    private Supplier<Boolean> value;
    private boolean cachedValue = true;

    public EnabledBinder(ModifierTarget target) {
        this.element = (HTMLInputElement) target.getElement();
    }

    @BindContent
    public void setValue(Supplier<Boolean> value) {
        this.value = value;
    }

    @Override
    public void render() {
        boolean newValue = value.get();
        if (newValue != cachedValue) {
            cachedValue = newValue;
            element.setDisabled(!newValue);
        }
    }

    @Override
    public void destroy() {
    }
}
