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

import org.teavm.jso.core.JSArray;
import org.teavm.jso.dom.xml.Node;

public abstract class Slot extends Space {
    Space first;
    Space last;

    Slot() {
    }

    public void append(Space slot) {
        insertBefore(slot, null);
    }

    public void insertBefore(Space space, Space successor) {
        if (space.getParent() != null) {
            throw new IllegalArgumentException("The given space is already hosted by a slot");
        }
        if (successor != null && successor.getParent() != this) {
            throw new IllegalArgumentException("Successor does not belong to this slot");
        }

        space.parent = this;
        if (successor == null) {
            space.previous = last;
            if (last != null) {
                last.next = space;
            } else {
                first = space;
            }
            last = space;
        } else {
            space.next = successor;
            space.previous = successor.previous;
            if (space.next != null) {
                space.next.previous = space;
            } else {
                last = space;
            }
            if (space.previous != null) {
                space.previous.next = space;
            } else {
                first = space;
            }
        }

        RootSlot root = getRoot();
        if (root == null) {
            return;
        }

        JSArray<Node> domNodes = JSArray.create();
        space.getAllNodes(domNodes);
        if (domNodes.getLength() == 0) {
            return;
        }
        Node successorDomNode;
        if (successor != null) {
            successorDomNode = successor.getFirstNode();
        } else {
            Space ancestor = this;
            successorDomNode = null;
            while (ancestor != null) {
                if (ancestor.next != null) {
                    successorDomNode = ancestor.next.getFirstNode();
                    break;
                }
                ancestor = ancestor.parent;
            }
        }
        for (int i = 0; i < domNodes.getLength(); ++i) {
            root.domNode.insertBefore(domNodes.get(i), successorDomNode);
        }
    }

    @Override
    Node getFirstNode() {
        Space child = first;
        while (child != null) {
            Node result = child.getFirstNode();
            if (result != null) {
                return result;
            }
            child = child.getNext();
        }
        return null;
    }

    @Override
    Node getLastNode() {
        Space child = last;
        while (child != null) {
            Node result = child.getLastNode();
            if (result != null) {
                return result;
            }
            child = child.getPrevious();
        }
        return null;
    }

    @Override
    void getAllNodes(JSArray<Node> nodes) {
        for (Space child = first; child != null; child = child.getNext()) {
            child.getAllNodes(nodes);
        }
    }

    @Override
    void deleteDom() {
        Space child = first;
        while (child != null) {
            child.deleteDom();
            child = child.getNext();
        }
    }

    public static Slot create() {
        return new ContainerSlot();
    }

    public static Slot root(Node domNode) {
        return new RootSlot(domNode);
    }
}
