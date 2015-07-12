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

import java.util.List;
import org.teavm.dom.core.Node;
import org.teavm.dom.core.NodeList;

/**
 *
 * @author Alexey Andreev
 */
public abstract class Space {
    Slot parent;
    int index;
    int lowerNode;
    int upperNode;

    Space() {
    }

    public int getIndex() {
        return index;
    }

    public Slot getParent() {
        return parent;
    }

    public void delete() {
        if (parent == null) {
            return;
        }

        RootSlot root = getRoot();
        if (root != null) {
            NodeList<Node> domNodes = root.domNode.getChildNodes();
            for (int i = upperNode - 1; i >= lowerNode; --i) {
                root.domNode.removeChild(domNodes.get(i));
            }
        }

        parent.childList.remove(index);
        int nodeCount = upperNode - lowerNode;
        for (int i = index; i < parent.childList.size(); ++i) {
            parent.childList.get(i).index = i;
        }

        upperNode += nodeCount;
        Space ancestor = this;
        while (ancestor != null) {
            if (ancestor.parent != null) {
                for (int i = ancestor.index + 1; i < ancestor.parent.childList.size(); ++i) {
                    ancestor.parent.childList.get(i).offsetNode(-nodeCount);
                }
            }
            ancestor.upperNode -= nodeCount;
            ancestor = ancestor.parent;
        }

        parent = null;
        index = 0;
        offsetNode(-lowerNode);
    }

    void offsetNode(int offset) {
        lowerNode += offset;
        upperNode += offset;
    }

    RootSlot getRoot() {
        Space space = this;
        while (space.parent != null) {
            space = space.parent;
        }
        return space instanceof RootSlot ? (RootSlot)space : null;
    }

    void getNodeHolders(@SuppressWarnings("unused") List<NodeHolder> receiver) {
        // Do nothing
    }

    public abstract void buildDebugString(StringBuilder sb);
}
