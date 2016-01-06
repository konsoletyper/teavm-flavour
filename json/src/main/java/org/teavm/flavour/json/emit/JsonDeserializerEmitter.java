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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.BooleanArrayDeserializer;
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
import org.teavm.flavour.json.deserializer.NullableDeserializer;
import org.teavm.flavour.json.deserializer.ObjectDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.deserializer.ShortDeserializer;
import org.teavm.flavour.json.deserializer.StringDeserializer;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.impl.reflect.ReflectContext;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldReader;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.PhiEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.StringChooseEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class JsonDeserializerEmitter {
    private Emitter<JsonDeserializer> em;
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
        informationProvider = ClassInformationProvider.getInstance(em.getContext());
    }

    public Value<? extends JsonDeserializer> getClassDeserializer(ReflectClass<?> cls) {
        Value<? extends JsonDeserializer> deserializer = tryGetPredefinedDeserializer(cls);
        if (deserializer == null) {
            deserializer = emitClassDeserializer(cls);
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

    private Value<? extends JsonDeserializer> emitClassDeserializer(ReflectClass<?> cls) {
        return em.proxy(NullableDeserializer.class, (bodyEm, instance, method, args) -> {
            ClassInformation information = null;
            if (!cls.isEnum()) {
                information = informationProvider.get(cls.getName());
                if (information == null) {
                    return;
                }
            }

            Value<JsonDeserializerContext> context = bodyEm.emit(() -> (JsonDeserializerContext) args[0]);
            Value<Node> node = bodyEm.emit(() -> (Node) args[1]);
            if (cls.isEnum()) {
                emitEnumDeserializer(bodyEm, cls, node);
            } else {
                BasicBlock nonObjectBlock = pe.prepareBlock();
                BasicBlock mainBlock = pe.prepareBlock();
                emitNodeTypeCheck(nonObjectBlock);
                emitSubTypes(information, mainBlock);
                pe.enter(mainBlock);
                emitConstructor(information);
                emitIdRegistration(information);
                emitProperties(information);
                targetVar.returnValue();
                pe.enter(nonObjectBlock);
                nodeVar = nodeVarBackup;
                emitIdCheck(bodyEm, information, node, context);
            }
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
            throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                    + " to an instance of " + className);
            return;
        }

        Type type = getPropertyGenericType(property);

        if (type != null) {
            Value<Object> converted = convert(em, node, type);
            em.returnValue(() -> context.get().get(converted.get()));
        } else {
            em.emit(() -> {
                throw new IllegalArgumentException("Can't deserialize node " + node.get().stringify()
                        + " to an instance of " + className);
            });
        }
    }

    private Value<Boolean> emitNodeTypeCheck(Emitter<Object> em, Value<Node> node,
            Value<JsonDeserializerContext> context) {
        Value<Object> byId = em.lazyFragment(Object.class, lem -> emitIdCheck(lem, information, node, context));
        return em.emit(() -> {
            if (node.get().isObject()) {

            }
            pe.when(nodeVar.invokeVirtual("isObject", boolean.class).isTrue()
                    .or(() -> nodeVar.invokeVirtual("isArray", boolean.class).isTrue()))
                    .thenDo(() -> {
                        nodeVar = nodeVar.cast(ValueType.object(ObjectNode.class.getName()));
                    })
                    .elseDo(() -> pe.jump(errorBlock));
        });

    }

    private void emitSubTypes(ClassInformation information, BasicBlock mainBlock) {
        if (information.inheritance.subTypes.isEmpty() || information.inheritance.value == InheritanceValue.NONE) {
            pe.jump(mainBlock);
            return;
        }

        ObjectWithTag taggedObject = emitTypeNameExtractor(information);
        if (taggedObject == null) {
            pe.jump(mainBlock);
            return;
        }
        nodeVar = taggedObject.object;

        Map<String, ClassInformation> subTypes = new HashMap<>();
        String rootTypeName = getTypeName(information, information);
        subTypes.put(rootTypeName, information);

        StringChooseEmitter choice = pe.stringChoice(taggedObject.tag);
        for (ClassInformation subType : information.inheritance.subTypes) {
            choice.option(getTypeName(information, subType), () -> createObjectDeserializer(subType.className)
                            .invokeVirtual("deserialize", Object.class, contextVar, nodeVar.cast(Node.class))
                            .returnValue());
        }
        choice.option(getTypeName(information, information), () -> pe.jump(mainBlock));
        choice.otherwise(() -> {
            ValueEmitter errorVar = pe.string()
                    .append("Invalid type tag: ")
                    .append(taggedObject.tag)
                    .build();
            pe.construct(IllegalArgumentException.class, errorVar).raise();
        });
    }

    private ObjectWithTag emitTypeNameExtractor(ClassInformation information) {
        switch (information.inheritance.key) {
            case PROPERTY:
                return emitPropertyTypeNameExtractor(information);
            case WRAPPER_ARRAY:
                return emitArrayTypeNameExtractor();
            case WRAPPER_OBJECT:
                return emitObjectTypeNameExtractor();
        }
        return null;
    }

    private ObjectWithTag emitPropertyTypeNameExtractor(ClassInformation information) {
        BasicBlock exit = pe.prepareBlock();

        ValueEmitter node = nodeVar.cast(ValueType.parse(ObjectNode.class));
        PhiEmitter result = pe.phi(String.class, exit);
        pe.when(node.invokeVirtual("has", boolean.class, pe.constant(information.inheritance.propertyName)).isTrue())
                .thenDo(() -> getJsonProperty(node, information.inheritance.propertyName)
                        .cast(StringNode.class)
                        .invokeVirtual("getValue", String.class)
                        .propagateTo(result).jump(exit))
                .elseDo(() -> pe
                        .constant(getTypeName(information, information))
                        .propagateTo(result)
                        .jump(exit));
        pe.enter(exit);
        return new ObjectWithTag(result.getValue(), nodeVar);
    }

    private ObjectWithTag emitArrayTypeNameExtractor() {
        ValueEmitter node = nodeVar.cast(ValueType.parse(ArrayNode.class));
        ValueEmitter tag = node.invokeVirtual("get", Node.class, pe.constant(0))
                .cast(StringNode.class)
                .invokeVirtual("getValue", String.class);
        ValueEmitter object = node.invokeVirtual("get", Node.class, pe.constant(1));
        return new ObjectWithTag(tag, object);
    }

    private ObjectWithTag emitObjectTypeNameExtractor() {
        ValueEmitter node = nodeVar.cast(ObjectNode.class);
        ValueEmitter tag = node.invokeVirtual("allKeys", String[].class).getElement(0);
        ValueEmitter object = node.invokeVirtual("get", Node.class, tag);
        return new ObjectWithTag(tag, object);
    }

    private String getTypeName(ClassInformation baseType, ClassInformation type) {
        switch (baseType.inheritance.value) {
            case CLASS:
                return type.className;
            case MINIMAL_CLASS:
                return ClassInformationProvider.getUnqualifiedName(type.className);
            case NAME:
                return type.typeName != null ? type.typeName
                        : ClassInformationProvider.getUnqualifiedName(type.className);
            case NONE:
                break;
        }
        return "";
    }

    private void emitConstructor(ClassInformation information) {
        if (information.constructor == null) {
            agent.getDiagnostics().error(null, "Neither non-argument constructor nor @JonCreator were found in {{c0}}",
                    information.className);
            targetVar = pe.construct(information.className);
            return;
        }

        ValueEmitter[] args = new ValueEmitter[information.constructorArgs.size()];
        Type[] genericTypes;
        if (information.constructor.getName().equals("<init>")) {
            Constructor<?> javaCtor = findConstructor(new MethodReference(information.className,
                    information.constructor));
            genericTypes = javaCtor.getGenericParameterTypes();
        } else {
            Method javaMethod = findMethod(new MethodReference(information.className, information.constructor));
            genericTypes = javaMethod.getGenericParameterTypes();
        }
        for (int i = 0; i < args.length; ++i) {
            PropertyInformation property = information.constructorArgs.get(i);
            if (property != null) {
                Type type = genericTypes[i];
                ValueEmitter value = getJsonProperty(nodeVar, property.outputName);
                value = convert(value, type);
                args[i] = value.cast(information.constructor.parameterType(i));
            } else {
                args[i] = pe.defaultValue(information.constructor.parameterType(i));
            }
        }
        if (information.constructor.getName().equals("<init>")) {
            targetVar = pe.construct(information.className, args);
        } else {
            targetVar = pe.invoke(information.className, information.constructor.getName(),
                    ValueType.object(information.className), args);
        }
    }

    private void emitIdRegistration(ClassInformation information) {
        BasicBlock skip = pe.prepareBlock();

        ValueEmitter id;
        switch (information.idGenerator) {
            case INTEGER:
                id = emitIntegerIdRegistration(information, skip);
                break;
            case PROPERTY:
                id = emitPropertyIdRegistration(information, skip);
                break;
            default:
                id = null;
                break;
        }
        if (id != null) {
            contextVar.invokeVirtual("register", id.cast(Object.class), targetVar.cast(Object.class));
        }
        pe.jump(skip);
        pe.enter(skip);
    }

    private ValueEmitter emitIntegerIdRegistration(ClassInformation information, BasicBlock skip) {
        checkIdPropertyExistence(information, skip);
        ValueEmitter id = getJsonProperty(nodeVar, information.idProperty);
        id = pe.invoke(JSON.class, "deserializeInt", int.class, id);
        return pe.invoke(Integer.class, "valueOf",  Integer.class, id);
    }

    private ValueEmitter emitPropertyIdRegistration(ClassInformation information, BasicBlock skip) {
        PropertyInformation property = information.properties.get(information.idProperty);
        if (property == null) {
            return null;
        }

        checkIdPropertyExistence(information, skip);
        ValueEmitter id = getJsonProperty(nodeVar, information.idProperty);
        Type type = getPropertyGenericType(property);

        if (type == null) {
            return null;
        }
        return convert(id, type);
    }

    private void checkIdPropertyExistence(ClassInformation information, BasicBlock skip) {
        pe.when(nodeVar.invokeVirtual("has", boolean.class, pe.constant(information.idProperty)).isFalse())
                .thenDo(() -> pe.jump(skip));
    }

    private void emitProperties(ClassInformation information) {
        for (PropertyInformation property : information.properties.values()) {
            if (property.ignored) {
                continue;
            }
            if (property.setter != null) {
                emitSetter(property);
            } else if (property.field != null) {
                emitField(property);
            }
        }
    }

    private void emitSetter(PropertyInformation property) {
        MethodReference method = new MethodReference(property.className, property.setter);
        Method javaMethod = findMethod(method);
        Type type = javaMethod.getGenericParameterTypes()[0];

        ValueEmitter value = getJsonProperty(nodeVar, property.outputName);
        value = convert(value, type);
        targetVar.invokeVirtual(property.setter.getName(), value.cast(property.setter.parameterType(0)));
    }

    private void emitField(PropertyInformation property) {
        FieldReference field = new FieldReference(property.className, property.field);
        Field javaField = findField(field);
        Type type = javaField.getGenericType();
        ValueType fieldType = agent.linkField(field, null).getField().getType();

        ValueEmitter value = getJsonProperty(nodeVar, property.outputName);
        value = convert(value, type);
        targetVar.setField(field.getFieldName(), value.cast(fieldType));
    }

    private Type getPropertyGenericType(PropertyInformation property) {
        Type type = null;
        if (property.getter != null) {
            Method getter = findMethod(new MethodReference(property.className, property.getter));
            if (getter != null) {
                type = getter.getGenericReturnType();
            }
        }
        if (type == null && property.field != null) {
            Field field = findField(new FieldReference(property.className, property.field));
            if (field != null) {
                type = field.getGenericType();
            }
        }
        return type;
    }

    private ValueEmitter convert(ValueEmitter node, Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (cls.isPrimitive()) {
                return convertPrimitive(node, cls);
            }
        }
        return convertNullable(node, type);
    }

    private ValueEmitter convertNullable(ValueEmitter node, Type type) {
        ValueEmitter deserializer = createDeserializer(type);
        return deserializer.invokeVirtual("deserialize", Object.class, contextVar, node.cast(Node.class))
                .cast(rawType(type));
    }

    private ValueEmitter createDeserializer(Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>) type;
            if (cls.isArray()) {
                return createArrayDeserializer(cls);
            } else {
                return createObjectDeserializer(cls);
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapDeserializer(typeArgs[0], typeArgs[1]);
            } else if (paramType.getRawType().equals(List.class)) {
                return createListDeserializer(typeArgs[0]);
            } else if (paramType.getRawType().equals(Set.class)) {
                return createSetDeserializer(typeArgs[0]);
            } else {
                return createDeserializer(paramType.getRawType());
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
            return createArrayDeserializer(array);
        } else {
            return createObjectDeserializer(Object.class);
        }
    }

    private ValueEmitter convertPrimitive(ValueEmitter node, Class<?> type) {
        switch (type.getName()) {
            case "boolean":
                return pe.invoke(JSON.class, "deserializeBoolean", boolean.class, node);
            case "byte":
                return pe.invoke(JSON.class, "deserializeByte", byte.class, node);
            case "short":
                return pe.invoke(JSON.class, "deserializeShort", short.class, node);
            case "int":
                return pe.invoke(JSON.class, "deserializeInt", int.class, node);
            case "long":
                return pe.invoke(JSON.class, "deserializeLong", long.class, node);
            case "float":
                return pe.invoke(JSON.class, "deserializeFloat", float.class, node);
            case "double":
                return pe.invoke(JSON.class, "deserializeDouble", double.class, node);
            case "char":
                return pe.invoke(JSON.class, "deserializeChar", char.class, node);
        }
        throw new AssertionError("Unknown primitive type: " + type);
    }

    private ValueEmitter createArrayDeserializer(GenericArrayType type) {
        ValueEmitter itemDeserializer = createDeserializer(type.getGenericComponentType())
                .cast(JsonDeserializer.class);
        return pe.construct(ArrayDeserializer.class, pe.constant(Object.class), itemDeserializer);
    }

    private ValueEmitter createArrayDeserializer(Class<?> type) {
        if (type.getComponentType().isPrimitive()) {
            String name = type.getComponentType().getName();
            String deserializerClass = BooleanArrayDeserializer.class.getPackage().getName() + "."
                    + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "ArrayDeserializer";
            return pe.construct(deserializerClass);
        } else {
            ValueEmitter itemDeserializer = createDeserializer(type.getComponentType()).cast(JsonDeserializer.class);
            return pe.construct(ArrayDeserializer.class, pe.constant(type), itemDeserializer);
        }
    }

    private ValueEmitter createObjectDeserializer(Class<?> type) {
        String deserializerName;
        if (predefinedDeserializers.containsKey(type.getName())) {
            deserializerName = predefinedDeserializers.get(type.getName());
        } else {
            deserializableClassesNode.propagate(agent.getType(type.getName()));
            deserializerName = type.getName() + "$$__deserializer__$$";
        }
        return pe.construct(deserializerName);
    }

    private ValueEmitter createObjectDeserializer(String type) {
        String deserializerName;
        if (predefinedDeserializers.containsKey(type)) {
            deserializerName = predefinedDeserializers.get(type);
        } else {
            deserializableClassesNode.propagate(agent.getType(type));
            deserializerName = type + "$$__deserializer__$$";
        }
        return pe.construct(deserializerName);
    }

    private ValueEmitter createMapDeserializer(Type keyType, Type valueType) {
        ValueEmitter keyDeserializer = createDeserializer(keyType).cast(JsonDeserializer.class);
        ValueEmitter valueDeserializer = createDeserializer(valueType).cast(JsonDeserializer.class);
        return pe.construct(MapDeserializer.class, keyDeserializer, valueDeserializer);
    }

    private ValueEmitter createListDeserializer(Type itemType) {
        ValueEmitter itemDeserializer = createDeserializer(itemType).cast(JsonDeserializer.class);
        return pe.construct(ListDeserializer.class, itemDeserializer);
    }

    private ValueEmitter createSetDeserializer(Type itemType) {
        ValueEmitter itemDeserializer = createDeserializer(itemType).cast(JsonDeserializer.class);
        return pe.construct(SetDeserializer.class, itemDeserializer);
    }

    private Method findMethod(MethodReference reference) {
        Class<?> owner = findClass(reference.getClassName());
        Class<?>[] params = new Class<?>[reference.parameterCount()];
        for (int i = 0; i < params.length; ++i) {
            params[i] = convertType(reference.parameterType(i));
        }
        while (owner != null) {
            try {
                return owner.getDeclaredMethod(reference.getName(), params);
            } catch (NoSuchMethodException e) {
                owner = owner.getSuperclass();
            }
        }
        return null;
    }

    private Constructor<?> findConstructor(MethodReference reference) {
        Class<?> owner = findClass(reference.getClassName());
        Class<?>[] params = new Class<?>[reference.parameterCount()];
        for (int i = 0; i < params.length; ++i) {
            params[i] = convertType(reference.parameterType(i));
        }
        while (owner != null) {
            try {
                return owner.getDeclaredConstructor(params);
            } catch (NoSuchMethodException e) {
                owner = owner.getSuperclass();
            }
        }
        return null;
    }

    private Field findField(FieldReference reference) {
        Class<?> owner = findClass(reference.getClassName());
        while (owner != null) {
            try {
                return owner.getDeclaredField(reference.getFieldName());
            } catch (NoSuchFieldException e) {
                owner = owner.getSuperclass();
            }
        }
        return null;
    }

    private Class<?> convertType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return boolean.class;
                case BYTE:
                    return byte.class;
                case SHORT:
                    return short.class;
                case CHARACTER:
                    return char.class;
                case INTEGER:
                    return int.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                case DOUBLE:
                    return double.class;
            }
        } else if (type instanceof ValueType.Array) {
            Class<?> itemCls = convertType(((ValueType.Array) type).getItemType());
            return Array.newInstance(itemCls, 0).getClass();
        } else if (type instanceof ValueType.Void) {
            return void.class;
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            return findClass(className);
        }
        throw new AssertionError("Can't convert type: " + type);
    }

    private Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Can't find class " + name, e);
        }
    }

    private ValueEmitter getJsonProperty(ValueEmitter object, String property) {
        return object.cast(ObjectNode.class).invokeVirtual("get", Node.class, pe.constant(property));
    }

    static class ObjectWithTag {
        ValueEmitter tag;
        ValueEmitter object;

        public ObjectWithTag(ValueEmitter tag, ValueEmitter object) {
            this.tag = tag;
            this.object = object;
        }
    }

    private ValueType rawType(Type type) {
        if (type instanceof Class<?>) {
            return ValueType.parse((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            return rawType(((ParameterizedType) type).getRawType());
        } else if (type instanceof GenericArrayType) {
            return ValueType.arrayOf(rawType(((GenericArrayType) type).getGenericComponentType()));
        } else if (type instanceof TypeVariable<?>) {
            return rawType(((TypeVariable<?>) type).getBounds()[0]);
        } else if (type instanceof WildcardType) {
            return rawType(((WildcardType) type).getUpperBounds()[0]);
        } else {
            throw new IllegalArgumentException("Don't know how to convert generic type: " + type);
        }
    }
}
