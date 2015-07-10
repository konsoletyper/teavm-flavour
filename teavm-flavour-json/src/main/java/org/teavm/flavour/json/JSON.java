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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.BooleanDeserializer;
import org.teavm.flavour.json.deserializer.ByteDeserializer;
import org.teavm.flavour.json.deserializer.CharacterDeserializer;
import org.teavm.flavour.json.deserializer.DoubleDeserializer;
import org.teavm.flavour.json.deserializer.FloatDeserializer;
import org.teavm.flavour.json.deserializer.IntegerDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.ListDeserializer;
import org.teavm.flavour.json.deserializer.LongDeserializer;
import org.teavm.flavour.json.deserializer.MapDeserializer;
import org.teavm.flavour.json.deserializer.NumberDeserializer;
import org.teavm.flavour.json.deserializer.ObjectDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.deserializer.ShortDeserializer;
import org.teavm.flavour.json.deserializer.StringDeserializer;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NullNode;

/**
 *
 * @author Alexey Andreev
 */
public final class JSON {
    private static Map<Class<?>, JsonDeserializer> standardDeserializers;

    private JSON() {
    }

    public static Node serialize(Object object) {
        return serialize(new JsonSerializerContext(), object);
    }

    public static Node serialize(JsonSerializerContext context, Object object) {
        if (object == null) {
            return NullNode.instance();
        } else if (object.getClass().isArray()) {
            ArrayNode result = ArrayNode.create();
            int length = Array.getLength(object);
            for (int i = 0; i < length; ++i) {
                result.add(serialize(context, Array.get(object, i)));
            }
            return result;
        } else {
            JsonSerializer serializer = getClassSerializer(object.getClass().getName());
            if (serializer == null) {
                throw new IllegalArgumentException("Can't serialize object of type " + object.getClass().getName());
            }
            return serializer.serialize(context, object);
        }
    }

    static native JsonSerializer getClassSerializer(String cls);

    public static Object deserialize(Node node, Class<?> type) {
        JsonDeserializer deserializer = getClassDeserializer(type);
        if (deserializer == null) {
            throw new IllegalArgumentException("Don't know how to deserialize " + type.getName());
        }
        return deserializer.deserialize(new JsonDeserializerContext(), node);
    }

    private static void ensureStandardDeserializers() {
        if (standardDeserializers != null) {
            return;
        }

        standardDeserializers = new HashMap<>();
        standardDeserializers.put(Object.class, null);
        standardDeserializers.put(Boolean.class, new BooleanDeserializer());
        standardDeserializers.put(Character.class, new CharacterDeserializer());
        standardDeserializers.put(Number.class, new NumberDeserializer());
        standardDeserializers.put(Byte.class, new ByteDeserializer());
        standardDeserializers.put(Short.class, new ShortDeserializer());
        standardDeserializers.put(Integer.class, new IntegerDeserializer());
        standardDeserializers.put(Long.class, new LongDeserializer());
        standardDeserializers.put(Float.class, new FloatDeserializer());
        standardDeserializers.put(Double.class, new DoubleDeserializer());
        standardDeserializers.put(String.class, new StringDeserializer());
    }

    static JsonDeserializer getClassDeserializer(Class<?> cls) {
        ensureStandardDeserializers();
        JsonDeserializer deserializer = standardDeserializers.get(cls);
        if (deserializer != null) {
            return deserializer;
        }

        if (cls.isArray()) {
            return new ArrayDeserializer(cls.getComponentType(), getClassDeserializer(cls.getComponentType()));
        }
        if (List.class.isAssignableFrom(cls)) {
            return new ListDeserializer(new ObjectDeserializer());
        }
        if (Set.class.isAssignableFrom(cls)) {
            return new SetDeserializer(deserializer);
        }
        if (Map.class.isAssignableFrom(cls)) {
            return new MapDeserializer(new StringDeserializer(), new ObjectDeserializer());
        }

        return findClassDeserializer(cls.getName());
    }

    static native JsonDeserializer findClassDeserializer(String cls);
}
