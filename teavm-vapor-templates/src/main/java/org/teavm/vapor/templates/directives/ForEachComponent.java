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
package org.teavm.vapor.templates.directives;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.teavm.vapor.templates.BindAttribute;
import org.teavm.vapor.templates.BindContent;
import org.teavm.vapor.templates.BindDirective;
import org.teavm.vapor.templates.Computation;
import org.teavm.vapor.templates.Fragment;
import org.teavm.vapor.templates.Component;
import org.teavm.vapor.templates.Slot;
import org.teavm.vapor.templates.Variable;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(prefix = "v", name = "foreach")
public class ForEachComponent<T> implements Component {
    private Computation<Iterable<T>> collection;
    private Variable<T> elementVariable;
    private Variable<Integer> indexVariable;
    private Fragment body;
    private List<Component> childComponents = new ArrayList<>();
    private List<T> computedCollection;
    private Slot slot;

    public ForEachComponent(Slot slot) {
        this.slot = slot;
    }

    @BindAttribute(name = "in")
    public void setCollection(Computation<Iterable<T>> collection) {
        this.collection = collection;
    }

    @BindAttribute(name = "var")
    public void setElementVariable(Variable<T> elementVariable) {
        this.elementVariable = elementVariable;
    }

    @BindAttribute(name = "index", optional = true)
    public void setIndexVariable(Variable<Integer> indexVariable) {
        this.indexVariable = indexVariable;
    }

    @BindContent
    public void setBody(Fragment body) {
        this.body = body;
    }

    @Override
    public void render() {
        List<T> newComputedCollection = new ArrayList<>();
        Iterable<T> items = collection.perform();
        if (items instanceof Collection<?>) {
            @SuppressWarnings("unchecked")
            Collection<T> safeItems = (Collection<T>)(Collection<?>)items;
            newComputedCollection.addAll(safeItems);
        } else {
            for (T item : items) {
                newComputedCollection.add(item);
            }
        }

        for (int i = 0; i < newComputedCollection.size(); ++i) {
            T item = newComputedCollection.get(i);
            elementVariable.set(item);
            if (indexVariable != null) {
                indexVariable.set(i);
            }
            if (i >= computedCollection.size()) {
                Component childComponent = body.create();
                childComponents.add(childComponent);
                slot.append(childComponent.getSlot());
            }
            if (!Objects.equals(computedCollection.get(i), item)) {
                childComponents.get(i).render();
            }
        }
        for (int i = newComputedCollection.size(); i < childComponents.size(); ++i) {
            childComponents.get(i).destroy();
        }

        computedCollection = newComputedCollection;
    }

    @Override
    public void destroy() {
        slot.delete();
        for (Component renderer : childComponents) {
            renderer.destroy();
        }
    }

    @Override
    public Slot getSlot() {
        return slot;
    }
}
