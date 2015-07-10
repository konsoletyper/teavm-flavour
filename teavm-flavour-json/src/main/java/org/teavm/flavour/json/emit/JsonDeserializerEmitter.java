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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.DependencyAgent;
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
        informationProvider = new ClassInformationProvider(classSource, agent.getDiagnostics());
    }

    public String addClassSerializer(String serializedClassName) {
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
        cls.setParent(JsonDeserializer.class.getName());

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
            MethodHolder method = new MethodHolder("deserialize", ValueType.parse(JsonDeserializerContext.class),
                    ValueType.parse(Node.class), ValueType.parse(Object.class));
            method.setLevel(AccessLevel.PUBLIC);

            pe = ProgramEmitter.create(method);
            pe.newVar(); // skip this variable
            contextVar = pe.newVar();
            nodeVar = pe.newVar();

            emitIdCheck(information, cls);
            emitNodeTypeCheck(information, cls);

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

    private void emitIdCheck(ClassInformation information, ClassHolder cls) {
        switch (information.idGenerator) {
            case INTEGER:
                emitIntegerIdCheck(information, cls);
                break;
            case PROPERTY:
                emitIntegerIdCheck(information, cls);
                break;
            default:
                break;
        }
    }

    private void emitIntegerIdCheck(ClassInformation information, ClassHolder cls) {
        BasicBlock solidObject = pe.getProgram().createBasicBlock();
        BasicBlock thinObject = pe.getProgram().createBasicBlock();

        ForkEmitter fork = nodeVar.invokeVirtual(new MethodReference(Node.class, "isInt()", boolean.class))
                .fork(BranchingCondition.NOT_EQUAL);
        fork.setThen(thinObject);
        fork.setElse(solidObject);

        pe.setBlock(thinObject);
        ValueEmitter id = nodeVar.cast(ValueType.parse(NumberNode.class))
                .invokeVirtual(new MethodReference(NumberNode.class, "getIntValue", int.class));
        id = pe.invoke(new MethodReference(Integer.class, "valueOf", int.class), id);
        contextVar.invokeVirtual(new MethodReference(JsonDeserializerContext.class, "get",
                Object.class, Object.class), id)
                .returnValue();

        pe.setBlock(solidObject);
    }

    private void emitPropertyIdCheck(ClassInformation information, ClassHolder cls) {

    }

    private void emitNodeTypeCheck(ClassInformation information, ClassHolder cls) {
        BasicBlock errorBlock = pe.getProgram().createBasicBlock();
        BasicBlock okBlock = pe.getProgram().createBasicBlock();

        ForkEmitter fork = nodeVar.invokeVirtual(new MethodReference(Node.class, "isObject()", boolean.class))
                .fork(BranchingCondition.NOT_EQUAL);
        fork.setThen(okBlock);
        fork.setElse(errorBlock);

        pe.setBlock(errorBlock);
        ValueEmitter ex = pe.construct(new MethodReference(IllegalArgumentException.class, "<init>", String.class),
                pe.constant("Can't deserialize non-object node to an instance of " + information.className));
        RaiseInstruction raise = new RaiseInstruction();
        raise.setException(ex.getVariable());
        pe.addInstruction(raise);

        pe.setBlock(okBlock);
        nodeVar = nodeVar.cast(ValueType.object(ObjectNode.class.getName()));
    }
}
