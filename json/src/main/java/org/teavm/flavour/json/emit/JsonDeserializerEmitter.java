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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;

/**
 *
 * @author Alexey Andreev
 */
public class JsonDeserializerEmitter {
    private Emitter<JsonDeserializer> em;
    private EmitterDiagnostics diagnostics;
    private ClassLoader classLoader;
    private ClassInformationProvider informationProvider;
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

    public JsonDeserializerEmitter(Emitter<JsonDeserializer> em) {
        this.em = em;
        this.classLoader = em.getContext().getClassLoader();
        this.diagnostics = em.getContext().getDiagnostics();
        informationProvider = ClassInformationProvider.getInstance(em.getContext());
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
            ReflectMethod ctor = em.getContext().findClass(serializerClass).getMethod("<init>");
            return em.emit(() -> (JsonDeserializer) ctor.construct());
        }

        if (em.getContext().findClass(Map.class).isAssignableFrom(cls)) {
            return em.emit(() -> new MapDeserializer(new ObjectDeserializer(), new ObjectDeserializer()));
        } else if (em.getContext().findClass(Collection.class).isAssignableFrom(cls)) {
            return em.emit(() -> new ListDeserializer(new ObjectDeserializer()));
        }

        return null;
    }

    private Value<? extends JsonDeserializer> emitArrayDeserializer(ReflectClass<?> cls) {
        if (cls.getComponentType().isPrimitive()) {
            String name = cls.getComponentType().getName();
            switch (name) {
                case "boolean":
                    return em.emit(() -> new BooleanArrayDeserializer());
                case "byte":
                    return em.emit(() -> new ByteArrayDeserializer());
                case "short":
                    return em.emit(() -> new ShortArrayDeserializer());
                case "char":
                    return em.emit(() -> new CharArrayDeserializer());
                case "int":
                    return em.emit(() -> new IntArrayDeserializer());
                case "long":
                    return em.emit(() -> new LongArrayDeserializer());
                case "float":
                    return em.emit(() -> new FloatArrayDeserializer());
                case "double":
                    return em.emit(() -> new DoubleArrayDeserializer());
            }
        }
        Value<? extends JsonDeserializer> itemDeserializer = getClassDeserializer(cls);
        return em.emit(() -> new ArrayDeserializer(cls.asJavaClass(), itemDeserializer.get()));
    }

    private Value<? extends JsonDeserializer> emitEnumDeserializer(ReflectClass<?> cls) {
        return em.proxy(NullableDeserializer.class, (bodyEm, instance, method, args) -> {
            Value<Node> node = bodyEm.emit(() -> (Node) args[1]);
            emitEnumDeserializer(bodyEm, cls, node);
        });
    }

    private Value<? extends JsonDeserializer> emitClassDeserializer(ReflectClass<?> cls) {
        return em.proxy(NullableDeserializer.class, (bodyEm, instance, method, args) -> {
            ClassInformation information = informationProvider.get(cls.getName());
            Value<JsonDeserializerContext> context = bodyEm.emit(() -> (JsonDeserializerContext) args[0]);
            Value<Node> node = bodyEm.emit(() -> (Node) args[1]);

            bodyEm = emitNodeTypeCheck(bodyEm, information, node, context);
            SubTypeResult subtype = emitSubTypes(bodyEm, information, node, context);
            bodyEm = subtype.em;
            Value<ObjectNode> contentNode = subtype.contentNode;
            Value<Object> target = emitConstructor(bodyEm, information, contentNode, context);
            emitIdRegistration(bodyEm, information, target, contentNode, context);
            emitProperties(bodyEm, information, target, contentNode, context);
            bodyEm.returnValue(target);
        });
    }

    private void emitEnumDeserializer(Emitter<Object> em, ReflectClass<?> cls, Value<Node> node) {
        Choice<Object> choice = em.choose(Object.class);
        String className = cls.getName();
        choice.option(() -> !node.get().isString()).emit(() -> {
            throw new IllegalArgumentException("Can't convert to " + className + ": "
                    + node.get().stringify());
        });
        em.returnValue(choice.getValue());
        em = choice.defaultOption();

        Value<String> text = em.emit(() -> ((StringNode) node.get()).getValue());
        choice = em.choose(Object.class);
        em.returnValue(choice.getValue());
        for (ReflectField field : cls.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                String fieldName = field.getName();
                choice.option(() -> text.get().equals(fieldName)).returnValue(() -> field.get(null));
            }
        }

        choice.defaultOption().emit(() -> {
            throw new IllegalArgumentException("Can't convert to " + className + ": "
                    + node.get().stringify());
        });
    }

    private void emitIdCheck(Emitter<Object> em, ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        String className = information.className;
        switch (information.idGenerator) {
            case INTEGER:
                emitIntegerIdCheck(em, information, node, context);
                break;
            case PROPERTY:
                emitPropertyIdCheck(em, information, node, context);
                break;
            case NONE:
                em.emit(() -> {
                    throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                            + " to an instance of " + className);
                });
                break;
            default:
                break;
        }
    }

    private void emitIntegerIdCheck(Emitter<Object> em, ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        String className = information.className;
        em.returnValue(() -> {
            if (node.get().isNumber()) {
                NumberNode number = (NumberNode) node.get();
                return context.get().get(number.getIntValue());
            } else {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            }
        });
    }

    private void emitPropertyIdCheck(Emitter<Object> em, ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        PropertyInformation property = information.properties.get(information.idProperty);
        String className = information.className;
        if (property == null) {
            em.emit(() -> {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            });
            return;
        }

        Type type = getPropertyGenericType(property);

        if (type != null) {
            Value<Object> converted = convert(em, node, context, type);
            em.returnValue(() -> context.get().get(converted.get()));
        } else {
            em.emit(() -> {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            });
        }
    }

    private Emitter<Object> emitNodeTypeCheck(Emitter<Object> em, ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        Choice<Object> choice = em.choose(Object.class);
        Emitter<Object> nextEm = choice.option(() -> node.get().isArray() || node.get().isObject());
        em.returnValue(choice.getValue());
        emitIdCheck(choice.defaultOption(), information, node, context);
        return nextEm;
    }

    private SubTypeResult emitSubTypes(Emitter<Object> em, ClassInformation information, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        if (information.inheritance.subTypes.isEmpty() || information.inheritance.value == InheritanceValue.NONE) {
            return new SubTypeResult(em, em.emit(() -> (ObjectNode) node.get()));
        }

        ObjectWithTag taggedObject = emitTypeNameExtractor(em, information, node);
        if (taggedObject == null) {
            return new SubTypeResult(em, em.emit(() -> (ObjectNode) node.get()));
        }

        Value<ObjectNode> contentNode = taggedObject.object;
        Value<String> tag = taggedObject.tag;

        Map<String, ClassInformation> subTypes = new HashMap<>();
        String rootTypeName = getTypeName(information, information);
        subTypes.put(rootTypeName, information);

        Choice<Object> choice = em.choose(Object.class);
        for (ClassInformation subType : information.inheritance.subTypes) {
            String typeName = getTypeName(information, subType);
            ReflectClass<?> subclass = em.getContext().findClass(subType.className);
            choice.option(() -> tag.get().equals(typeName)).returnValue(() -> {
                return JSON.getClassDeserializer(subclass.asJavaClass()).deserialize(context.get(), contentNode.get());
            });
        }
        choice.defaultOption().emit(() -> {
            throw new IllegalArgumentException("Invalid type tag: " + tag.get());
        });
        em.returnValue(choice.getValue());

        String defaultTypeName = getTypeName(information, information);
        return new SubTypeResult(choice.option(() -> tag.get().equals(defaultTypeName)), contentNode);
    }

    static class SubTypeResult {
        Emitter<Object> em;
        Value<ObjectNode> contentNode;
        SubTypeResult(Emitter<Object> em, Value<ObjectNode> contentNode) {
            this.em = em;
            this.contentNode = contentNode;
        }
    }

    private ObjectWithTag emitTypeNameExtractor(Emitter<Object> em, ClassInformation information, Value<Node> node) {
        switch (information.inheritance.key) {
            case PROPERTY:
                return emitPropertyTypeNameExtractor(em, information, node);
            case WRAPPER_ARRAY:
                return emitArrayTypeNameExtractor(em, node);
            case WRAPPER_OBJECT:
                return emitObjectTypeNameExtractor(em, node);
        }
        return null;
    }

    private ObjectWithTag emitPropertyTypeNameExtractor(Emitter<Object> em, ClassInformation information,
            Value<Node> node) {
        String propertyName = information.inheritance.propertyName;
        String defaultTypeName = getTypeName(information, information);
        Value<ObjectNode> contentNode = em.emit(() -> (ObjectNode) node.get());
        Value<String> typeName = em.emit(() -> contentNode.get().has(propertyName)
                ? ((StringNode) contentNode.get().get(propertyName)).getValue()
                : defaultTypeName);
        return new ObjectWithTag(typeName, contentNode);
    }

    private ObjectWithTag emitArrayTypeNameExtractor(Emitter<Object> em, Value<Node> node) {
        Value<ArrayNode> array = em.emit(() -> (ArrayNode) node.get());
        Value<String> tag = em.emit(() -> ((StringNode) array.get().get(0)).getValue());
        Value<ObjectNode> valueNode = em.emit(() -> (ObjectNode) array.get().get(1));
        return new ObjectWithTag(tag, valueNode);
    }

    private ObjectWithTag emitObjectTypeNameExtractor(Emitter<Object> em, Value<Node> node) {
        Value<ObjectNode> obj = em.emit(() -> (ObjectNode) node.get());
        Value<String> tag = em.emit(() -> obj.get().allKeys()[0]);
        Value<ObjectNode> valueNode = em.emit(() -> (ObjectNode) obj.get().get(tag.get()));
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

    private Value<Object> emitConstructor(Emitter<Object> em, ClassInformation information, Value<ObjectNode> node,
            Value<JsonDeserializerContext> context) {
        if (information.constructor == null) {
            diagnostics.error(null, "Neither non-argument constructor nor @JsonCreator were found in {{c0}}",
                    information.className);
            return em.emit(() -> null);
        }

        int paramCount = information.constructorArgs.size();
        Value<Object[]> args = em.emit(() -> new Object[paramCount]);
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
                Value<Node> valueNode = em.emit(() -> node.get().get(propertyName));
                paramValue = convert(em, valueNode, context, type);
            } else {
                paramValue = defaultValue(em, information.constructor.getParameterType(i));
            }
            int index = i;
            em.emit(() -> args.get()[index] = paramValue.get());
        }

        ReflectMethod ctor = information.constructor;
        return ctor.getName().equals("<init>")
                ? em.emit(() -> ctor.construct(args))
                : em.emit(() -> ctor.invoke(null, args));
    }

    private void emitIdRegistration(Emitter<Object> em, ClassInformation information, Value<Object> object,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        Choice<Void> choice = em.choose(Void.class);
        String idProperty = information.idProperty;
        Emitter<Void> bodyEm = choice.option(() -> node.get().has(idProperty));
        Value<Object> id;
        switch (information.idGenerator) {
            case INTEGER:
                id = emitIntegerIdRegistration(bodyEm, information, node);
                break;
            case PROPERTY:
                id = emitPropertyIdRegistration(bodyEm, information, node, context);
                break;
            default:
                id = null;
                break;
        }
        if (id != null) {
            bodyEm.emit(() -> context.get().register(id.get(), object.get()));
        }
    }

    private Value<Object> emitIntegerIdRegistration(Emitter<?> em, ClassInformation information,
            Value<ObjectNode> node) {
        String idProperty = information.idProperty;
        return em.emit(() -> JSON.deserializeInt(node.get().get(idProperty)));
    }

    private Value<Object> emitPropertyIdRegistration(Emitter<?> em, ClassInformation information,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        PropertyInformation property = information.properties.get(information.idProperty);
        if (property == null) {
            return null;
        }

        String idProperty = information.idProperty;
        Value<Node> id = em.emit(() -> node.get().get(idProperty));
        Type type = getPropertyGenericType(property);

        if (type == null) {
            return null;
        }
        return convert(em, id, context, type);
    }

    private void emitProperties(Emitter<?> em, ClassInformation information, Value<Object> target,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        for (PropertyInformation property : information.properties.values()) {
            if (property.ignored) {
                continue;
            }
            if (property.setter != null) {
                emitSetter(em, property, target, node, context);
            } else if (property.field != null) {
                emitField(em, property, target, node, context);
            }
        }
    }

    private void emitSetter(Emitter<?> em, PropertyInformation property, Value<Object> target,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        ReflectMethod method = property.setter;
        Method javaMethod = findMethod(method);
        Type type = javaMethod.getGenericParameterTypes()[0];

        String propertyName = property.outputName;
        Value<Node> jsonValue = em.emit(() -> node.get().get(propertyName));
        Value<Object> value = convert(em, jsonValue, context, type);
        em.emit(() -> method.invoke(target.get(), value.get()));
    }

    private void emitField(Emitter<?> em, PropertyInformation property, Value<Object> target,
            Value<ObjectNode> node, Value<JsonDeserializerContext> context) {
        ReflectField field = property.field;
        Field javaField = findField(field);
        Type type = javaField.getGenericType();

        String propertyName = property.outputName;
        Value<Node> jsonValue = em.emit(() -> node.get().get(propertyName));
        Value<Object> value = convert(em, jsonValue, context, type);
        em.emit(() -> field.set(target.get(), value.get()));
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

    private Value<Object> convert(Emitter<?> em, Value<Node> node, Value<JsonDeserializerContext> context, Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (cls.isPrimitive()) {
                return convertPrimitive(em, node, cls);
            }
        }
        return convertNullable(em, node, context, type);
    }

    private Value<Object> convertNullable(Emitter<?> em, Value<Node> node, Value<JsonDeserializerContext> context,
            Type type) {
        Value<JsonDeserializer> deserializer = createDeserializer(em, type);
        return em.emit(() -> deserializer.get().deserialize(context.get(), node.get()));
    }

    private Value<JsonDeserializer> createDeserializer(Emitter<?> em, Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            return cls.isArray()
                    ? createArrayDeserializer(em, cls)
                    : createObjectDeserializer(em, cls);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapDeserializer(em, typeArgs[0], typeArgs[1]);
            } else if (paramType.getRawType().equals(List.class)) {
                return createListDeserializer(em, typeArgs[0]);
            } else if (paramType.getRawType().equals(Set.class)) {
                return createSetDeserializer(em, typeArgs[0]);
            } else {
                return createDeserializer(em, paramType.getRawType());
            }
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(em, upperCls);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>) type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(em, upperCls);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            return createArrayDeserializer(em, array);
        } else {
            return createObjectDeserializer(em, Object.class);
        }
    }

    private Value<Object> convertPrimitive(Emitter<?> em, Value<Node> node, Class<?> type) {
        switch (type.getName()) {
            case "boolean":
                return em.emit(() -> JSON.deserializeBoolean(node.get()));
            case "byte":
                return em.emit(() -> JSON.deserializeByte(node.get()));
            case "short":
                return em.emit(() -> JSON.deserializeShort(node.get()));
            case "int":
                return em.emit(() -> JSON.deserializeInt(node.get()));
            case "long":
                return em.emit(() -> JSON.deserializeLong(node.get()));
            case "float":
                return em.emit(() -> JSON.deserializeFloat(node.get()));
            case "double":
                return em.emit(() -> JSON.deserializeDouble(node.get()));
            case "char":
                return em.emit(() -> JSON.deserializeChar(node.get()));
        }
        throw new AssertionError("Unknown primitive type: " + type);
    }

    private Value<JsonDeserializer> createArrayDeserializer(Emitter<?> em, GenericArrayType type) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, type.getGenericComponentType());
        Class<?> cls = rawType(type);
        return em.emit(() -> new ArrayDeserializer(cls, itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createArrayDeserializer(Emitter<?> em, Class<?> type) {
        if (type.getComponentType().isPrimitive()) {
            String name = type.getComponentType().getName();
            switch (name) {
                case "boolean":
                    return em.emit(() -> new BooleanArrayDeserializer());
                case "byte":
                    return em.emit(() -> new ByteArrayDeserializer());
                case "short":
                    return em.emit(() -> new ShortArrayDeserializer());
                case "char":
                    return em.emit(() -> new CharArrayDeserializer());
                case "int":
                    return em.emit(() -> new IntArrayDeserializer());
                case "long":
                    return em.emit(() -> new LongArrayDeserializer());
                case "float":
                    return em.emit(() -> new FloatArrayDeserializer());
                case "double":
                    return em.emit(() -> new DoubleArrayDeserializer());
            }
        }
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, type.getComponentType());
        return em.emit(() -> new ArrayDeserializer(type, itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createObjectDeserializer(Emitter<?> em, Class<?> type) {
        return em.emit(() -> JSON.getClassDeserializer(type));
    }

    private Value<JsonDeserializer> createMapDeserializer(Emitter<?> em, Type keyType, Type valueType) {
        Value<JsonDeserializer> keyDeserializer = createDeserializer(em, keyType);
        Value<JsonDeserializer> valueDeserializer = createDeserializer(em, valueType);
        return em.emit(() -> new MapDeserializer(keyDeserializer.get(), valueDeserializer.get()));
    }

    private Value<JsonDeserializer> createListDeserializer(Emitter<?> em, Type itemType) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, itemType);
        return em.emit(() -> new ListDeserializer(itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createSetDeserializer(Emitter<?> em, Type itemType) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, itemType);
        return em.emit(() -> new SetDeserializer(itemDeserializer.get()));
    }

    private Method findMethod(ReflectMethod reference) {
        Class<?> owner = findClass(reference.getDeclaringClass().getName());
        Class<?>[] params = Arrays.stream(reference.getParameterTypes())
                .map(this::convertType)
                .toArray(sz -> new Class<?>[sz]);
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
                .map(param -> convertType(param))
                .toArray(sz -> new Class<?>[sz]);
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

    private Value<Object> defaultValue(Emitter<?> em, ReflectClass<?> type) {
        if (type.isPrimitive()) {
            switch (type.getName()) {
                case "boolean":
                    return em.emit(() -> false);
                case "byte":
                    return em.emit(() -> (byte) 0);
                case "short":
                    return em.emit(() -> (short) 0);
                case "char":
                    return em.emit(() -> '\0');
                case "int":
                    return em.emit(() -> 0);
                case "long":
                    return em.emit(() -> 0L);
                case "float":
                    return em.emit(() -> 0F);
                case "double":
                    return em.emit(() -> 0.0);
            }
        }
        return em.emit(() -> null);
    }
}
