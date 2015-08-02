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
package org.teavm.flavour.directives.standard;

import org.teavm.dom.html.HTMLElement;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.ElementConsumer;
import org.teavm.flavour.templates.Renderable;

/**
 *
 * @author Alexey Andreev
 */
@BindAttributeDirective(name = "expose-element")
public class ExposeElementComponent implements Renderable {
    private HTMLElement element;
    private ElementConsumer elementConsumer;

    public ExposeElementComponent(HTMLElement element) {
        this.element = element;
    }

    @BindContent
    public void setElementConsumer(ElementConsumer elementConsumer) {
        this.elementConsumer = elementConsumer;
    }

    @Override
    public void render() {
        elementConsumer.consume(element);
    }

    @Override
    public void destroy() {
    }
}
