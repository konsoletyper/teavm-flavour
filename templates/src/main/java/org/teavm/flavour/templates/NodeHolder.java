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

public class NodeHolder extends Space {
    Node node;

    public NodeHolder(Node node) {
        this.node = node;
    }

    @Override
    Node getFirstNode() {
        return node;
    }

    @Override
    Node getLastNode() {
        return node;
    }

    @Override
    void getAllNodes(JSArray<Node> nodes) {
        nodes.push(node);
    }

    @Override
    void deleteDom() {
        node.delete();
    }
}
