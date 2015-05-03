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
package org.teavm.flavour.templates.directives;

import java.util.Objects;
import org.teavm.dom.html.HTMLElement;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Renderable;

/**
 *
 * @author Alexey Andreev
 */
@BindAttributeDirective(name = "class")
public class ClassAttribute<T> implements Renderable {
    private HTMLElement element;
    private Computation<T> value;
    private Object cachedValue;

    public ClassAttribute(HTMLElement element) {
        this.element = element;
    }

    @BindContent
    public void setValue(Computation<T> value) {
        this.value = value;
    }

    @Override
    public void render() {
        Object newValue = value.perform();
        if (!Objects.equals(newValue, cachedValue)) {
            cachedValue = newValue;
            element.setAttribute("class", String.valueOf(newValue));
        }
    }

    @Override
    public void destroy() {
        element.removeAttribute("class");
    }
}
