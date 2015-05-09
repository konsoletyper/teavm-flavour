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

import java.util.ArrayList;
import java.util.List;
import org.teavm.dom.core.Node;

/**
 *
 * @author Alexey Andreev
 */
public abstract class Slot extends Space {
    List<Space> childList = new ArrayList<>();

    Slot() {
    }

    public void append(Space slot) {
        insert(slot, size());
    }

    public void insert(Space space, int index) {
        if (space.getParent() != null) {
            throw new IllegalArgumentException("The given space is already hosted by a slot");
        }

        RootSlot root = getRoot();
        int successorIndex = index < childList.size() ? childList.get(index).upperNode : upperNode;
        if (root != null) {
            List<NodeHolder> nodeHolders = new ArrayList<>();
            space.getNodeHolders(nodeHolders);
            Node successor = root.domNode.getChildNodes().get(successorIndex);
            for (NodeHolder nodeHolder : nodeHolders) {
                root.domNode.insertBefore(nodeHolder.node, successor);
            }
        }

        childList.add(index, space);
        space.parent = this;
        for (int i = index; i < childList.size(); ++i) {
            childList.get(i).index = index;
        }

        int nodeCount = space.upperNode - space.lowerNode;
        space.offsetNode(successorIndex);
        space.upperNode -= nodeCount;
        Space ancestor = space;
        while (ancestor != null) {
            if (ancestor.parent != null) {
                for (int i = ancestor.index + 1; i < ancestor.parent.childList.size(); ++i) {
                    ancestor.parent.childList.get(i).offsetNode(nodeCount);
                }
            }
            ancestor.upperNode += nodeCount;
            ancestor = ancestor.parent;
        }

        if (root != null) {
            StringBuilder sb = new StringBuilder();
            root.buildDebugString(sb);
            System.out.println(sb.toString());
        }
    }

    public Space getChild(int index) {
        return childList.get(index);
    }

    public int size() {
        return childList.size();
    }
    public static Slot create() {
        return new ContainerSlot();
    }

    public static Slot root(Node domNode) {
        return new RootSlot(domNode);
    }

    @Override
    void offsetNode(int offset) {
        super.offsetNode(offset);
        for (Space child : childList) {
            child.offsetNode(offset);
        }
    }

    @Override
    void getNodeHolders(List<NodeHolder> receiver) {
        for (Space child : childList) {
            child.getNodeHolders(receiver);
        }
    }

    @Override
    public void buildDebugString(StringBuilder sb) {
        sb.append('[').append(lowerNode).append(' ').append('(');
        if (!childList.isEmpty()) {
            childList.get(0).buildDebugString(sb);
            for (int i = 1; i < childList.size(); ++i) {
                sb.append(' ');
                childList.get(i).buildDebugString(sb);
            }
        }
        sb.append(')');
        sb.append(' ').append(upperNode).append(']');
    }
}
