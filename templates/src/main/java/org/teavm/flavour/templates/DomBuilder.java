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
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.Document;
import org.teavm.jso.dom.xml.Element;
import org.teavm.jso.dom.xml.Node;
import org.teavm.jso.dom.xml.Text;

/**
 *
 * @author Alexey Andreev
 */
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
        Renderable renderable = modifier.apply((HTMLElement) stack.peek().element);
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

    static class Item {
        Element element;
        Slot slot;
    }
}
