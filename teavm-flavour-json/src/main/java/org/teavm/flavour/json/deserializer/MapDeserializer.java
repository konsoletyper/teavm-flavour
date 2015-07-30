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

import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;

/**
 *
 * @author Alexey Andreev
 */
public class MapDeserializer extends NullableDeserializer {
    private JsonDeserializer keyDeserializer;
    private JsonDeserializer valueDeserializer;

    public MapDeserializer(JsonDeserializer keyDeserializer, JsonDeserializer valueDeserializer) {
        this.keyDeserializer = keyDeserializer;
        this.valueDeserializer = valueDeserializer;
    }

    @Override
    public Object deserializeNonNull(JsonDeserializerContext context, Node node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Can't deserialize non-object node as a map");
        }

        ObjectNode objectNode = (ObjectNode) node;
        Map<Object, Object> map = new HashMap<>();
        for (String key : objectNode.allKeys()) {
            Node valueNode = objectNode.get(key);
            map.put(keyDeserializer.deserialize(context, StringNode.create(key)),
                    valueDeserializer.deserialize(context, valueNode));
        }

        return map;
    }
}
