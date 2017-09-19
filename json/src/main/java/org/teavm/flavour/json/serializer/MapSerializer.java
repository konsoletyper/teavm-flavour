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
package org.teavm.flavour.json.serializer;

import java.util.Map;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;

public class MapSerializer extends NullableSerializer {
    private JsonSerializer keySerializer;
    private JsonSerializer valueSerializer;

    public MapSerializer(JsonSerializer keySerializer, JsonSerializer valueSerializer) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    @Override
    public Node serializeNonNull(JsonSerializerContext context, Object value) {
        ObjectNode result = ObjectNode.create();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            StringNode key = (StringNode) keySerializer.serialize(context, entry.getKey());
            result.set(key.getValue(), valueSerializer.serialize(context, entry.getValue()));
        }
        return result;
    }
}
