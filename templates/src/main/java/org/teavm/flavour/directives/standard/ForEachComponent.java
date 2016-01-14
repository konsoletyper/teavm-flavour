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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.Slot;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "foreach")
public class ForEachComponent<T> extends AbstractComponent {
    private Supplier<Iterable<T>> collection;
    private T elementVariable;
    private int indexVariable;
    private Fragment body;
    private List<Component> childComponents = new ArrayList<>();
    private List<T> computedCollection = new ArrayList<>();

    public ForEachComponent(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "in")
    public void setCollection(Supplier<Iterable<T>> collection) {
        this.collection = collection;
    }

    @BindAttribute(name = "var")
    public T getElementVariable() {
        return elementVariable;
    }

    @BindAttribute(name = "index")
    @OptionalBinding
    public int getIndexVariable() {
        return indexVariable;
    }

    @BindContent
    public void setBody(Fragment body) {
        this.body = body;
    }

    @Override
    public void render() {
        List<T> newComputedCollection;
        Iterable<T> items = collection.get();
        if (items instanceof Collection<?>) {
            @SuppressWarnings("unchecked")
            Collection<T> safeItems = (Collection<T>) (Collection<?>) items;
            newComputedCollection = new ArrayList<>(safeItems);
        } else {
            newComputedCollection = new ArrayList<>();
            for (T item : items) {
                newComputedCollection.add(item);
            }
        }

        for (int i = 0; i < newComputedCollection.size(); ++i) {
            T item = newComputedCollection.get(i);
            elementVariable = item;
            indexVariable = i;
            if (i >= computedCollection.size()) {
                Component childComponent = body.create();
                childComponent.render();
                childComponents.add(childComponent);
                getSlot().append(childComponent.getSlot());
            } else {
                childComponents.get(i).render();
            }
        }
        for (int i = childComponents.size() - 1; i >= newComputedCollection.size(); --i) {
            childComponents.remove(i).destroy();
        }

        computedCollection = newComputedCollection;
    }

    @Override
    public void destroy() {
        super.destroy();
        for (int i = childComponents.size() - 1; i >= 0; --i) {
            childComponents.get(i).destroy();
        }
    }
}
