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

import java.lang.reflect.Array;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;

/**
 *
 * @author Alexey Andreev
 */
public class ArrayDeserializer extends NullableDeserializer {
    private Class<?> itemType;
    private JsonDeserializer itemDeserializer;

    public ArrayDeserializer(Class<?> itemType, JsonDeserializer itemDeserializer) {
        this.itemType = itemType;
        this.itemDeserializer = itemDeserializer;
    }

    @Override
    public Object deserializeNonNull(JsonDeserializerContext context, Node node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Can't deserialize non-array node as an array");
        }

        ArrayNode arrayNode = (ArrayNode) node;
        Object[] array = (Object[]) Array.newInstance(itemType, arrayNode.size());
        for (int i = 0; i < array.length; ++i) {
            array[i] = itemDeserializer.deserialize(context, arrayNode.get(i));
        }

        return array;
    }
}
