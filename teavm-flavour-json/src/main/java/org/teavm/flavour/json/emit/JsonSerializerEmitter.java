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

import java.util.HashSet;
import java.util.Set;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyConsumer;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.DependencyType;
import org.teavm.dependency.MethodDependency;
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
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ForkEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.ArrayElementType;
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
    private ClassInformationProvider informationProvider;
    private Set<String> serializableClasses = new HashSet<>();

    public JsonSerializerEmitter(DependencyAgent agent) {
        this.agent = agent;
        this.classSource = agent.getClassSource();
        informationProvider = new ClassInformationProvider(classSource, agent.getDiagnostics());
    }

    public String addClassSerializer(String serializedClassName) {
        ClassReader cls = classSource.get(serializedClassName);
        if (cls == null) {
            return null;
        }
        if (serializableClasses.add(serializedClassName)) {
            emitClassSerializer(serializedClassName);
        }
        return getClassSerializer(serializedClassName);
    }

    public String getClassSerializer(String className) {
        return className + "$$__serializer__$$";
    }


    private void emitClassSerializer(String serializedClassName) {
        ClassInformation information = informationProvider.get(serializedClassName);
        if (information == null) {
            return;
        }

        ClassHolder cls = new ClassHolder(serializedClassName + "$$__serializer__$$");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(JsonSerializer.class.getName());

        emitSerializationMethod(information, cls);
        emitConstructor(cls);
        agent.submitClass(cls);
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

    private void emitSerializationMethod(ClassInformation information, ClassHolder cls) {
        serializedClass = classSource.get(information.className);
        if (serializedClass == null) {
            return;
        }

        ProgramEmitter oldPe = pe;
        ValueEmitter oldValueVar = valueVar;
        ValueEmitter oldTagetVar = targetVar;
        ValueEmitter oldContextVar = contextVar;
        ClassReader oldSerializedClass = serializedClass;
        try {
            MethodHolder method = new MethodHolder("serialize", ValueType.parse(JsonSerializerContext.class),
                    ValueType.parse(Object.class), ValueType.parse(ObjectNode.class), ValueType.VOID);
            method.setLevel(AccessLevel.PUBLIC);

            pe = ProgramEmitter.create(method);
            pe.newVar(); // skip this variable
            contextVar = pe.newVar();
            valueVar = pe.newVar();
            targetVar = pe.newVar();
            valueVar = valueVar.cast(ValueType.object(information.className));

            emitGetters(information);

            pe.exit();
            cls.addMethod(method);
        } finally {
            pe = oldPe;
            valueVar = oldValueVar;
            targetVar = oldTagetVar;
            contextVar = oldContextVar;
            serializedClass = oldSerializedClass;
        }
    }

    private void emitGetters(ClassInformation information) {
        for (GetterInformation getter : information.getters.values()) {
            if (getter.ignored) {
                continue;
            }
            PropertyInformation property = information.properties.get(getter.targetProperty);
            emitGetter(property);
        }
    }

    private void emitGetter(PropertyInformation property) {
        MethodReference method = new MethodReference(property.className, property.getter);

        MethodDependency getterDep = agent.linkMethod(method, null);
        ValueEmitter propertyValue = convertValue(valueVar.invokeVirtual(method), method.getReturnType(),
                getterDep.getResult());
        targetVar.invokeSpecial(new MethodReference(ObjectNode.class, "set", String.class, Node.class, void.class),
                pe.constant(property.name), propertyValue);
    }

    private ValueEmitter convertValue(ValueEmitter value, final ValueType type, DependencyNode node) {
        if (type instanceof ValueType.Primitive) {
            return convertPrimitive(value, (ValueType.Primitive)type);
        } else {
            return convertNullable(value, type, node);
        }
    }

    private ValueEmitter convertNullable(ValueEmitter value, ValueType type, DependencyNode node) {
        BasicBlock nullBlock = pe.getProgram().createBasicBlock();
        BasicBlock notNullBlock = pe.getProgram().createBasicBlock();
        BasicBlock resultBlock = pe.getProgram().createBasicBlock();

        ForkEmitter fork = value.fork(BinaryBranchingCondition.REFERENCE_EQUAL, pe.constantNull());
        fork.setThen(nullBlock);
        fork.setElse(notNullBlock);

        pe.setBlock(nullBlock);
        pe.jump(resultBlock);

        pe.setBlock(notNullBlock);
        ValueEmitter notNullValue;
        if (type instanceof ValueType.Array) {
            notNullValue = convertArray(value, (ValueType.Array)type, node);
        } else if (type instanceof ValueType.Object) {
            notNullValue = convertObject(value, (ValueType.Object)type, node);
        } else {
            notNullValue = value;
        }
        notNullBlock = pe.getBlock();
        pe.jump(resultBlock);

        value = notNullValue.join(notNullBlock, value, nullBlock);
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

    private ValueEmitter convertArray(ValueEmitter value, ValueType.Array type, DependencyNode node) {
        ValueType itemType = type.getItemType();
        ArrayElementType arrayElementType = getArrayElementType(itemType);

        BasicBlock loopBody = pe.getProgram().createBasicBlock();
        BasicBlock loopDecision = pe.getProgram().createBasicBlock();
        BasicBlock loopExit = pe.getProgram().createBasicBlock();
        BasicBlock loopEnd = pe.getProgram().createBasicBlock();

        BasicBlock loopEnter = pe.getBlock();
        ValueEmitter json = pe.invoke(new MethodReference(ArrayNode.class, "create", ArrayNode.class));
        ValueEmitter startIndex = pe.constant(0);
        ValueEmitter incIndex = pe.newVar();
        ValueEmitter incStep = pe.constant(1);
        value = value.unwrapArray(arrayElementType);
        ValueEmitter size = value.arrayLength();
        pe.jump(loopDecision);

        ValueEmitter index = incIndex.join(loopEnd, startIndex, loopEnter);
        ForkEmitter fork = index.compare(NumericOperandType.INT, size).fork(BranchingCondition.LESS);
        fork.setThen(loopBody);
        fork.setElse(loopExit);

        pe.setBlock(loopBody);
        ValueEmitter item = value.getElement(index);
        json.invokeVirtual(new MethodReference(ArrayNode.class, "add", Node.class, void.class),
                convertValue(item, itemType, node.getArrayItem()));

        pe.jump(loopEnd);
        BinaryInstruction increment = new BinaryInstruction(BinaryOperation.ADD, NumericOperandType.INT);
        increment.setFirstOperand(index.getVariable());
        increment.setSecondOperand(incStep.getVariable());
        increment.setReceiver(incIndex.getVariable());
        pe.addInstruction(increment);
        pe.jump(loopDecision);

        pe.setBlock(loopExit);
        return json;
    }

    private ArrayElementType getArrayElementType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive)type).getKind()) {
                case BOOLEAN:
                case BYTE:
                    return ArrayElementType.BYTE;
                case CHARACTER:
                    return ArrayElementType.CHAR;
                case SHORT:
                    return ArrayElementType.SHORT;
                case INTEGER:
                    return ArrayElementType.INT;
                case FLOAT:
                    return ArrayElementType.FLOAT;
                case DOUBLE:
                    return ArrayElementType.DOUBLE;
                case LONG:
                    return ArrayElementType.LONG;
            }
        }
        return ArrayElementType.OBJECT;
    }

    private ValueEmitter convertObject(ValueEmitter value, ValueType.Object type, DependencyNode node) {
        if (type.getClassName().equals(String.class.getName())) {
            return pe.invoke(new MethodReference(StringNode.class, "create", String.class, StringNode.class), value);
        } else {
            final MethodReference serializeRef = new MethodReference(JSON.class, "serialize",
                    JsonSerializerContext.class, Object.class, Node.class);
            node.addConsumer(new DependencyConsumer() {
                @Override
                public void consume(DependencyType type) {
                    agent.linkMethod(serializeRef, null).propagate(2, type);
                }
            });
            return pe.invoke(serializeRef, contextVar, value);
        }
    }
}
