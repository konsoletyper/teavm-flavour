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
import static org.teavm.metaprogramming.Metaprogramming.getClassLoader;
import static org.teavm.metaprogramming.Metaprogramming.lazyFragment;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import static org.teavm.metaprogramming.Metaprogramming.unsupportedCase;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.JsonPersistable;
import org.teavm.flavour.json.serializer.ArraySerializer;
import org.teavm.flavour.json.serializer.BooleanArraySerializer;
import org.teavm.flavour.json.serializer.BooleanSerializer;
import org.teavm.flavour.json.serializer.ByteArraySerializer;
import org.teavm.flavour.json.serializer.CharArraySerializer;
import org.teavm.flavour.json.serializer.CharacterSerializer;
import org.teavm.flavour.json.serializer.DoubleArraySerializer;
import org.teavm.flavour.json.serializer.DoubleSerializer;
import org.teavm.flavour.json.serializer.EnumSerializer;
import org.teavm.flavour.json.serializer.FloatArraySerializer;
import org.teavm.flavour.json.serializer.IntArraySerializer;
import org.teavm.flavour.json.serializer.IntegerSerializer;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.serializer.ListSerializer;
import org.teavm.flavour.json.serializer.LongArraySerializer;
import org.teavm.flavour.json.serializer.MapSerializer;
import org.teavm.flavour.json.serializer.ObjectSerializer;
import org.teavm.flavour.json.serializer.ShortArraySerializer;
import org.teavm.flavour.json.serializer.StringSerializer;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NullNode;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectAnnotatedElement;
import org.teavm.metaprogramming.reflect.ReflectField;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class JsonSerializerEmitter {
    private ClassInformationProvider informationProvider;
    private GenericTypeProvider genericTypeProvider = new GenericTypeProvider(getClassLoader());
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
        if (serializer == null) {
            unsupportedCase();
            return;
        }
        exit(() -> serializer.get());
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
        if (cls.isEnum()) {
            if (cls.getAnnotation(JsonPersistable.class) == null) {
                return null;
            }
            return emit(() -> new EnumSerializer());
        } else if (findClass(Map.class).isAssignableFrom(cls)) {
            Value<JsonSerializer> itemSerializer = createObjectSerializer(Object.class);
            return emit(() -> new MapSerializer(itemSerializer.get(), itemSerializer.get()));
        } else if (findClass(Collection.class).isAssignableFrom(cls)) {
            Value<JsonSerializer> itemSerializer = createObjectSerializer(Object.class);
            return emit(() -> new ListSerializer(itemSerializer.get()));
        }
        return null;
    }

    private Value<JsonSerializer> emitClassSerializer(ReflectClass<?> cls) {
        if (cls.isArray()) {
            ReflectClass<?> componentType = cls.getComponentType();
            Value<JsonSerializer> componentSerializer = getPrimitiveSerializer(componentType);
            if (componentSerializer != null) {
                return proxy(JsonSerializer.class, (instance, method, args) -> {
                    Value<JsonSerializerContext> context = emit(() -> (JsonSerializerContext) args[0]);
                    Value<Object> value = args[1];
                    exit(() -> {
                        ArrayNode target = ArrayNode.create();
                        int sz = cls.getArrayLength(value.get());
                        for (int i = 0; i < sz; ++i) {
                            Object component = cls.getArrayElement(value.get(), i);
                            target.add(componentSerializer.get().serialize(context.get(), component));
                        }
                        return target;
                    });
                });
            } else {
                Value<JsonSerializer> objectComponentSerializer;
                if (componentType.getName().equals("java.lang.Object")) {
                    objectComponentSerializer = emit(() -> ObjectSerializer.INSTANCE);
                } else {
                    if (getClassSerializer(componentType) == null) {
                        return null;
                    }
                    objectComponentSerializer = proxy(JsonSerializer.class, (instance, method, args) -> {
                        Value<Object> context = args[0];
                        Value<Object> value = args[1];
                        Value<Node> result = emit(() -> JSON.serialize((JsonSerializerContext) context.get(),
                                componentType.cast(value.get())));
                        exit(() -> result.get());
                    });
                }
                return emit(() -> new ArraySerializer(objectComponentSerializer.get()));
            }
        } else {
            ClassInformation information = informationProvider.get(cls.getName());
            if (information == null || !information.persistable) {
                return null;
            }

            return proxy(JsonSerializer.class, (instance, method, args) -> {
                Value<JsonSerializerContext> context = emit(() -> (JsonSerializerContext) args[0]);
                Value<Object> value = args[1];
                Value<ObjectNode> target = emit(() -> ObjectNode.create());
                emitIdentity(information, value, context, target);
                emitProperties(information, value, context, target);
                Value<? extends Node> result = emitInheritance(information, target);
                exit(() -> result.get());
            });
        }
    }

    private Value<JsonSerializer> getPrimitiveSerializer(ReflectClass<?> cls) {
        if (cls.isPrimitive()) {
            switch (cls.getName()) {
                case "boolean":
                    return emit(() -> new BooleanSerializer());
                case "char":
                    return emit(() -> new CharacterSerializer());
                case "byte":
                case "short":
                case "int":
                    return emit(() -> new IntegerSerializer());
                case "long":
                case "float":
                case "double":
                    return emit(() -> new DoubleSerializer());
            }
        } else if (cls.getName().equals(String.class.getName())) {
            return emit(() -> new StringSerializer());
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
                break;
            case PROPERTY:
                break;
        }
    }

    private void emitIntegerIdentity(ClassInformation information, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        String idProperty = information.idProperty;
        Value<Boolean> has = emit(() -> context.get().hasId(value.get()));
        Value<NumberNode> id = emit(() -> NumberNode.create(context.get().getId(value.get())));

        Value<Object> returnIntegerId = lazyFragment(() -> {
            exit(() -> id.get());
            return null;
        });

        emit(() -> {
            if (has.get()) {
                returnIntegerId.get();
            } else {
                target.get().set(idProperty, id.get());
            }
        });
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
        Method javaMethod = genericTypeProvider.findMethod(method);
        Type type = javaMethod.getGenericReturnType();
        Value<Node> propertyValue = convertValue(emit(() -> method.invoke(value.get())), context, type, method);
        emit(() -> target.get().set(outputName, propertyValue.get()));
    }

    private void emitField(PropertyInformation property, Value<Object> value,
            Value<JsonSerializerContext> context, Value<ObjectNode> target) {
        ReflectField field = property.field;
        Field javaField = genericTypeProvider.findField(field);
        String outputName = property.outputName;

        Value<Node> propertyValue = convertValue(emit(() -> field.get(value.get())), context,
                javaField.getGenericType(), field);
        emit(() -> target.get().set(outputName, propertyValue.get()));
    }

    private Value<Node> convertValue(Value<Object> value, Value<JsonSerializerContext> context,
            Type type, ReflectAnnotatedElement annotations) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (!cls.isArray()) {
                if (cls.getName().equals(String.class.getName())) {
                    return emit(() -> StringNode.create((String) value.get()));
                } else if (findClass(Date.class).isAssignableFrom(cls)) {
                    return convertDate(value, annotations);
                }
            }
            if (cls.isPrimitive()) {
                return convertPrimitive(value, cls);
            }
        }

        return convertNullable(value, context, type, annotations);
    }

    private Value<JsonSerializer> createSerializer(Type type, ReflectAnnotatedElement annotations) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            return cls.isArray()
                    ? createArraySerializer(cls.getComponentType(), annotations)
                    : createObjectSerializer(cls);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapSerializer(typeArgs[0], typeArgs[1], annotations);
            } else if (paramType.getRawType().equals(List.class) || paramType.getRawType().equals(Set.class)) {
                return createListSerializer(typeArgs[0], annotations);
            } else {
                return createSerializer(paramType.getRawType(), annotations);
            }
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            return createArraySerializer(arrayType, annotations);
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectSerializer(upperCls);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>) type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }

            return createObjectSerializer(upperCls);
        } else {
            return createObjectSerializer(Object.class);
        }
    }

    private Value<JsonSerializer> createArraySerializer(Class<?> type, ReflectAnnotatedElement annotations) {
        if (type.isPrimitive()) {
            String name = type.getName();
            switch (name) {
                case "boolean":
                    return emit(() -> new BooleanArraySerializer());
                case "byte":
                    return emit(() -> new ByteArraySerializer());
                case "short":
                    return emit(() -> new ShortArraySerializer());
                case "char":
                    return emit(() -> new CharArraySerializer());
                case "int":
                    return emit(() -> new IntArraySerializer());
                case "long":
                    return emit(() -> new LongArraySerializer());
                case "float":
                    return emit(() -> new FloatArraySerializer());
                case "double":
                    return emit(() -> new DoubleArraySerializer());
            }
        }
        Value<JsonSerializer> itemSerializer = createSerializer(type, annotations);
        return emit(() -> new ArraySerializer(itemSerializer.get()));
    }

    private Value<JsonSerializer> createArraySerializer(GenericArrayType type,
            ReflectAnnotatedElement annotations) {
        Value<JsonSerializer> itemSerializer = createSerializer(type.getGenericComponentType(), annotations);
        return emit(() -> new ArraySerializer(itemSerializer.get()));
    }

    private Value<JsonSerializer> createObjectSerializer(Class<?> type) {
        ReflectClass<?> cls = findClass(type);
        return proxy(JsonSerializer.class, (instance, method, args) -> {
            Value<Object> castValue = emit(() -> cls.cast(args[1].get()));
            Value<Node> result = emit(() -> JSON.serialize((JsonSerializerContext) args[0].get(), castValue.get()));
            exit(() -> result.get());
        });
    }

    private Value<Node> convertNullable(Value<Object> value, Value<JsonSerializerContext> context, Type type,
            ReflectAnnotatedElement annotations) {
        Value<Node> result = lazyFragment(() -> {
            Value<JsonSerializer> serializer = createSerializer(type, annotations);
            return emit(() -> serializer.get().serialize(context.get(), value.get()));
        });
        return emit(() -> value.get() == null ? NullNode.instance() : result.get());
    }

    private Value<Node> convertPrimitive(Value<Object> value, Class<?> type) {
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

    private Value<JsonSerializer> createListSerializer(Type type, ReflectAnnotatedElement annotations) {
        Value<JsonSerializer> itemSerializer = createSerializer(type, annotations);
        return emit(() -> new ListSerializer(itemSerializer.get()));
    }

    private Value<JsonSerializer> createMapSerializer(Type keyType, Type valueType,
            ReflectAnnotatedElement annotations) {
        Value<JsonSerializer> keySerializer = createSerializer(keyType, annotations);
        Value<JsonSerializer> valueSerializer = createSerializer(valueType, annotations);
        return emit(() -> new MapSerializer(keySerializer.get(), valueSerializer.get()));
    }

    private Value<Node> convertDate(Value<Object> value, ReflectAnnotatedElement annotations) {
        DateFormatInformation formatInfo = DateFormatInformation.get(annotations);
        if (formatInfo.asString) {
            String localeName = formatInfo.locale;
            String pattern = formatInfo.pattern;
            Value<Locale> locale = formatInfo.locale != null
                    ? emit(() -> new Locale(localeName))
                    : emit(() -> Locale.getDefault());
            return emit(() -> {
                if (value.get() == null) {
                    return NullNode.instance();
                }
                DateFormat format = new SimpleDateFormat(pattern, locale.get());
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                return StringNode.create(format.format((Date) value.get()));
            });
        } else {
            return emit(() -> {
                Date date = (Date) value.get();
                return date != null ? NumberNode.create(date.getTime()) : NullNode.instance();
            });
        }
    }
}
