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
package org.teavm.flavour.directives.attributes;

import java.util.Objects;
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirectiveName;
import org.teavm.flavour.templates.Renderable;
import org.teavm.jso.dom.html.HTMLElement;

@BindAttributeDirective(name = "*")
public class ComputedAttribute<T> implements Renderable {
    private HTMLElement element;
    private Supplier<T> value;
    private Object cachedValue;
    private String name;

    public ComputedAttribute(HTMLElement element) {
        this.element = element;
    }

    @BindContent
    public void setValue(Supplier<T> value) {
        this.value = value;
    }

    @BindDirectiveName
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void render() {
        Object newValue = value.get();
        if (!Objects.equals(newValue, cachedValue)) {
            cachedValue = newValue;
            element.setAttribute(name, String.valueOf(newValue));
        }
    }

    @Override
    public void destroy() {
        element.removeAttribute(name);
    }
}
