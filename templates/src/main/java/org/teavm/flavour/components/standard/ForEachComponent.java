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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.Space;

@BindElement(name = "foreach")
public class ForEachComponent<T> extends AbstractComponent {
    private Supplier<Iterable<T>> collection;
    private T elementVariable;
    private int indexVariable;
    private Fragment body;
    private List<Component> childComponents = new LinkedList<>();
    private List<T> computedCollection = new LinkedList<>();

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
        List<T> newComputedCollection = initNewCollection();

        ListIterator<T> lowerDataIterator = computedCollection.listIterator();
        ListIterator<T> lowerNewDataIterator = newComputedCollection.listIterator();
        ListIterator<Component> lowerComponentIterator = childComponents.listIterator();
        ListIterator<T> upperDataIterator = computedCollection.listIterator(computedCollection.size());
        ListIterator<T> upperNewDataIterator = newComputedCollection.listIterator(newComputedCollection.size());
        ListIterator<Component> upperComponentIterator = childComponents.listIterator(childComponents.size());

        findLastChangedItem(upperDataIterator, upperNewDataIterator, upperComponentIterator);
        int dataLimit = upperDataIterator.nextIndex();
        int newDataLimit = upperNewDataIterator.nextIndex();
        while (lowerDataIterator.hasNext() && lowerDataIterator.nextIndex() < dataLimit
                && lowerNewDataIterator.hasNext() && lowerNewDataIterator.nextIndex() < newDataLimit) {
            Component component = lowerComponentIterator.next();
            indexVariable = lowerDataIterator.nextIndex();
            elementVariable = lowerNewDataIterator.next();
            lowerDataIterator.next();
            component.render();
        }

        if (lowerDataIterator.hasNext() && lowerDataIterator.nextIndex() < dataLimit) {
            while (lowerDataIterator.hasNext() && lowerDataIterator.nextIndex() < dataLimit) {
                Component component = lowerComponentIterator.next();
                lowerComponentIterator.remove();
                component.destroy();
                lowerDataIterator.next();
                lowerDataIterator.remove();
                --dataLimit;
            }
        } else if (lowerNewDataIterator.hasNext() && lowerNewDataIterator.nextIndex() < newDataLimit) {
            Component nextComponent = upperComponentIterator.hasNext() ? upperComponentIterator.next() : null;
            Space nextSlot = nextComponent != null ? nextComponent.getSlot() : null;
            while (lowerNewDataIterator.hasNext() && lowerNewDataIterator.nextIndex() < newDataLimit) {
                indexVariable = lowerNewDataIterator.nextIndex();
                elementVariable = lowerNewDataIterator.next();
                lowerDataIterator.add(elementVariable);
                Component childComponent = body.create();
                childComponent.render();
                lowerComponentIterator.add(childComponent);
                getSlot().insertBefore(childComponent.getSlot(), nextSlot);
            }
        }
    }

    private List<T> initNewCollection() {
        List<T> newComputedCollection;
        Iterable<T> items = collection.get();
        if (items instanceof List<?>) {
            newComputedCollection = (List<T>) items;
        } else if (items instanceof Collection<?>) {
            Collection<T> safeItems = (Collection<T>) items;
            newComputedCollection = new LinkedList<>(safeItems);
        } else {
            newComputedCollection = new LinkedList<>();
            for (T item : items) {
                newComputedCollection.add(item);
            }
        }
        return newComputedCollection;
    }

    private void findLastChangedItem(ListIterator<T> dataIterator, ListIterator<T> newDataIterator,
            ListIterator<Component> componentIterator) {
        while (dataIterator.hasPrevious() && newDataIterator.hasPrevious()) {
            indexVariable = newDataIterator.previousIndex();
            elementVariable = newDataIterator.previous();
            if (elementVariable != dataIterator.previous()) {
                dataIterator.next();
                newDataIterator.next();
                break;
            }
            Component component = componentIterator.previous();
            component.render();
        }
    }
    @Override
    public void destroy() {
        super.destroy();
        for (int i = childComponents.size() - 1; i >= 0; --i) {
            childComponents.get(i).destroy();
        }
    }
}
