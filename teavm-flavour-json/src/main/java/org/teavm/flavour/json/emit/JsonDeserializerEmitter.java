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
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
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
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.deserializer.ShortDeserializer;
import org.teavm.flavour.json.deserializer.StringDeserializer;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
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
    private DependencyAgent agent;
    private ClassReaderSource classSource;
    private ClassReader deserializedClass;
    private ClassLoader classLoader;
    private ValueEmitter contextVar;
    private ValueEmitter nodeVar;
    private ValueEmitter targetVar;
    private ProgramEmitter pe;
    private ClassInformationProvider informationProvider;
    private Set<String> deserializableClasses = new HashSet<>();
    private DependencyNode deserializableClassesNode;
    private static Map<String, String> predefinedDeserializers = new HashMap<>();

    static {
        predefinedDeserializers.put(Object.class.getName(), BooleanDeserializer.class.getName());
        predefinedDeserializers.put(Number.class.getName(), BooleanDeserializer.class.getName());
        predefinedDeserializers.put(Boolean.class.getName(), BooleanDeserializer.class.getName());
        predefinedDeserializers.put(Byte.class.getName(), ByteDeserializer.class.getName());
        predefinedDeserializers.put(Short.class.getName(), ShortDeserializer.class.getName());
        predefinedDeserializers.put(Character.class.getName(), CharacterDeserializer.class.getName());
        predefinedDeserializers.put(Integer.class.getName(), IntegerDeserializer.class.getName());
        predefinedDeserializers.put(Long.class.getName(), LongDeserializer.class.getName());
        predefinedDeserializers.put(Float.class.getName(), FloatDeserializer.class.getName());
        predefinedDeserializers.put(Double.class.getName(), DoubleDeserializer.class.getName());
        predefinedDeserializers.put(String.class.getName(), StringDeserializer.class.getName());
    }

    public JsonDeserializerEmitter(DependencyAgent agent, DependencyNode deserializableClassesNode) {
        this.agent = agent;
        this.classSource = agent.getClassSource();
        this.classLoader = agent.getClassLoader();
        informationProvider = new ClassInformationProvider(classSource, agent.getDiagnostics());
        this.deserializableClassesNode = deserializableClassesNode;
    }

    public String addClassDeserializer(String serializedClassName) {
        ClassReader cls = classSource.get(serializedClassName);
        if (cls == null) {
            return null;
        }
        if (deserializableClasses.add(serializedClassName)) {
            if (tryGetPredefinedDeserializer(serializedClassName) == null) {
                emitClassDeserializer(serializedClassName);
            }
        }
        return getClassDeserializer(serializedClassName);
    }

    public String getClassDeserializer(String className) {
        String serializer = tryGetPredefinedDeserializer(className);
        if (serializer == null) {
            serializer = deserializableClasses.contains(className) ? className + "$$__deserializer__$$" : null;
        }
        return serializer;
    }

    private String tryGetPredefinedDeserializer(String className) {
        String serializer = predefinedDeserializers.get(className);
        if (serializer == null) {
            if (isSuperType(Map.class.getName(), className)) {
                serializer = MapDeserializer.class.getName();
            } else if (isSuperType(Collection.class.getName(), className)) {
                serializer = ListDeserializer.class.getName();
            }
        }
        return serializer;
    }

    private boolean isSuperType(String superType, String subType) {
        if (superType.equals(subType)) {
            return true;
        }

        ClassReader cls = classSource.get(subType);
        if (cls == null) {
            return false;
        }

        if (cls.getParent() != null && !cls.getParent().equals(cls.getName())) {
            if (isSuperType(superType, cls.getParent())) {
                return true;
            }
        }

        for (String iface : cls.getInterfaces()) {
            if (isSuperType(superType, iface)) {
                return true;
            }
        }

        return false;
    }

    private void emitClassDeserializer(String serializedClassName) {
        ClassInformation information = informationProvider.get(serializedClassName);
        if (information == null) {
            return;
        }

        ClassHolder cls = new ClassHolder(serializedClassName + "$$__deserializer__$$");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(NullableDeserializer.class.getName());

        emitDeserializationMethod(information, cls);
        emitConstructor(cls);
        agent.submitClass(cls);
    }

    private void emitConstructor(ClassHolder cls) {
        MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);

        ProgramEmitter.create(ctor, classSource)
                .newVar(cls)
                .invokeSpecial(JsonDeserializer.class, "<init>")
                .exit();

        cls.addMethod(ctor);
    }

    private void emitDeserializationMethod(ClassInformation information, ClassHolder cls) {
        deserializedClass = classSource.get(information.className);
        if (deserializedClass == null) {
            return;
        }

        try {
            MethodHolder method = new MethodHolder("deserializeNonNull",
                    ValueType.parse(JsonDeserializerContext.class),
                    ValueType.parse(Node.class), ValueType.parse(Object.class));
            method.setLevel(AccessLevel.PUBLIC);

            pe = ProgramEmitter.create(method, classSource);
            contextVar = pe.var(1, JsonDeserializerContext.class);
            nodeVar = pe.var(2, Node.class);
            ValueEmitter nodeVarBackup = nodeVar;

            if (isSuperType(Enum.class.getName(), information.className)) {
                emitEnumDeserializer(information);
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
                emitIdCheck(information);
            }
            cls.addMethod(method);
        } finally {
            pe = null;
            nodeVar = null;
            targetVar = null;
            contextVar = null;
            deserializedClass = null;
        }
    }

    private void emitEnumDeserializer(ClassInformation information) {
        ClassReader cls = classSource.get(information.className);
        Set<String> valueSet = new HashSet<>();
        for (FieldReader field : cls.getFields()) {
            if (!field.hasModifier(ElementModifier.STATIC) || !field.getType().isObject(information.className)) {
                continue;
            }
            valueSet.add(field.getName());
        }

        BasicBlock invalidValueBlock = pe.prepareBlock();
        pe.when(nodeVar.invokeVirtual("isString", boolean.class).isFalse())
                .thenDo(() -> pe.jump(invalidValueBlock));

        ValueEmitter textVar = nodeVar.cast(StringNode.class).invokeVirtual("getValue", String.class);
        StringChooseEmitter choise = pe.stringChoise(textVar);
        for (String enumValue : valueSet) {
            choise.option(enumValue, () -> pe
                    .initClass(information.className)
                    .getField(information.className, enumValue, ValueType.object(information.className))
                    .returnValue());
        }
        choise.otherwise(() -> pe.jump(invalidValueBlock));

        pe.jump(invalidValueBlock);
        ValueEmitter error = pe.string()
                .append("Can't convert to " + information.className + ": ")
                .append(nodeVar.invokeVirtual("stringify", String.class))
                .build();
        pe.construct(IllegalArgumentException.class, error).raise();
    }

    private void emitIdCheck(ClassInformation information) {
        switch (information.idGenerator) {
            case INTEGER:
                emitIntegerIdCheck(information);
                break;
            case PROPERTY:
                emitPropertyIdCheck(information);
                break;
            case NONE:
                emitNoId(information);
                break;
            default:
                break;
        }
    }

    private void emitIntegerIdCheck(ClassInformation information) {
        pe.when(nodeVar.invokeVirtual("isNumber", boolean.class).isTrue())
                .thenDo(() -> {
                    ValueEmitter id = nodeVar.cast(NumberNode.class).invokeVirtual("getIntValue", int.class).box();
                    contextVar.invokeVirtual("get", Object.class, id.cast(Object.class)).returnValue();
                })
                .elseDo(() -> emitNoId(information));
    }

    private void emitPropertyIdCheck(ClassInformation information) {
        PropertyInformation property = information.properties.get(information.idProperty);
        if (property == null) {
            emitNoId(information);
            return;
        }

        Type type = getPropertyGenericType(property);
        if (type != null) {
            ValueEmitter id = convert(nodeVar, type);
            contextVar.invokeVirtual("get", Object.class, id.cast(Object.class)).returnValue();
        } else {
            emitNoId(information);
        }
    }

    private void emitNoId(ClassInformation information) {
        pe.construct(IllegalArgumentException.class, pe.constant(
                "Can't deserialize node to an instance of " + information.className))
                .raise();
    }

    private void emitNodeTypeCheck(BasicBlock errorBlock) {
        pe.when(nodeVar.invokeVirtual("isObject", boolean.class).isTrue()
                .or(() -> nodeVar.invokeVirtual("isArray", boolean.class).isTrue()))
                .thenDo(() -> {
                    nodeVar = nodeVar.cast(ValueType.object(ObjectNode.class.getName()));
                })
                .elseDo(() -> pe.jump(errorBlock));
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
        for (ClassInformation subType : information.inheritance.subTypes) {
            String typeName = getTypeName(information, subType);
            subTypes.put(typeName, subType);
        }
        String rootTypeName = getTypeName(information, information);
        subTypes.put(rootTypeName, information);

        StringChooseEmitter choice = pe.stringChoise(taggedObject.tag);
        for (ClassInformation classInfo : subTypes.values()) {
            choice.option(getTypeName(classInfo, information), () -> createObjectDeserializer(classInfo.className)
                            .invokeVirtual("deserialize", Object.class, contextVar, nodeVar.cast(Node.class))
                            .returnValue());
        }
        choice.otherwise(() -> {
            ValueEmitter errorVar = pe.string()
                    .append("Invalid type tag: ")
                    .append(nodeVar.invokeVirtual("stringify", String.class))
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
                return type.typeName != null ? type.typeName :
                        ClassInformationProvider.getUnqualifiedName(type.className);
            case NONE:
                break;
        }
        return "";
    }

    private void emitConstructor(ClassInformation information) {
        targetVar = pe.construct(information.className);
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
            contextVar.invokeVirtual("register", Object.class, id.cast(Object.class), targetVar.cast(Object.class));
        }
        pe.jump(skip);
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
            } else if (property.fieldName != null) {
                emitField(property);
            }
        }
    }

    private void emitSetter(PropertyInformation property) {
        MethodReference method = new MethodReference(property.className, property.setter);
        Method javaMethod = findMethod(method);
        Type type = javaMethod.getGenericParameterTypes()[0];
        agent.linkMethod(method, null);

        ValueEmitter value = getJsonProperty(nodeVar, property.outputName);
        value = convert(value, type);
        targetVar.invokeVirtual(method, value);
    }

    private void emitField(PropertyInformation property) {
        FieldReference field = new FieldReference(property.className, property.fieldName);
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
        if (type == null && property.fieldName != null) {
            Field field = findField(new FieldReference(property.className, property.fieldName));
            if (field != null) {
                type = field.getGenericType();
            }
        }
        return type;
    }

    private ValueEmitter convert(ValueEmitter node, Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>)type;
            if (cls.isPrimitive()) {
                return convertPrimitive(node, cls);
            }
        }
        return convertNullable(node, type);
    }

    private ValueEmitter convertNullable(ValueEmitter node, Type type) {
        ValueEmitter deserializer = createDeserializer(type);
        return deserializer.invokeVirtual("deserialize", Object.class, contextVar, node.cast(Node.class));
    }

    private ValueEmitter createDeserializer(Type type) {
        if (type instanceof Class<?>) {
            Class<?> cls = (Class<?>)type;
            if (cls.isArray()) {
                return createArrayDeserializer(cls);
            } else {
                return createObjectDeserializer(cls);
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType)type;
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
            WildcardType wildcard = (WildcardType)type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>)upperBound;
            }
            return createObjectDeserializer(upperCls);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>)type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>)upperBound;
            }
            return createObjectDeserializer(upperCls);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType)type;
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
            String deserializerClass = BooleanArrayDeserializer.class.getPackage().getName() + "." +
                    Character.toUpperCase(name.charAt(0)) + name.substring(1) + "ArrayDeserializer";
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
            switch (((ValueType.Primitive)type).getKind()) {
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
            Class<?> itemCls = convertType(((ValueType.Array)type).getItemType());
            return Array.newInstance(itemCls, 0).getClass();
        } else if (type instanceof ValueType.Void) {
            return void.class;
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object)type).getClassName();
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
        return object.invokeVirtual("get", Node.class, pe.constant(property));
    }

    static class ObjectWithTag {
        ValueEmitter tag;
        ValueEmitter object;

        public ObjectWithTag(ValueEmitter tag, ValueEmitter object) {
            this.tag = tag;
            this.object = object;
        }
    }
}
