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
import org.teavm.dom.browser.Window;
import org.teavm.dom.core.Document;
import org.teavm.dom.core.Element;
import org.teavm.dom.core.Node;
import org.teavm.dom.core.Text;
import org.teavm.jso.JS;

/**
 *
 * @author Alexey Andreev
 */
public class DomBuilder {
    private static final Window window = (Window)JS.getGlobal();
    private static final Document document = window.getDocument();
    private Slot slot;
    private Deque<Element> stack = new ArrayDeque<>();
    private List<Renderable> renderables = new ArrayList<>();

    public DomBuilder(Slot slot) {
        this.slot = slot;
    }

    public DomBuilder open(String tagName) {
        Element elem = document.createElement(tagName);
        appendNode(elem);
        stack.push(elem);
        return this;
    }

    public DomBuilder close() {
        stack.pop();
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
        stack.peek().setAttribute(name, value);
        return this;
    }

    public DomBuilder add(Component component) {
        if (stack.isEmpty()) {
            slot.append(component.getSlot());
        } else {
            Slot elemSlot = Slot.root(stack.peek());
            elemSlot.append(component.getSlot());
        }
        component.render();
        renderables.add(component);
        return this;
    }

    public DomBuilder add(Fragment fragment) {
        return add(fragment.create());
    }

    private void appendNode(Node node) {
        if (stack.isEmpty()) {
            slot.append(new NodeHolder(node));
        } else {
            stack.peek().appendChild(node);
        }
    }

    public List<Renderable> getRenderables() {
        return renderables;
    }
}
