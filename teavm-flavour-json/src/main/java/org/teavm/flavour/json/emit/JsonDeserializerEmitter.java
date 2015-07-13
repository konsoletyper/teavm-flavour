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
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ForkEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.RaiseInstruction;

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

    public JsonDeserializerEmitter(DependencyAgent agent) {
        this.agent = agent;
        this.classSource = agent.getClassSource();
        this.classLoader = agent.getClassLoader();
        informationProvider = new ClassInformationProvider(classSource, agent.getDiagnostics());
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
            serializer = deserializableClasses.contains(className) ?
                    className + "$$__deserializer__$$" : null;
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

        ProgramEmitter pe = ProgramEmitter.create(ctor);
        ValueEmitter thisVar = pe.newVar();
        thisVar.invokeSpecial(new MethodReference(JsonDeserializer.class, "<init>", void.class));
        pe.exit();

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

            pe = ProgramEmitter.create(method);
            pe.newVar(); // skip this variable
            contextVar = pe.newVar();
            nodeVar = pe.newVar();

            BasicBlock nonObjectBlock = pe.createBlock();
            emitNodeTypeCheck(nonObjectBlock);

            emitIdCheck(information);
            emitConstructor(information);
            emitIdRegistration(information);

            targetVar.returnValue();
            cls.addMethod(method);
        } finally {
            pe = null;
            nodeVar = null;
            targetVar = null;
            contextVar = null;
            deserializedClass = null;
        }
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
        BasicBlock errorBlock = pe.getProgram().createBasicBlock();
        BasicBlock okBlock = pe.getProgram().createBasicBlock();

        ForkEmitter fork = nodeVar.invokeVirtual(new MethodReference(Node.class, "isInt()", boolean.class))
                .fork(BranchingCondition.NOT_EQUAL);
        fork.setThen(okBlock);
        fork.setElse(errorBlock);

        pe.setBlock(okBlock);
        ValueEmitter id = nodeVar.cast(ValueType.parse(NumberNode.class))
                .invokeVirtual(new MethodReference(NumberNode.class, "getIntValue", int.class));
        id = pe.invoke(new MethodReference(Integer.class, "valueOf", int.class), id);
        contextVar.invokeVirtual(new MethodReference(JsonDeserializerContext.class, "get",
                Object.class, Object.class), id)
                .returnValue();

        pe.setBlock(errorBlock);
        emitNoId(information);
    }

    private void emitPropertyIdCheck(ClassInformation information) {
        PropertyInformation property = information.properties.get(information.idProperty);
        if (property == null) {
            emitNoId(information);
            return;
        }

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

        if (type != null) {
            ValueEmitter id = convert(nodeVar, type);
            contextVar.invokeVirtual(new MethodReference(JsonDeserializerContext.class, "get", Object.class,
                    Object.class), id).returnValue();
        } else {
            emitNoId(information);
        }
    }

    private void emitNoId(ClassInformation information) {
        ValueEmitter ex = pe.construct(new MethodReference(IllegalArgumentException.class, "<init>", String.class),
                pe.constant("Can't deserialize node to an instance of " + information.className));
        RaiseInstruction raise = new RaiseInstruction();
        raise.setException(ex.getVariable());
        pe.addInstruction(raise);
    }

    private void emitNodeTypeCheck(BasicBlock errorBlock) {
        BasicBlock okBlock = pe.getProgram().createBasicBlock();

        ForkEmitter okFork = nodeVar.invokeVirtual(new MethodReference(Node.class, "isObject()", boolean.class))
                .fork(BranchingCondition.NOT_EQUAL);

        pe.createBlock();
        okFork.or(pe.getBlock(), nodeVar.invokeVirtual(new MethodReference(Node.class, "isArray()", boolean.class))
                .fork(BranchingCondition.NOT_EQUAL));

        okFork.setThen(okBlock);
        okFork.setElse(errorBlock);

        pe.setBlock(okBlock);
        nodeVar = nodeVar.cast(ValueType.object(ObjectNode.class.getName()));
    }

    private void emitConstructor(ClassInformation information) {
        targetVar = pe.construct(new MethodReference(information.className, "<init>", ValueType.VOID));
    }

    private void emitIdRegistration(ClassInformation information) {
        ValueEmitter id;
        switch (information.idGenerator) {
            case INTEGER:
                id = emitIntegerIdRegistration(information);
                break;
            case PROPERTY:
                id = emitPropertyIdRegistration(information);
                break;
            default:
                id = null;
                break;
        }
        if (id != null) {
            contextVar.invokeVirtual(new MethodReference(JsonDeserializerContext.class, "register", Object.class,
                    Object.class, void.class), id, targetVar);
        }
    }

    private ValueEmitter emitIntegerIdRegistration(ClassInformation information) {
        ValueEmitter id = nodeVar.invokeVirtual(new MethodReference(ObjectNode.class, "get",
                String.class, Node.class), pe.constant(information.idProperty));
        id = pe.invoke(new MethodReference(JSON.class, "deserializeInt", Node.class, int.class), id);
        id = pe.invoke(new MethodReference(Integer.class, "valueOf", int.class, Integer.class), id);
        return id;
    }

    private ValueEmitter emitPropertyIdRegistration(ClassInformation information) {
        return null;
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
        return deserializer.invokeVirtual(new MethodReference(JsonDeserializer.class, "deserializer",
                JsonDeserializerContext.class, Node.class, Object.class), contextVar, node);
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
            if (paramType.getRawType().equals(Set.class)) {
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
                return pe.invoke(new MethodReference(JSON.class, "deserializeBoolean",
                        Node.class, boolean.class), node);
            case "byte":
                return pe.invoke(new MethodReference(JSON.class, "deserializeByte", Node.class, byte.class), node);
            case "short":
                return pe.invoke(new MethodReference(JSON.class, "deserializeShort", Node.class, short.class), node);
            case "int":
                return pe.invoke(new MethodReference(JSON.class, "deserializeInt", Node.class, int.class), node);
            case "long":
                return pe.invoke(new MethodReference(JSON.class, "deserializeLong", Node.class, long.class), node);
            case "float":
                return pe.invoke(new MethodReference(JSON.class, "deserializeFloat", Node.class, float.class), node);
            case "double":
                return pe.invoke(new MethodReference(JSON.class, "deserializeDouble", Node.class, double.class), node);
            case "char":
                return pe.invoke(new MethodReference(JSON.class, "deserializeChar", Node.class, char.class), node);
        }
        throw new AssertionError("Unknown primitive type: " + type);
    }

    private ValueEmitter createArrayDeserializer(GenericArrayType type) {
        ValueEmitter itemDeserializer = createDeserializer(type.getGenericComponentType());
        return pe.construct(new MethodReference(ArrayDeserializer.class, "<init>", Class.class,
                JsonDeserializer.class, void.class), pe.constant(Object.class), itemDeserializer);
    }

    private ValueEmitter createArrayDeserializer(Class<?> type) {
        if (type.getComponentType().isPrimitive()) {
            String name = type.getComponentType().getName();
            String deserializerClass = BooleanArrayDeserializer.class.getPackage().getName() +
                    Character.toUpperCase(name.charAt(0)) + name.substring(1) + "ArrayDeserializer";
            return pe.construct(new MethodReference(deserializerClass, "<init>", ValueType.VOID));
        } else {
            ValueEmitter itemDeserializer = createDeserializer(type.getComponentType());
            return pe.construct(new MethodReference(ArrayDeserializer.class, "<init>", Class.class,
                    JsonDeserializer.class, void.class), pe.constant(type), itemDeserializer);
        }
    }

    private ValueEmitter createObjectDeserializer(Class<?> type) {
        String deserializerClass = addClassDeserializer(type.getName());
        return pe.construct(new MethodReference(deserializerClass, "<init>", ValueType.VOID));
    }

    private ValueEmitter createMapDeserializer(Type keyType, Type valueType) {
        ValueEmitter keyDeserializer = createDeserializer(keyType);
        ValueEmitter valueDeserializer = createDeserializer(valueType);
        return pe.construct(new MethodReference(MapDeserializer.class, "<init>", JsonDeserializer.class,
                JsonDeserializer.class, void.class), keyDeserializer, valueDeserializer);
    }

    private ValueEmitter createListDeserializer(Type itemType) {
        ValueEmitter itemDeserializer = createDeserializer(itemType);
        return pe.construct(new MethodReference(ListDeserializer.class, "<init>", JsonDeserializer.class,
                void.class), itemDeserializer);
    }

    private ValueEmitter createSetDeserializer(Type itemType) {
        ValueEmitter itemDeserializer = createDeserializer(itemType);
        return pe.construct(new MethodReference(SetDeserializer.class, "<init>", JsonDeserializer.class,
                void.class), itemDeserializer);
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
}
