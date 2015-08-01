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

import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.StringNode;

/**
 *
 * @author Alexey Andreev
 */
public class ObjectDeserializer extends NullableDeserializer {
    @Override
    public Object deserializeNonNull(JsonDeserializerContext context, Node node) {
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            Object[] result = new Object[arrayNode.size()];
            for (int i = 0; i < arrayNode.size(); ++i) {
                result[i] = JSON.deserialize(arrayNode.get(i), Object.class);
            }
            return result;
        } else if (node.isBoolean()) {
            return ((BooleanNode) node).getValue();
        } else if (node.isNumber()) {
            NumberNode number = (NumberNode) node;
            return number.isInt() ? number.getIntValue() : number.getValue();
        } else if (node.isString()) {
            return ((StringNode) node).getValue();
        } else {
            throw new IllegalArgumentException("Don't know how to deserialize given JSON as "
                    + "a java.lang.Object: " + node.stringify());
        }
    }
}
