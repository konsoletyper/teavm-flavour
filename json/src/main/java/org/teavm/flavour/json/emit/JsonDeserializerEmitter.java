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
import static org.teavm.metaprogramming.Metaprogramming.getClassLoader;
import static org.teavm.metaprogramming.Metaprogramming.getDiagnostics;
import static org.teavm.metaprogramming.Metaprogramming.lazy;
import static org.teavm.metaprogramming.Metaprogramming.lazyFragment;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.BooleanArrayDeserializer;
import org.teavm.flavour.json.deserializer.BooleanDeserializer;
import org.teavm.flavour.json.deserializer.ByteArrayDeserializer;
import org.teavm.flavour.json.deserializer.ByteDeserializer;
import org.teavm.flavour.json.deserializer.CharArrayDeserializer;
import org.teavm.flavour.json.deserializer.CharacterDeserializer;
import org.teavm.flavour.json.deserializer.DoubleArrayDeserializer;
import org.teavm.flavour.json.deserializer.DoubleDeserializer;
import org.teavm.flavour.json.deserializer.FloatArrayDeserializer;
import org.teavm.flavour.json.deserializer.FloatDeserializer;
import org.teavm.flavour.json.deserializer.IntArrayDeserializer;
import org.teavm.flavour.json.deserializer.IntegerDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.ListDeserializer;
import org.teavm.flavour.json.deserializer.LongArrayDeserializer;
import org.teavm.flavour.json.deserializer.LongDeserializer;
import org.teavm.flavour.json.deserializer.MapDeserializer;
import org.teavm.flavour.json.deserializer.NullableDeserializer;
import org.teavm.flavour.json.deserializer.ObjectDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.deserializer.ShortArrayDeserializer;
import org.teavm.flavour.json.deserializer.ShortDeserializer;
import org.teavm.flavour.json.deserializer.StringDeserializer;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.metaprogramming.Diagnostics;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectAnnotatedElement;
import org.teavm.metaprogramming.reflect.ReflectField;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class JsonDeserializerEmitter {
    private Diagnostics diagnostics = getDiagnostics();
    private ClassLoader classLoader = getClassLoader();
    private ClassInformationProvider informationProvider = ClassInformationProvider.getInstance();
    private static Map<String, Class<?>> predefinedDeserializers = new HashMap<>();

    static {
        predefinedDeserializers.put(Object.class.getName(), BooleanDeserializer.class);
        predefinedDeserializers.put(Number.class.getName(), BooleanDeserializer.class);
        predefinedDeserializers.put(Boolean.class.getName(), BooleanDeserializer.class);
        predefinedDeserializers.put(Byte.class.getName(), ByteDeserializer.class);
        predefinedDeserializers.put(Short.class.getName(), ShortDeserializer.class);
        predefinedDeserializers.put(Character.class.getName(), CharacterDeserializer.class);
        predefinedDeserializers.put(Integer.class.getName(), IntegerDeserializer.class);
        predefinedDeserializers.put(Long.class.getName(), LongDeserializer.class);
        predefinedDeserializers.put(Float.class.getName(), FloatDeserializer.class);
        predefinedDeserializers.put(Double.class.getName(), DoubleDeserializer.class);
        predefinedDeserializers.put(String.class.getName(), StringDeserializer.class);
    }

    public Value<? extends JsonDeserializer> getClassDeserializer(ReflectClass<?> cls) {
        Value<? extends JsonDeserializer> deserializer = tryGetPredefinedDeserializer(cls);
        if (deserializer == null) {
            if (cls.isArray()) {
                deserializer = emitArrayDeserializer(cls);
            } else if (cls.isEnum()) {
                deserializer = emitEnumDeserializer(cls);
            } else {
                deserializer = emitClassDeserializer(cls);
            }
        }

        return deserializer;
    }

    private Value<JsonDeserializer> tryGetPredefinedDeserializer(ReflectClass<?> cls) {
        if (cls.isArray() || cls.isPrimitive()) {
            return null;
        }

        Class<?> serializerClass = predefinedDeserializers.get(cls.getName());
        if (serializerClass != null) {
            ReflectMethod ctor = Metaprogramming.findClass(serializerClass.getName()).getMethod("<init>");
            return emit(() -> (JsonDeserializer) ctor.construct());
        }

        if (Metaprogramming.findClass(Map.class).isAssignableFrom(cls)) {
            return emit(() -> new MapDeserializer(new ObjectDeserializer(), new ObjectDeserializer()));
        } else if (Metaprogramming.findClass(Collection.class).isAssignableFrom(cls)) {
            return emit(() -> new ListDeserializer(new ObjectDeserializer()));
        }

        return null;
    }

    private Value<? extends JsonDeserializer> emitArrayDeserializer(ReflectClass<?> cls) {
        if (cls.getComponentType().isPrimitive()) {
            String name = cls.getComponentType().getName();
            switch (name) {
                case "boolean":
                    return emit(() -> new BooleanArrayDeserializer());
                case "byte":
                    return emit(() -> new ByteArrayDeserializer());
                case "short":
                    return emit(() -> new ShortArrayDeserializer());
                case "char":
                    return emit(() -> new CharArrayDeserializer());
                case "int":
                    return emit(() -> new IntArrayDeserializer());
                case "long":
                    return emit(() -> new LongArrayDeserializer());
                case "float":
                    return emit(() -> new FloatArrayDeserializer());
                case "double":
                    return emit(() -> new DoubleArrayDeserializer());
            }
        }
        Value<? extends JsonDeserializer> itemDeserializer = getClassDeserializer(cls);
        return emit(() -> new ArrayDeserializer(cls.asJavaClass(), itemDeserializer.get()));
    }

    private Value<? extends JsonDeserializer> emitEnumDeserializer(ReflectClass<?> cls) {
        return proxy(NullableDeserializer.class, (instance, method, args) -> {
            Value<Node> node = emit(() -> (Node) args[1]);
            emitEnumDeserializer(cls, node);
        });
    }

    private Value<? extends JsonDeserializer> emitClassDeserializer(ReflectClass<?> cls) {
        return proxy(NullableDeserializer.class, (instance, method, args) -> {
            ClassInformation information = informationProvider.get(cls.getName());
            Value<JsonDeserializerContext> context = emit(() -> (JsonDeserializerContext) args[0]);
            Value<Node> node = emit(() -> (Node) args[1]);

            Value<Object> result = lazyFragment(() -> emitSubTypes(information, node, context, contentNode -> {
                Value<Object> target = emitConstructor(information, contentNode, context);
                emitIdRegistration(information, target, contentNode, context);
                emitProperties(information, target, contentNode, context);
                return emit(() -> target.get());
            }));
            result = emitNodeTypeCheck(node, result, emitIdCheck(information, node, context));

            Value<Object> finalResult = result;
            exit(() -> finalResult.get());
        });
    }

    private void emitEnumDeserializer(ReflectClass<?> cls, Value<Node> node) {
        String className = cls.getName();

        Value<String> text = emit(() -> ((StringNode) node.get()).getValue());
        Value<Object> result = lazy(() -> {
            throw new IllegalArgumentException("Can't convert to " + className + ": "
                    + node.get().stringify());
        });

        for (ReflectField field : cls.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                String fieldName = field.getName();
                Value<Object> currentResult = result;
                result = lazy(() -> text.get().equals(fieldName) ? field.get(null) : currentResult.get());
            }
        }

        Value<Object> finalResult = result;
        exit(() -> {
            if (!node.get().isString()) {
                throw new IllegalArgumentException("Can't convert to " + className + ": "
                        + node.get().stringify());
            } else {
                return finalResult;
            }
        });
    }

    private Value<Object> emitIdCheck(ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        String className = information.className;
        switch (information.idGenerator) {
            case INTEGER:
                return emitIntegerIdCheck(information, node, context);
            case PROPERTY:
                return emitPropertyIdCheck(information, node, context);
            case NONE:
                return lazy(() -> {
                    throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                            + " to an instance of " + className);
                });
            default:
                throw new AssertionError("Unsupported id kind: " + information.idGenerator);
        }
    }

    private Value<Object> emitIntegerIdCheck(ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        String className = information.className;
        return lazy(() -> {
            if (node.get().isNumber()) {
                NumberNode number = (NumberNode) node.get();
                return context.get().get(number.getIntValue());
            } else {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            }
        });
    }

    private Value<Object> emitPropertyIdCheck(ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        PropertyInformation property = information.properties.get(information.idProperty);
        String className = information.className;
        if (property == null) {
            return lazy(() -> {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            });
        }

        Type type = getPropertyGenericType(property);

        if (type != null) {
            Value<Object> converted = convert(node, context, type, property.setter);
            return lazy(() -> context.get().get(converted.get()));
        } else {
            return lazy(() -> {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            });
        }
    }

    private Value<Object> emitNodeTypeCheck(Value<Node> node, Value<Object> defaultValue, Value<Object> idValue) {
        return lazy(() -> node.get().isArray() || node.get().isObject() ? defaultValue.get() : idValue.get());
    }

    private Value<Object> emitSubTypes(ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context, Function<Value<ObjectNode>, Value<Object>> consumer) {
        if (information.inheritance.subTypes.isEmpty() || information.inheritance.value == InheritanceValue.NONE) {
            return consumer.apply(lazy(() -> (ObjectNode) node.get()));
        }

        ObjectWithTag taggedObject = emitTypeNameExtractor(information, node);
        if (taggedObject == null) {
            return consumer.apply(lazy(() -> (ObjectNode) node.get()));
        }

        Value<ObjectNode> contentNode = taggedObject.object;
        Value<String> tag = taggedObject.tag;

        Value<Object> result = lazy(() -> {
            throw new IllegalArgumentException("Invalid type tag: " + tag.get());
        });
        for (ClassInformation subType : information.inheritance.subTypes) {
            String typeName = getTypeName(information, subType);
            ReflectClass<?> subclass = Metaprogramming.findClass(subType.className);
            Value<Object> currentResult = result;
            result = lazy(() -> {
                if (tag.get().equals(typeName)) {
                    return JSON.getClassDeserializer(subclass.asJavaClass())
                            .deserialize(context.get(), contentNode.get());
                } else {
                    return currentResult.get();
                }
            });
        }

        Value<Object> finalResult = result;
        String defaultTypeName = getTypeName(information, information);
        Value<Object> defaultValue = consumer.apply(taggedObject.object);
        return lazy(() -> tag.get().equals(defaultTypeName) ? defaultValue.get() : finalResult.get());
    }

    private ObjectWithTag emitTypeNameExtractor(ClassInformation information, Value<Node> node) {
        switch (information.inheritance.key) {
            case PROPERTY:
                return emitPropertyTypeNameExtractor(information, node);
            case WRAPPER_ARRAY:
                return emitArrayTypeNameExtractor(node);
            case WRAPPER_OBJECT:
                return emitObjectTypeNameExtractor(node);
        }
        return null;
    }

    private ObjectWithTag emitPropertyTypeNameExtractor(ClassInformation information,
            Value<Node> node) {
        String propertyName = information.inheritance.propertyName;
        String defaultTypeName = getTypeName(information, information);
        Value<ObjectNode> contentNode = emit(() -> (ObjectNode) node.get());
        Value<String> typeName = emit(() -> contentNode.get().has(propertyName)
                ? ((StringNode) contentNode.get().get(propertyName)).getValue()
                : defaultTypeName);
        return new ObjectWithTag(typeName, contentNode);
    }

    private ObjectWithTag emitArrayTypeNameExtractor(Value<Node> node) {
        Value<ArrayNode> array = emit(() -> (ArrayNode) node.get());
        Value<String> tag = emit(() -> ((StringNode) array.get().get(0)).getValue());
        Value<ObjectNode> valueNode = emit(() -> (ObjectNode) array.get().get(1));
        return new ObjectWithTag(tag, valueNode);
    }

    private ObjectWithTag emitObjectTypeNameExtractor(Value<Node> node) {
        Value<ObjectNode> obj = emit(() -> (ObjectNode) node.get());
        Value<String> tag = emit(() -> obj.get().allKeys()[0]);
        Value<ObjectNode> valueNode = emit(() -> (ObjectNode) obj.get().get(tag.get()));
        return new ObjectWithTag(tag, valueNode);
    }

    private String getTypeName(ClassInformation baseType, ClassInformation type) {
        switch (baseType.inheritance.value) {
            case CLASS:
                return type.className;
            case MINIMAL_CLASS:
                return ClassInformationProvider.getUnqualifiedName(type.className);
            case NAME:
                return !type.typeName.isEmpty() ? type.typeName
                        : ClassInformationProvider.getUnqualifiedName(type.className);
            case NONE:
                break;
        }
        return "";
    }

    private Value<Object> emitConstructor(ClassInformation information, Value<ObjectNode> node,
            Value<JsonDeserializerContext> context) {
        if (information.constructor == null) {
            diagnostics.error(null, "Neither non-argument constructor nor @JsonCreator were found in {{c0}}",
                    information.className);
            return emit(() -> null);
        }

        int paramCount = information.constructorArgs.size();
        Value<Object[]> args = emit(() -> new Object[paramCount]);
        Type[] genericTypes;
        if (information.constructor.getName().equals("<init>")) {
            Constructor<?> javaCtor = findConstructor(information.constructor);
            genericTypes = javaCtor.getGenericParameterTypes();
        } else {
            Method javaMethod = findMethod(information.constructor);
            genericTypes = javaMethod.getGenericParameterTypes();
        }

        for (int i = 0; i < paramCount; ++i) {
            PropertyInformation property = information.constructorArgs.get(i);
            Value<Object> paramValue;
            if (property != null) {
                String propertyName = property.outputName;
                Type type = genericTypes[i];
                Value<Node> valueNode = emit(() -> node.get().get(propertyName));
                paramValue = convert(valueNode, context, type, information.constructor.getParameterAnnotations(i));
            } else {
                paramValue = defaultValue(information.constructor.getParameterType(i));
            }
            int index = i;
            emit(() -> args.get()[index] = paramValue.get());
        }

        ReflectMethod ctor = information.constructor;
        return ctor.getName().equals("<init>")
                ? emit(() -> ctor.construct(args.get()))
                : emit(() -> ctor.invoke(null, args.get()));
    }

    private void emitIdRegistration(ClassInformation information, Value<Object> object,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        Value<Void> register = lazyFragment(() -> {
            Value<Object> id;
            switch (information.idGenerator) {
                case INTEGER:
                    id = emitIntegerIdRegistration(information, node);
                    break;
                case PROPERTY:
                    id = emitPropertyIdRegistration(information, node, context);
                    break;
                default:
                    id = null;
                    break;
            }
            if (id != null) {
                emit(() -> context.get().register(id.get(), object.get()));
            }
            return null;
        });

        String idProperty = information.idProperty;
        if (idProperty != null) {
            emit(() -> {
                if (node.get().has(idProperty)) {
                    register.get();
                }
            });
        }
    }

    private Value<Object> emitIntegerIdRegistration(ClassInformation information, Value<ObjectNode> node) {
        String idProperty = information.idProperty;
        return emit(() -> JSON.deserializeInt(node.get().get(idProperty)));
    }

    private Value<Object> emitPropertyIdRegistration(ClassInformation information,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        PropertyInformation property = information.properties.get(information.idProperty);
        if (property == null) {
            return null;
        }

        String idProperty = information.idProperty;
        Value<Node> id = emit(() -> node.get().get(idProperty));
        Type type = getPropertyGenericType(property);

        if (type == null) {
            return null;
        }
        return convert(id, context, type, property.setter);
    }

    private void emitProperties(ClassInformation information, Value<Object> target,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        for (PropertyInformation property : information.properties.values()) {
            if (property.ignored) {
                continue;
            }
            if (property.setter != null) {
                emitSetter(property, target, node, context);
            } else if (property.field != null) {
                emitField(property, target, node, context);
            }
        }
    }

    private void emitSetter(PropertyInformation property, Value<Object> target,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        ReflectMethod method = property.setter;
        Method javaMethod = findMethod(method);
        Type type = javaMethod.getGenericParameterTypes()[0];

        String propertyName = property.outputName;
        Value<Node> jsonValue = emit(() -> node.get().get(propertyName));
        Value<Object> value = convert(jsonValue, context, type, method);
        emit(() -> method.invoke(target.get(), value.get()));
    }

    private void emitField(PropertyInformation property, Value<Object> target,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        ReflectField field = property.field;
        Field javaField = findField(field);
        Type type = javaField.getGenericType();

        String propertyName = property.outputName;
        Value<Node> jsonValue = emit(() -> node.get().get(propertyName));
        Value<Object> value = convert(jsonValue, context, type, field);
        emit(() -> field.set(target.get(), value.get()));
    }

    private Type getPropertyGenericType(PropertyInformation property) {
        Type type = null;
        if (property.getter != null) {
            Method getter = findMethod(property.getter);
            if (getter != null) {
                type = getter.getGenericReturnType();
            }
        }
        if (type == null && property.field != null) {
            Field field = findField(property.field);
            if (field != null) {
                type = field.getGenericType();
            }
        }
        return type;
    }

    private Value<Object> convert(Value<Node> node, Value<JsonDeserializerContext> context, Type type,
            ReflectAnnotatedElement annotations) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (cls.isPrimitive()) {
                return convertPrimitive(node, cls);
            }
        }
        return convertNullable(node, context, type, annotations);
    }

    private Value<Object> convertNullable(Value<Node> node, Value<JsonDeserializerContext> context, Type type,
            ReflectAnnotatedElement annotations) {
        if (type instanceof Class<?> && Date.class.isAssignableFrom((Class<?>) type)) {
            return convertDate(node, annotations);
        }
        Value<JsonDeserializer> deserializer = createDeserializer(type, annotations);
        return emit(() -> deserializer.get().deserialize(context.get(), node.get()));
    }

    private Value<JsonDeserializer> createDeserializer(Type type, ReflectAnnotatedElement annotations) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            return cls.isArray() ? createArrayDeserializer(cls, annotations) : createObjectDeserializer(cls);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapDeserializer(typeArgs[0], typeArgs[1], annotations);
            } else if (paramType.getRawType().equals(List.class)) {
                return createListDeserializer(typeArgs[0], annotations);
            } else if (paramType.getRawType().equals(Set.class)) {
                return createSetDeserializer(typeArgs[0], annotations);
            } else {
                return createDeserializer(paramType.getRawType(), annotations);
            }
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(upperCls);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>) type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(upperCls);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            return createArrayDeserializer(array, annotations);
        } else {
            return createObjectDeserializer(Object.class);
        }
    }

    private Value<Object> convertPrimitive(Value<Node> node, Class<?> type) {
        switch (type.getName()) {
            case "boolean":
                return emit(() -> JSON.deserializeBoolean(node.get()));
            case "byte":
                return emit(() -> JSON.deserializeByte(node.get()));
            case "short":
                return emit(() -> JSON.deserializeShort(node.get()));
            case "int":
                return emit(() -> JSON.deserializeInt(node.get()));
            case "long":
                return emit(() -> JSON.deserializeLong(node.get()));
            case "float":
                return emit(() -> JSON.deserializeFloat(node.get()));
            case "double":
                return emit(() -> JSON.deserializeDouble(node.get()));
            case "char":
                return emit(() -> JSON.deserializeChar(node.get()));
        }
        throw new AssertionError("Unknown primitive type: " + type);
    }

    private Value<JsonDeserializer> createArrayDeserializer(GenericArrayType type,
            ReflectAnnotatedElement annotations) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(type.getGenericComponentType(), annotations);
        Class<?> cls = rawType(type);
        return emit(() -> new ArrayDeserializer(cls, itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createArrayDeserializer(Class<?> type, ReflectAnnotatedElement annotations) {
        if (type.getComponentType().isPrimitive()) {
            String name = type.getComponentType().getName();
            switch (name) {
                case "boolean":
                    return emit(() -> new BooleanArrayDeserializer());
                case "byte":
                    return emit(() -> new ByteArrayDeserializer());
                case "short":
                    return emit(() -> new ShortArrayDeserializer());
                case "char":
                    return emit(() -> new CharArrayDeserializer());
                case "int":
                    return emit(() -> new IntArrayDeserializer());
                case "long":
                    return emit(() -> new LongArrayDeserializer());
                case "float":
                    return emit(() -> new FloatArrayDeserializer());
                case "double":
                    return emit(() -> new DoubleArrayDeserializer());
            }
        }
        Value<JsonDeserializer> itemDeserializer = createDeserializer(type.getComponentType(), annotations);
        return emit(() -> new ArrayDeserializer(type, itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createObjectDeserializer(Class<?> type) {
        return emit(() -> JSON.getClassDeserializer(type));
    }

    private Value<JsonDeserializer> createMapDeserializer(Type keyType, Type valueType,
            ReflectAnnotatedElement annotations) {
        Value<JsonDeserializer> keyDeserializer = createDeserializer(keyType, annotations);
        Value<JsonDeserializer> valueDeserializer = createDeserializer(valueType, annotations);
        return emit(() -> new MapDeserializer(keyDeserializer.get(), valueDeserializer.get()));
    }

    private Value<JsonDeserializer> createListDeserializer(Type itemType, ReflectAnnotatedElement annotations) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(itemType, annotations);
        return emit(() -> new ListDeserializer(itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createSetDeserializer(Type itemType, ReflectAnnotatedElement annotations) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(itemType, annotations);
        return emit(() -> new SetDeserializer(itemDeserializer.get()));
    }

    private Value<Object> convertDate(Value<Node> value, ReflectAnnotatedElement annotations) {
        DateFormatInformation formatInfo = DateFormatInformation.get(annotations);
        if (formatInfo.asString) {
            String localeName = formatInfo.locale;
            String pattern = formatInfo.pattern;
            Value<Locale> locale = formatInfo.locale != null
                    ? emit(() -> new Locale(localeName))
                    : emit(() -> Locale.getDefault());
            return emit(() -> {
                DateFormat format = new SimpleDateFormat(pattern, locale.get());
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                try {
                    return value != null ? format.parse(((StringNode) value.get()).getValue()) : null;
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            return emit(() -> {
                NumberNode node = (NumberNode) value.get();
                return node != null ? new Date(node.getIntValue()) : null;
            });
        }
    }

    private Method findMethod(ReflectMethod reference) {
        Class<?> owner = findClass(reference.getDeclaringClass().getName());
        Class<?>[] params = Arrays.stream(reference.getParameterTypes())
                .map(this::convertType)
                .toArray((IntFunction<Class<?>[]>) Class[]::new);
        while (owner != null) {
            try {
                return owner.getDeclaredMethod(reference.getName(), params);
            } catch (NoSuchMethodException e) {
                owner = owner.getSuperclass();
            }
        }
        return null;
    }

    private Constructor<?> findConstructor(ReflectMethod method) {
        Class<?> owner = findClass(method.getDeclaringClass().getName());
        Class<?>[] params = Arrays.stream(method.getParameterTypes())
                .map(this::convertType)
                .toArray((IntFunction<Class<?>[]>) Class[]::new);
        while (owner != null) {
            try {
                return owner.getDeclaredConstructor(params);
            } catch (NoSuchMethodException e) {
                owner = owner.getSuperclass();
            }
        }
        return null;
    }

    private Field findField(ReflectField field) {
        Class<?> owner = findClass(field.getDeclaringClass().getName());
        while (owner != null) {
            try {
                return owner.getDeclaredField(field.getName());
            } catch (NoSuchFieldException e) {
                owner = owner.getSuperclass();
            }
        }
        return null;
    }

    private Class<?> convertType(ReflectClass<?> type) {
        if (type.isPrimitive()) {
            switch (type.getName()) {
                case "boolean":
                    return boolean.class;
                case "byte":
                    return byte.class;
                case "short":
                    return short.class;
                case "char":
                    return char.class;
                case "int":
                    return int.class;
                case "long":
                    return long.class;
                case "float":
                    return float.class;
                case "double":
                    return double.class;
                case "void":
                    return void.class;
            }
        } else if (type.isArray()) {
            Class<?> itemCls = convertType(type.getComponentType());
            return Array.newInstance(itemCls, 0).getClass();
        }
        return findClass(type.getName());
    }

    private Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Can't find class " + name, e);
        }
    }

    static class ObjectWithTag {
        Value<String> tag;
        Value<ObjectNode> object;
        ObjectWithTag(Value<String> tag, Value<ObjectNode> object) {
            this.tag = tag;
            this.object = object;
        }
    }

    private Class<?> rawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return rawType(((ParameterizedType) type).getRawType());
        } else if (type instanceof GenericArrayType) {
            return Array.newInstance(rawType(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        } else if (type instanceof TypeVariable<?>) {
            return rawType(((TypeVariable<?>) type).getBounds()[0]);
        } else if (type instanceof WildcardType) {
            return rawType(((WildcardType) type).getUpperBounds()[0]);
        } else {
            throw new IllegalArgumentException("Don't know how to convert generic type: " + type);
        }
    }

    private Value<Object> defaultValue(ReflectClass<?> type) {
        if (type.isPrimitive()) {
            switch (type.getName()) {
                case "boolean":
                    return emit(() -> false);
                case "byte":
                    return emit(() -> (byte) 0);
                case "short":
                    return emit(() -> (short) 0);
                case "char":
                    return emit(() -> '\0');
                case "int":
                    return emit(() -> 0);
                case "long":
                    return emit(() -> 0L);
                case "float":
                    return emit(() -> 0F);
                case "double":
                    return emit(() -> 0.0);
            }
        }
        return emit(() -> null);
    }
}
