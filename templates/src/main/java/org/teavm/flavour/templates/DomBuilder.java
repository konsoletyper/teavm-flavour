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
package org.teavm.flavour.templates;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Document;
import org.teavm.jso.dom.xml.Element;
import org.teavm.jso.dom.xml.Node;
import org.teavm.jso.dom.xml.Text;

public class DomBuilder {
    private static final Document document = Window.current().getDocument();
    private Slot slot;
    private Deque<Item> stack = new ArrayDeque<>();
    private List<Renderable> renderables = new ArrayList<>();

    public DomBuilder(Slot slot) {
        this.slot = slot;
    }

    public DomBuilder open(String tagName) {
        return open(tagName, false);
    }

    public DomBuilder openSlot(String tagName) {
        return open(tagName, true);
    }

    private DomBuilder open(String tagName, boolean slot) {
        Element elem = document.createElement(tagName);
        Item item = new Item();
        item.element = elem;
        if (slot) {
            item.slot = Slot.root(elem);
        }
        stack.push(item);
        return this;
    }

    public DomBuilder close() {
        appendNode(stack.pop().element);
        return this;
    }

    public DomBuilder text(String text) {
        Text node = document.createTextNode(text);
        appendNode(node);
        return this;
    }

    public DomBuilder attribute(String name, String value) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Can't set attribute to root node");
        }
        stack.peek().element.setAttribute(name, value);
        return this;
    }

    public DomBuilder add(Component component) {
        if (stack.isEmpty()) {
            slot.append(component.getSlot());
        } else {
            Item item = stack.peek();
            if (item.slot == null) {
                Slot elemSlot = Slot.root(item.element);
                elemSlot.append(component.getSlot());
            } else {
                item.slot.append(component.getSlot());
            }
        }
        component.render();
        renderables.add(component);
        return this;
    }

    public DomBuilder add(Fragment fragment) {
        return add(fragment.create());
    }

    public DomBuilder add(Modifier modifier) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Can't apply modifier to root node");
        }
        Renderable renderable = modifier.apply(stack.peek());
        renderables.add(renderable);
        return this;
    }

    private void appendNode(Node node) {
        if (stack.isEmpty()) {
            slot.append(new NodeHolder(node));
        } else {
            Item item = stack.peek();
            if (item.slot == null) {
                item.element.appendChild(node);
            } else {
                item.slot.append(new NodeHolder(node));
            }
        }
    }

    public List<Renderable> getRenderables() {
        return renderables;
    }

    static class Item implements ModifierTarget {
        Element element;
        Slot slot;
        Object valueChangeListeners;
        private EventListener<Event> changeListener;

        @Override
        public HTMLElement getElement() {
            return (HTMLElement) element;
        }

        @Override
        public void updateValue(String value) {
            updateValueNative(element, value);
            triggerValueChanged(value);
        }

        private void triggerValueChanged(String value) {
            if (valueChangeListeners == null) {
                return;
            }
            if (valueChangeListeners instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<ValueChangeListener<String>> listeners =
                        (List<ValueChangeListener<String>>) valueChangeListeners;
                for (ValueChangeListener<String> listener : listeners) {
                    listener.changed(value);
                }
            } else {
                @SuppressWarnings("unchecked")
                ValueChangeListener<String> listener = (ValueChangeListener<String>) valueChangeListeners;
                listener.changed(value);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void addValueChangeListener(ValueChangeListener<String> listener) {
            if (valueChangeListeners == null) {
                valueChangeListeners = listener;
                HTMLElement htmlElement = (HTMLElement) element;
                createChangeListener();
                htmlElement.addEventListener("change", changeListener);
            } else if (valueChangeListeners instanceof List<?>) {
                List<ValueChangeListener<String>> listeners =
                        (List<ValueChangeListener<String>>) valueChangeListeners;
                listeners.add(listener);
            } else {
                List<ValueChangeListener<String>> listeners = new ArrayList<>(2);
                listeners.add((ValueChangeListener<String>) valueChangeListeners);
                listeners.add(listener);
                valueChangeListeners = listeners;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void removeValueChangeListener(ValueChangeListener<String> listener) {
            if (valueChangeListeners != null) {
                if (valueChangeListeners == listener) {
                    HTMLElement htmlElement = (HTMLElement) element;
                    htmlElement.removeEventListener("change", changeListener);
                    changeListener = null;
                    valueChangeListeners = null;
                } else if (valueChangeListeners instanceof List<?>) {
                    List<ValueChangeListener<String>> listeners =
                            (List<ValueChangeListener<String>>) valueChangeListeners;
                    listeners.remove(listener);
                    if (listeners.size() == 1) {
                        valueChangeListeners = listeners.get(0);
                    }
                }
            }
        }

        private void createChangeListener() {
            changeListener = event -> triggerValueChanged(getValue());
        }

        @Override
        public String getValue() {
            return getValueNative(element);
        }

        @JSBody(params = { "element", "value" }, script = "element.value = value;")
        private static native void updateValueNative(Element element, String value);

        @JSBody(params = "element", script = "return element.value;")
        private static native String getValueNative(Element element);
    }
}
