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
package org.teavm.flavour.templates.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;

@BindElement(name = "var-with-wildcard-type")
public class VariableWithWildcardType extends AbstractComponent {
    private List<Nested<?>> components;
    private List<Component> nestedComponents;

    public VariableWithWildcardType(Slot slot) {
        super(slot);
    }

    @BindElement(name = "component")
    public void setComponents(List<Nested<?>> components) {
        this.components = components;
    }

    @Override
    public void render() {
        if (nestedComponents == null) {
            nestedComponents = new ArrayList<>();
            for (Nested<?> component : components) {
                Component nestedComponent = component.content.create();
                getSlot().append(nestedComponent.getSlot());
                nestedComponents.add(nestedComponent);
            }
        }

        for (Nested<?> component : components) {
            @SuppressWarnings("unchecked")
            Nested<Object> safeComponent = (Nested<Object>) component;
            safeComponent.result = safeComponent.value.get();
        }

        for (Component nestedComponent : nestedComponents) {
            nestedComponent.render();
        }
    }

    public static class Nested<T> {
        Supplier<T> value;
        T result;
        Fragment content;

        @BindAttribute(name = "value")
        public void setValue(Supplier<T> value) {
            this.value = value;
        }

        @BindAttribute(name = "as")
        public T getResult() {
            return result;
        }

        @BindContent
        public void setContent(Fragment content) {
            this.content = content;
        }
    }
}
