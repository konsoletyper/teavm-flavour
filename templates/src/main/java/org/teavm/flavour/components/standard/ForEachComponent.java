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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.Slot;

@BindElement(name = "foreach")
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
            Collection<T> safeItems = (Collection<T>) items;
            newComputedCollection = new ArrayList<>(safeItems);
        } else {
            newComputedCollection = new ArrayList<>();
            for (T item : items) {
                newComputedCollection.add(item);
            }
        }


        int sizeDiff = Math.abs(newComputedCollection.size() - computedCollection.size());

        if (sizeDiff <= 2) {
            int upper = computedCollection.size() - 1;
            int newUpper = newComputedCollection.size() - 1;
            while (upper > 0 && newUpper > 0 && newComputedCollection.get(newUpper) == computedCollection.get(upper)) {
                --upper;
                --newUpper;
            }

            if (newComputedCollection.size() > computedCollection.size()) {
                for (int i = 0; i < sizeDiff; ++i) {
                    int index = upper + i;
                    elementVariable = newComputedCollection.get(index);
                    indexVariable = index;
                    Component childComponent = body.create();
                    childComponent.render();
                    childComponents.add(upper + i, childComponent);
                    getSlot().insert(childComponent.getSlot(), upper + i);
                }
            } else {
                for (int i = sizeDiff - 1; i >= 0; --i) {
                    childComponents.remove(newUpper + i).destroy();
                }
            }

            for (int i = 0; i < upper; ++i) {
                rerenderElement(newComputedCollection, i);
            }
            for (int i = newUpper + 1; i < newComputedCollection.size(); ++i) {
                rerenderElement(newComputedCollection, i);
            }

        } else {
            for (int i = 0; i < computedCollection.size(); ++i) {
                rerenderElement(newComputedCollection, i);
            }
            for (int i = computedCollection.size(); i < newComputedCollection.size(); ++i) {
                elementVariable = newComputedCollection.get(i);
                indexVariable = i;
                Component childComponent = body.create();
                childComponent.render();
                childComponents.add(childComponent);
                getSlot().append(childComponent.getSlot());
            }
            for (int i = childComponents.size() - 1; i >= newComputedCollection.size(); --i) {
                childComponents.remove(i).destroy();
            }
        }

        computedCollection = newComputedCollection;
    }

    private void rerenderElement(List<T> collection, int index) {
        elementVariable = collection.get(index);
        indexVariable = index;
        childComponents.get(index).render();
    }

    @Override
    public void destroy() {
        super.destroy();
        for (int i = childComponents.size() - 1; i >= 0; --i) {
            childComponents.get(i).destroy();
        }
    }
}
