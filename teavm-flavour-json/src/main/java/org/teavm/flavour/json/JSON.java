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
package org.teavm.flavour.json;

import java.lang.reflect.Array;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NullNode;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;

/**
 *
 * @author Alexey Andreev
 */
public final class JSON {
    private JSON() {
    }

    public Node serialize(Object object) {
        return serialize(new JsonSerializerContext(), object);
    }

    Node serialize(JsonSerializerContext context, Object object) {
        if (object == null) {
            return NullNode.instance();
        } else if (object instanceof Integer) {
            return NumberNode.create((Integer)object);
        } else if (object instanceof Byte) {
            return NumberNode.create((Byte)object);
        } else if (object instanceof Short) {
            return NumberNode.create((Short)object);
        } else if (object instanceof Float) {
            return NumberNode.create((Float)object);
        } else if (object instanceof Double) {
            return NumberNode.create((Double)object);
        } else if (object.getClass().isArray()) {
            ArrayNode result = ArrayNode.create();
            int length = Array.getLength(object);
            for (int i = 0; i < length; ++i) {
                result.add(serialize(Array.get(object, i)));
            }
            return result;
        } else {
            ObjectNode target = ObjectNode.create();
            getClassSerializer(object.getClass()).serialize(context, object, target);
            return target;
        }
    }

    native JsonSerializer getClassSerializer(Class<?> cls);
}
