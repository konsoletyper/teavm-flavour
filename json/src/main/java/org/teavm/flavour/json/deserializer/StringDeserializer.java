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
package org.teavm.flavour.json.deserializer;

import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.StringNode;

/**
 *
 * @author Alexey Andreev
 */
public class StringDeserializer extends NullableDeserializer {
    @Override
    public Object deserializeNonNull(JsonDeserializerContext context, Node node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("Can't deserialize non-string node as a string");
        }
        return ((StringNode) node).getValue();
    }
}
