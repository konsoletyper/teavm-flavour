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

public abstract class Space {
    Slot parent;
    Space previous;
    Space next;

    Space() {
    }

    public Slot getParent() {
        return parent;
    }

    abstract Node getFirstNode();

    abstract Node getLastNode();

    abstract void getAllNodes(JSArray<Node> nodes);

    public Space getPrevious() {
        return previous;
    }

    public Space getNext() {
        return next;
    }

    public void delete() {
        if (parent == null) {
            return;
        }

        deleteDom();

        Space newPrevious = previous;
        if (newPrevious != null) {
            newPrevious = newPrevious.previous;
        } else {
            parent.first = next;
        }
        Space newNext = next;
        if (newNext != null) {
            newNext = newNext.next;
        } else {
            parent.previous = previous;
        }

        if (newPrevious != null) {
            newPrevious.next = newNext;
        }
        if (newNext != null) {
            newNext.previous = newPrevious;
        }

        next = newNext;
        previous = newPrevious;
        parent = null;
    }

    void deleteDom() {
    }

    RootSlot getRoot() {
        Space space = this;
        while (space.parent != null) {
            space = space.parent;
        }
        return space instanceof RootSlot ? (RootSlot) space : null;
    }
}
