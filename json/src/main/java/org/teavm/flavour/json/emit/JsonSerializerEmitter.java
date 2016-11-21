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
package org.teavm.flavour.json.emit;

import static org.teavm.metaprogramming.Metaprogramming.emit;
import static org.teavm.metaprogramming.Metaprogramming.exit;
import static org.teavm.metaprogramming.Metaprogramming.findClass;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.serializer.BooleanSerializer;
import org.teavm.flavour.json.serializer.CharacterSerializer;
import org.teavm.flavour.json.serializer.DoubleSerializer;
import org.teavm.flavour.json.serializer.EnumSerializer;
import org.teavm.flavour.json.serializer.IntegerSerializer;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.serializer.ListSerializer;
import org.teavm.flavour.json.serializer.MapSerializer;
import org.teavm.flavour.json.serializer.StringSerializer;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NullNode;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.metaprogramming.Computation;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectAnnotatedElement;
import org.teavm.metaprogramming.reflect.ReflectField;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class JsonSerializerEmitter {
    private ClassInformationProvider informationProvider;
    private static Map<String, Class<?>> predefinedSerializers = new HashMap<>();

    static {
        predefinedSerializers.put(Boolean.class.getName(), BooleanSerializer.class);
        predefinedSerializers.put(Byte.class.getName(), IntegerSerializer.class);
        predefinedSerializers.put(Short.class.getName(), IntegerSerializer.class);
        predefinedSerializers.put(Character.class.getName(), CharacterSerializer.class);
        predefinedSerializers.put(Integer.class.getName(), IntegerSerializer.class);
        predefinedSerializers.put(Long.class.getName(), DoubleSerializer.class);
        predefinedSerializers.put(Float.class.getName(), DoubleSerializer.class);
        predefinedSerializers.put(Double.class.getName(), DoubleSerializer.class);
        predefinedSerializers.put(BigInteger.class.getName(), DoubleSerializer.class);
        predefinedSerializers.put(BigDecimal.class.getName(), DoubleSerializer.class);
        predefinedSerializers.put(String.class.getName(), StringSerializer.class);
    }

    public JsonSerializerEmitter() {
        informationProvider = ClassInformationProvider.getInstance();
    }

    public void returnClassSerializer(ReflectClass<?> cls) {
        Value<JsonSerializer> serializer = getClassSerializer(cls);
        exit(serializer != null ? serializer::get : () -> null);
    }

    public Value<JsonSerializer> getClassSerializer(ReflectClass<?> cls) {
        Value<JsonSerializer> serializer = tryGetPredefinedSerializer(cls);
        if (serializer == null) {
            serializer = emitClassSerializer(cls);
        }
        return serializer;
    }

    private Value<JsonSerializer> tryGetPredefinedSerializer(ReflectClass<?> cls) {
        Class<?> serializerType = !cls.isArray() ? predefinedSerializers.get(cls.getName()) : null;
        if (serializerType != null) {
            ReflectMethod ctor = findClass(serializerType).getDeclaredMethod("<init>");
            return emit(() -> (JsonSerializer) ctor.construct());
        }
        if (findClass(Enum.class).isAssignableFrom(cls)) {
            return emit(EnumSerializer::new);
        } else if (findClass(Map.class).isAssignableFrom(cls)) {
            return emit(MapSerializer::new);
        } else if (findClass(Collection.class).isAssignableFrom(cls)) {
            return emit(ListSerializer::new);
        }
        return null;
    }

    private Value<JsonSerializer> emitClassSerializer(ReflectClass<?> cls) {
        if (cls.isArray()) {
            return proxy(JsonSerializer.class, (instance, method, args) -> {
                Value<JsonSerializerContext> context = emit(() -> (JsonSerializerContext) args[0]);
                Value<Object> value = args[1];
                ReflectClass<?> componentType = cls.getComponentType();
                Value<JsonSerializer> componentSerializer = getPrimitiveSerializer(componentType);
                if (componentSerializer != null) {
                    exit(() -> {
                        ArrayNode target = ArrayNode.create();
                        int sz = cls.getArrayLength(value.get());
                        for (int i = 0; i < sz; ++i) {
                            Object component = cls.getArrayElement(value.get(), i);
                            target.add(componentSerializer.get().serialize(context.get(), component));
                        }
                        return target;
                    });
                } else {
                    exit(() -> {
                        ArrayNode target = ArrayNode.create();
                        int sz = cls.getArrayLength(value.get());
                        for (int i = 0; i < sz; ++i) {
                            Object component = cls.getArrayElement(value.get(), i);
                            target.add(JSON.serialize(context.get(), component));
                        }
                        return target;
                    });
                }
            });
        } else {
            ClassInformation information = informationProvider.get(cls.getName());
            if (information == null) {
                return null;
            }

            return proxy(JsonSerializer.class, (instance, method, args) -> {
                Value<JsonSerializerContext> context = emit(() -> (JsonSerializerContext) args[0]);
                Value<Object> value = args[1];
                Value<ObjectNode> target = emit(ObjectNode::create);
                emitIdentity(information, value, context, target);
                emitProperties(information, value, context, target);
                Value<? extends Node> result = emitInheritance(information, target);
                exit(result::get);
            });
        }
    }

    private Value<JsonSerializer> getPrimitiveSerializer(ReflectClass<?> cls) {
        if (cls.isPrimitive()) {
            switch (cls.getName()) {
                case "boolean":
                    return emit(BooleanSerializer::new);
                case "char":
                    return emit(CharacterSerializer::new);
                case "byte":
                case "short":
                case "int":
                    return emit(IntegerSerializer::new);
                case "long":
                case "float":
                case "double":
                    return emit(DoubleSerializer::new);
            }
        } else if (cls.getName().equals(String.class.getName())) {
            return emit(StringSerializer::new);
        }
        return null;
    }

    private void emitIdentity(ClassInformation information, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        switch (information.idGenerator) {
            case NONE:
                emit(() -> context.get().touch(value.get()));
                break;
            case INTEGER:
                emitIntegerIdentity(information, value, context, target);
            case PROPERTY:
                break;
        }
    }

    private void emitIntegerIdentity(ClassInformation information, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        String idProperty = information.idProperty;
        Value<Boolean> has = emit(() -> context.get().hasId(value.get()));
        Value<NumberNode> id = emit(() -> NumberNode.create(context.get().getId(value.get())));
        /*Choice<Object> choice = em.choose(Object.class);
        choice.option(() -> has.get()).returnValue(() -> id.get());
        choice.defaultOption().emit(() -> target.get().set(idProperty, id.get()));
        em.returnValue(choice.getValue());
        return choice.defaultOption();*/
    }

    private void emitProperties(ClassInformation information, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        for (PropertyInformation property : information.properties.values()) {
            if (property.ignored) {
                continue;
            }
            if (property.getter != null) {
                emitGetter(property, value, context, target);
            } else if (property.field != null) {
                emitField(property, value, context, target);
            }
        }
    }

    private Value<? extends Node> emitInheritance(ClassInformation information, Value<ObjectNode> target) {
        if (information.inheritance.key == null) {
            return target;
        }

        String typeName;
        switch (information.inheritance.value) {
            case CLASS:
                typeName = information.className;
                break;
            case MINIMAL_CLASS:
                typeName = ClassInformationProvider.getUnqualifiedName(information.className);
                break;
            case NAME:
                typeName = !information.typeName.isEmpty()
                        ? information.typeName
                        : ClassInformationProvider.getUnqualifiedName(information.className);
                break;
            default:
                return target;
        }

        String propertyName = information.inheritance.propertyName;
        switch (information.inheritance.key) {
            case PROPERTY:
                emit(() -> target.get().set(propertyName, StringNode.create(typeName)));
                break;
            case WRAPPER_OBJECT:
                return emit(() -> {
                    ObjectNode wrapper = ObjectNode.create();
                    wrapper.set(typeName, target.get());
                    return wrapper;
                });
            case WRAPPER_ARRAY:
                return emit(() -> {
                    ArrayNode wrapper = ArrayNode.create();
                    wrapper.add(StringNode.create(typeName));
                    wrapper.add(target.get());
                    return wrapper;
                });
        }
        return target;
    }

    private void emitGetter(PropertyInformation property, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        ReflectMethod method = property.getter;
        String outputName = property.outputName;
        Value<Node> propertyValue = convertValue(emit(() -> method.invoke(value.get())), context,
                method.getReturnType(), method);
        emit(() -> target.get().set(outputName, propertyValue.get()));
    }

    private void emitField(PropertyInformation property, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        ReflectField field = property.field;
        String outputName = property.outputName;

        Value<Node> propertyValue = convertValue(emit(() -> field.get(value.get())), context,
                field.getType(), field);
        emit(() -> target.get().set(outputName, propertyValue.get()));
    }

    private Value<Node> convertValue(Value<Object> value, Value<JsonSerializerContext> context,
            ReflectClass<?> type, ReflectAnnotatedElement annotations) {
        if (type.isPrimitive()) {
            return convertPrimitive(value, type);
        } else {
            return convertNullable(value, context, type, annotations);
        }
    }

    private Value<Node> convertNullable(Value<Object> value, Value<JsonSerializerContext> context,
            ReflectClass<?> type, ReflectAnnotatedElement annotations) {
        /*Choice<Node> choice = em.choose(Node.class);
        choice.option(() -> value.get() == null).returnValue(() -> NullNode.instance());
        Value<Node> result = convertObject(choice.defaultOption(), value, context, type, annotations);
        choice.defaultOption().returnValue(() -> result.get());
        return choice.getValue();*/
        return null;
    }

    private Value<Node> convertPrimitive(Value<Object> value, ReflectClass<?> type) {
        switch (type.getName()) {
            case "boolean":
                return emit(() -> BooleanNode.get((Boolean) value.get()));
            case "byte":
                return emit(() -> NumberNode.create((Byte) value.get()));
            case "short":
                return emit(() -> NumberNode.create((Short) value.get()));
            case "char":
                return emit(() -> NumberNode.create((Character) value.get()));
            case "int":
                return emit(() -> NumberNode.create((Integer) value.get()));
            case "long":
                return emit(() -> NumberNode.create((Long) value.get()));
            case "float":
                return emit(() -> NumberNode.create((Float) value.get()));
            case "double":
                return emit(() -> NumberNode.create((Double) value.get()));
        }
        throw new AssertionError("Unknown primitive type: " + type);
    }

    private Value<Node> convertObject(Value<Object> value, Value<JsonSerializerContext> context,
            ReflectClass<?> type, ReflectAnnotatedElement annotations) {
        if (!type.isArray()) {
            if (type.getName().equals(String.class.getName())) {
                return emit(() -> StringNode.create((String) value.get()));
            } else if (findClass(Date.class).isAssignableFrom(type)) {
                return convertDate(value, annotations);
            }
        }

        return emit(() -> JSON.serialize(context.get(), value.get()));
    }

    private Value<Node> convertDate(Value<Object> value, ReflectAnnotatedElement annotations) {
        DateFormatInformation formatInfo = DateFormatInformation.get(annotations);
        if (formatInfo.asString) {
            String localeName = formatInfo.locale;
            String pattern = formatInfo.pattern;
            Value<Locale> locale = formatInfo.locale != null
                    ? emit(() -> new Locale(localeName))
                    : emit((Computation<Locale>) Locale::getDefault);
            return emit(() -> {
                DateFormat format = new SimpleDateFormat(pattern, locale.get());
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                return StringNode.create(format.format((Date) value.get()));
            });
        } else {
            return emit(() -> {
                Date date = (Date) value.get();
                return NumberNode.create(date.getTime());
            });
        }
    }
}
