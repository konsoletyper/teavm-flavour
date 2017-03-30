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
package org.teavm.flavour.components.standard;

import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;

@BindElement(name = "with")
public class WithComponent<T> extends AbstractComponent {
    private Fragment content;
    private T variable;
    private Supplier<T> value;
    private Component contentRenderer;

    public WithComponent(Slot slot) {
        super(slot);
    }

    @BindContent
    public void setContent(Fragment content) {
        this.content = content;
    }

    @BindAttribute(name = "var")
    public T getVariable() {
        return variable;
    }

    @BindAttribute(name = "value")
    public void setValue(Supplier<T> value) {
        this.value = value;
    }

    @Override
    public void render() {
        if (contentRenderer == null) {
            contentRenderer = content.create();
            getSlot().append(contentRenderer.getSlot());
        }
        variable = value.get();
        contentRenderer.render();
    }

    @Override
    public void destroy() {
        if (contentRenderer != null) {
            contentRenderer.destroy();
            contentRenderer = null;
        }
        super.destroy();
    }
}
