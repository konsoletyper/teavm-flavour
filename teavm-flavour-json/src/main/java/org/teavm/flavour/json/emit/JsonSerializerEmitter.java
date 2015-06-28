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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyConsumer;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.BooleanNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;
import org.teavm.model.AccessLevel;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ForkEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.NumericOperandType;

/**
 *
 * @author Alexey Andreev
 */
class JsonSerializerEmitter {
    private DependencyAgent agent;
    private ClassReaderSource classSource;
    private ClassReader serializedClass;
    private ValueEmitter contextVar;
    private ValueEmitter valueVar;
    private ValueEmitter targetVar;
    private ProgramEmitter pe;
    private Map<String, ClassSerializerInformation> informationStore = new HashMap<>();
    private List<ClassReader> serializableClasses = new ArrayList<>();
    private DependencyConsumer serializableClassesConsumer;
    private Set<String> pendingClasses = new HashSet<>();

    public JsonSerializerEmitter(DependencyAgent agent, DependencyConsumer serializableClassesConsumer) {
        this.agent = agent;
        this.classSource = agent.getClassSource();
        this.serializableClassesConsumer = serializableClassesConsumer;
    }

    public void addClassSerializer(String serializedClassName) {
        ClassReader cls = classSource.get(serializedClassName);
        if (cls != null && cls.getAnnotations().get(NotJsonSerializable.class.getName()) != null) {
            return;
        }
        if (isSerializable(serializedClassName)) {
            findClassSerializer(serializedClassName);
        } else {
            pendingClasses.add(serializedClassName);
        }
    }

    public String getClassSerializer(String className) {
        ClassSerializerInformation information = informationStore.get(className);
        return information != null ? information.serializerName : null;
    }

    private ClassSerializerInformation findClassSerializer(String serializedClassName) {
        ClassSerializerInformation information = informationStore.get(serializedClassName);
        if (information == null) {
            information = emitClassSerializer(serializedClassName);
            informationStore.put(serializedClassName, information);
            serializableClassesConsumer.consume(agent.getType(serializedClassName));
        }
        return information;
    }

    private ClassSerializerInformation emitClassSerializer(String serializedClassName) {
        ClassHolder cls = new ClassHolder(serializedClassName + "$$__serializer__$$");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(JsonSerializer.class.getName());
        cls.getAnnotations().add(new AnnotationHolder(NotJsonSerializable.class.getName()));
        ClassSerializerInformation information = emitSerializationMethod(serializedClassName, cls);
        emitConstructor(cls);
        agent.submitClass(cls);

        if (information == null) {
            information = new ClassSerializerInformation();
            information.serializerName = cls.getName();
        }

        return information;
    }

    private void emitConstructor(ClassHolder cls) {
        MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);

        ProgramEmitter pe = ProgramEmitter.create(ctor);
        ValueEmitter thisVar = pe.newVar();
        thisVar.invokeSpecial(new MethodReference(JsonSerializer.class, "<init>", void.class));
        pe.exit();

