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

import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.json.deserializer.BooleanDeserializer;
import org.teavm.flavour.json.deserializer.ByteDeserializer;
import org.teavm.flavour.json.deserializer.CharacterDeserializer;
import org.teavm.flavour.json.deserializer.DoubleDeserializer;
import org.teavm.flavour.json.deserializer.FloatDeserializer;
import org.teavm.flavour.json.deserializer.IntegerDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.LongDeserializer;
import org.teavm.flavour.json.deserializer.NumberDeserializer;
import org.teavm.flavour.json.deserializer.ShortDeserializer;
import org.teavm.flavour.json.deserializer.StringDeserializer;
import org.teavm.flavour.json.emit.JsonDeserializerEmitter;
import org.teavm.flavour.json.emit.JsonSerializerEmitter;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NullNode;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.flavour.mp.CompileTime;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;

/**
 *
 * @author Alexey Andreev
 */
@CompileTime
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
        } else {
            JsonSerializer serializer = getObjectSerializer(object);
            if (serializer == null) {
                throw new IllegalArgumentException("Can't serialize object of type " + object.getClass().getName());
            }
            return serializer.serialize(context, object);
        }
    }

    @Reflected
    static native JsonSerializer getObjectSerializer(Object obj);
    static void getObjectSerializer(Emitter<JsonSerializer> em, ReflectValue<Object> value) {
        ReflectClass<?> cls = value.getReflectClass();
        em.returnValue(() -> getClassSerializer(cls.asJavaClass()));
    }

    @Reflected
    public static native JsonSerializer getClassSerializer(Class<?> cls);
    public static void getClassSerializer(Emitter<JsonSerializer> em, ReflectClass<?> cls) {
        new JsonSerializerEmitter(em).returnClassSerializer(cls);
    }

    @Reflected
    public static native <T> T deserialize(Node node, Class<T> type);
    @SuppressWarnings("unchecked")
    private static <T> void deserialize(Emitter<T> em, Value<Node> node, ReflectClass<T> type) {
        String typeName = type.getName();
        em.returnValue(() -> {
            JsonDeserializer deserializer = getClassDeserializer(type.asJavaClass());
            if (deserializer == null) {
                throw new IllegalArgumentException("Don't know how to deserialize " + typeName);
            }
            return (T) deserializer.deserialize(new JsonDeserializerContext(), node.get());
        });
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

    @Reflected
    public static native JsonDeserializer getClassDeserializer(Class<?> cls);
    private static void getClassDeserializer(Emitter<JsonDeserializer> em, ReflectClass<?> cls) {
        em.returnValue(new JsonDeserializerEmitter(em).getClassDeserializer(cls));
    }

    public static boolean deserializeBoolean(Node node) {
        if (!node.isBoolean()) {
            throw new IllegalArgumentException("Can't deserialize non-boolean not as a boolean primitive");
        }
        return ((BooleanNode) node).getValue();
    }

    public static byte deserializeByte(Node node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-numeric node as a byte primitive");
        }
        NumberNode number = (NumberNode) node;
        return (byte) number.getIntValue();
    }

    public static short deserializeShort(Node node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-numeric node as a short primitive");
        }
        NumberNode number = (NumberNode) node;
        return (short) number.getIntValue();
    }

    public static int deserializeInt(Node node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-numeric node as an int primitive");
        }
        NumberNode number = (NumberNode) node;
        return number.getIntValue();
    }

    public static long deserializeLong(Node node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-numeric node as a long primitive");
        }
        NumberNode number = (NumberNode) node;
        return (long) number.getValue();
    }

    public static float deserializeFloat(Node node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-numeric node as a float primitive");
        }
        NumberNode number = (NumberNode) node;
        return (float) number.getValue();
    }

    public static double deserializeDouble(Node node) {
        if (!node.isNumber()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-numeric node as a double primitive");
        }
        NumberNode number = (NumberNode) node;
        return number.getValue();
    }

    public static char deserializeChar(Node node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("Don't know how to deserialize non-string node as a char");
        }

        String value = ((StringNode) node).getValue();
        if (value.length() != 1) {
            throw new IllegalArgumentException("String must be exactly one char length to be deserialized as a char");
        }

        return value.charAt(0);
    }
}
