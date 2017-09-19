/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.json.serializer;

import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;

public class ArraySerializer extends NullableSerializer {
    private JsonSerializer itemSerializer;

    public ArraySerializer(JsonSerializer itemSerializer) {
        this.itemSerializer = itemSerializer;
    }

    @Override
    public Node serializeNonNull(JsonSerializerContext context, Object value) {
        ArrayNode node = ArrayNode.create();
        Object[] array = (Object[]) value;
        for (Object item : array) {
            node.add(itemSerializer.serialize(context, item));
        }
        return node;
    }
}