        cls.addMethod(ctor);
    }

    private ClassSerializerInformation emitSerializationMethod(String serializedClassName, ClassHolder cls) {
        serializedClass = classSource.get(serializedClassName);
        if (serializedClass == null) {
            return null;
        }

        try {
            ClassSerializerInformation information = new ClassSerializerInformation();
            information.serializerName = cls.getName();
            MethodHolder method = new MethodHolder("serialize", ValueType.parse(JsonSerializerContext.class),
                    ValueType.parse(Object.class), ValueType.parse(ObjectNode.class), ValueType.VOID);
            method.setLevel(AccessLevel.PUBLIC);
            pe = ProgramEmitter.create(method);
            pe.newVar(); // skip this variable
            contextVar = pe.newVar();
            valueVar = pe.newVar();
            targetVar = pe.newVar();
            valueVar = valueVar.cast(ValueType.object(serializedClassName));
            emitSuperSerializer(information);
            scanGetters(information);
            cls.addMethod(method);
            return information;
        } finally {
            pe = null;
            valueVar = null;
            contextVar = null;
            serializedClass = null;
        }
    }

    private void emitSuperSerializer(ClassSerializerInformation information) {
        if (serializedClass.getParent().equals(Object.class.getName())) {
            return;
        }
        information.parentInformation = findClassSerializer(serializedClass.getParent());
        ValueEmitter superSerializer = pe.construct(new MethodReference(information.parentInformation.serializerName,
                "<init>", ValueType.VOID));
        superSerializer.invokeVirtual(new MethodReference(JsonSerializer.class, "serialize",
                JsonSerializerContext.class, Object.class, Node.class, void.class), contextVar, valueVar, targetVar);
        information.properties.putAll(information.parentInformation.properties);
        information.getters.putAll(information.parentInformation.getters);
    }

    private void scanGetters(ClassSerializerInformation information) {
        for (MethodReader method : serializedClass.getMethods()) {
            if (isGetterName(method.getName()) && method.parameterCount() == 0 &&
                    method.getResultType() != ValueType.VOID) {
                String propertyName = decapitalize(method.getName().substring(3));
                addGetter(information, propertyName, method, method.getResultType());
            } else if (isBooleanName(method.getName()) && method.parameterCount() == 0 &&
                    method.getResultType() == ValueType.BOOLEAN) {
                String propertyName = decapitalize(method.getName().substring(2));
                addGetter(information, propertyName, method, method.getResultType());
            }
        }
    }

    private boolean isGetterName(String name) {
        return name.startsWith("get") && name.length() > 3 && Character.toUpperCase(name.charAt(3)) == name.charAt(3);
    }

    private boolean isBooleanName(String name) {
        return name.startsWith("is") && name.length() > 2 && Character.toUpperCase(name.charAt(2)) == name.charAt(2);
    }

    private String decapitalize(String name) {
        if (name.length() > 1 && name.charAt(1) == Character.toUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private void addGetter(ClassSerializerInformation information, String propertyName, MethodReader method,
            ValueType type) {
        SerializerPropertyInformation property = information.properties.get(propertyName);
        duplication: if (property != null) {
            if (property.getter != null && property.getter.equals(method.getDescriptor())) {
                break duplication;
            }
            CallLocation location = new CallLocation(method.getReference());
            agent.getDiagnostics().error(location, "Duplicate property declaration " + propertyName + ". " +
                    "Already declared in {{c0}}", property.className);
            return;
        }

        property = new SerializerPropertyInformation();
        property.className = method.getOwnerName();
        property.getter = method.getDescriptor();
        information.properties.put(propertyName, property);

        ValueEmitter propertyValue = convertValue(valueVar.invokeVirtual(method.getReference()), type);
        targetVar.invokeSpecial(new MethodReference(ObjectNode.class, "set", int.class, Node.class, void.class),
                propertyValue);
    }

    private ValueEmitter convertValue(ValueEmitter value, final ValueType type) {
        if (type instanceof ValueType.Primitive) {
            return convertPrimitive(value, (ValueType.Primitive)type);
        } else {
            return convertNullable(value, type);
        }
    }

    private ValueEmitter convertNullable(ValueEmitter value, ValueType type) {
        BasicBlock nullBlock = pe.createBlock();
        BasicBlock notNullBlock = pe.createBlock();
        BasicBlock resultBlock = pe.createBlock();

        ForkEmitter fork = value.fork(BinaryBranchingCondition.REFERENCE_EQUAL, pe.constantNull());
        fork.setThen(nullBlock);
        fork.setElse(notNullBlock);

        pe.setBlock(nullBlock);
        pe.jump(resultBlock);

        pe.setBlock(notNullBlock);
        ValueEmitter notNullValue;
        if (type instanceof ValueType.Array) {
            notNullValue = convertArray(value, (ValueType.Array)type);
        } else if (type instanceof ValueType.Object) {
            notNullValue = convertObject(value, (ValueType.Object)type);
        } else {
            notNullValue = value;
        }
        pe.jump(resultBlock);

        value = notNullValue.join(value);
        return value;
    }

    private ValueEmitter convertPrimitive(ValueEmitter value, ValueType.Primitive type) {
        switch (type.getKind()) {
            case BOOLEAN:
                return pe.invoke(new MethodReference(BooleanNode.class, "get", boolean.class, BooleanNode.class),
                        value);
            case BYTE:
                value = value.cast(IntegerSubtype.BYTE, CastIntegerDirection.TO_INTEGER);
                return pe.invoke(new MethodReference(NumberNode.class, "create", int.class, NumberNode.class),
                        value);
            case SHORT:
                value = value.cast(IntegerSubtype.BYTE, CastIntegerDirection.TO_INTEGER);
                return pe.invoke(new MethodReference(NumberNode.class, "create", int.class, NumberNode.class),
                        value);
            case CHARACTER:
                value = value.cast(IntegerSubtype.CHARACTER, CastIntegerDirection.TO_INTEGER);
                return pe.invoke(new MethodReference(NumberNode.class, "create", int.class, NumberNode.class),
                        value);
            case INTEGER:
                return pe.invoke(new MethodReference(NumberNode.class, "create", int.class, NumberNode.class),
                        value);
            case FLOAT:
                value = value.cast(NumericOperandType.FLOAT, NumericOperandType.DOUBLE);
                return pe.invoke(new MethodReference(NumberNode.class, "create", double.class, NumberNode.class),
                        value);
            case DOUBLE:
                return pe.invoke(new MethodReference(NumberNode.class, "create", double.class, NumberNode.class),
                        value);
            case LONG:
                value = value.cast(NumericOperandType.LONG, NumericOperandType.DOUBLE);
                return pe.invoke(new MethodReference(NumberNode.class, "create", double.class, NumberNode.class),
                        value);
            default:
                throw new AssertionError("Unknown primitive type: " + type);
        }
    }

    private ValueEmitter convertArray(ValueEmitter value, ValueType.Array type) {
        ValueType itemType = type.getItemType();

        BasicBlock loopBody = pe.createBlock();
        BasicBlock loopDecision = pe.createBlock();
        BasicBlock loopExit = pe.createBlock();

        ValueEmitter json = pe.invoke(new MethodReference(ArrayNode.class, "create", ArrayNode.class));
        ValueEmitter startIndex = pe.constant(0);
        ValueEmitter incIndex = pe.newVar();
        ValueEmitter incStep = pe.constant(1);
        ValueEmitter size = value.arrayLength();
        pe.jump(loopDecision);

        ValueEmitter index = incIndex.join(startIndex);
        ForkEmitter fork = index.compare(NumericOperandType.INT, size).fork(BranchingCondition.LESS);
        fork.setThen(loopBody);
        fork.setElse(loopExit);

        pe.setBlock(loopBody);
        ValueEmitter item = value.getElement(index);
        json.setElement(0, convertValue(item, itemType));

        BinaryInstruction increment = new BinaryInstruction(BinaryOperation.ADD, NumericOperandType.INT);
        increment.setFirstOperand(index.getVariable());
        increment.setSecondOperand(incStep.getVariable());
        increment.setReceiver(incIndex.getVariable());
        pe.addInstruction(increment);
        pe.jump(loopDecision);

        pe.setBlock(loopExit);
        return json;
    }

    private ValueEmitter convertObject(ValueEmitter value, ValueType.Object type) {
        if (type.getClassName().equals(String.class)) {
            return pe.invoke(new MethodReference(StringNode.class, "create", String.class, StringNode.class), value);
        } else {
            return pe.invoke(new MethodReference(JSON.class, "serialize",
                    JsonSerializerContext.class, Object.class, Node.class), contextVar, value);
        }
    }

    public void addSerializableClass(String className) {
        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return;
        }
        for (int i = 0; i < serializableClasses.size(); ++i) {
            ClassReader serializableClass = serializableClasses.get(i);
            if (isSubtype(cls, serializableClass.getName())) {
                serializableClasses.remove(i--);
            } else if (isSubtype(serializableClass, cls.getName())) {
                return;
            }
        }
        serializableClasses.add(cls);
        for (String pendingClass : pendingClasses.toArray(new String[pendingClasses.size()])) {
            if (isSubtype(cls, pendingClass)) {
                pendingClasses.remove(pendingClass);
                findClassSerializer(className);
            }
        }
    }

    private boolean isSubtype(ClassReader supertype, String subtypeName) {
        if (supertype.getName().equals(subtypeName)) {
            return true;
        }

        ClassReader subtype = classSource.get(subtypeName);
        if (subtype == null) {
            return false;
        }

        if (subtype.getParent() != null && !subtype.getParent().equals(subtype.getName())) {
            if (isSubtype(supertype, subtype.getParent())) {
                return true;
            }
        }

        for (String iface : subtype.getInterfaces()) {
            if (isSubtype(supertype, iface)) {
                return true;
            }
        }

        return false;
    }

    private boolean isSerializable(String className) {
        for (ClassReader serializableClass : serializableClasses) {
            if (isSubtype(serializableClass, className)) {
                return true;
            }
        }
        return false;
    }
}
