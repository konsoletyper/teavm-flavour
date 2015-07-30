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

import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.StringNode;

/**
 *
 * @author Alexey Andreev
 */
public class CharacterSerializer extends NullableSerializer {
    @Override
    public Node serializeNonNull(JsonSerializerContext context, Object value) {
        return StringNode.create(String.valueOf(((Character) value).charValue()));
    }
}
