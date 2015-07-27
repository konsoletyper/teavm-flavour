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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyConsumer;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.DependencyType;
import org.teavm.dependency.MethodDependency;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.json.JSON;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReference;
import org.teavm.model.IncomingReader;
import org.teavm.model.InstructionLocation;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.PhiReader;
import org.teavm.model.ProgramReader;
import org.teavm.model.RuntimeConstant;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.ArrayElementType;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.InstructionReader;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.SwitchTableEntryReader;

/**
 *
 * @author Alexey Andreev
 */
class DeserializerDependencyListener extends AbstractDependencyListener {
    private JsonDeserializerEmitter emitter;
    private boolean generated;
    private DependencyNode deserializableClasses;
    private Map<MethodReference, Integer> deserializeMethods;
    private Set<MethodReference> processedMethods = new HashSet<>();

    @Override
    public void started(DependencyAgent agent) {
        deserializableClasses = agent.createNode();
        emitter = new JsonDeserializerEmitter(agent, deserializableClasses);
    }

    @Override
    public void methodReached(final DependencyAgent agent, final MethodDependency method,
            final CallLocation location) {
        if (method.getReference().getClassName().equals(JSON.class.getName()) &&
                method.getReference().getName().equals("findClassDeserializer")) {
            deserializableClasses.addConsumer(new DependencyConsumer() {
                @Override
                public void consume(DependencyType type) {
                    String deserializerName = emitter.addClassDeserializer(type.getName());
                    agent.linkMethod(new MethodReference(deserializerName, "<init>", ValueType.VOID), location)
                            .propagate(0, deserializerName)
                            .use();
                    method.getResult().propagate(agent.getType(deserializerName));
                }
            });
            generateDeserializers(agent, location);
        } else if (method.getMethod() != null) {
            if (!processedMethods.add(method.getReference())) {
                return;
            }
            MethodReader methodReader = method.getMethod();
            if (methodReader.getProgram() != null) {
                findDeserializeMethods(agent.getClassLoader(), agent.getDiagnostics());
                findDeserializableClasses(agent, methodReader.getProgram());
            }
        }
    }

    private void generateDeserializers(DependencyAgent agent, CallLocation location) {
        if (generated) {
            return;
        }
        generated = true;

        try {
            Enumeration<URL> resources = agent.getClassLoader().getResources("META-INF/flavour/deserializable");
            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.openStream()))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        ClassReader cls = agent.getClassSource().get(line);
                        if (cls == null) {
                            agent.getDiagnostics().warning(location, "Can't find class {{c0}} declared by " +
                                    res.toString(), line);
                        }

                        deserializableClasses.propagate(agent.getType(line));
                    }
                }
            }
        } catch (IOException e) {
            agent.getDiagnostics().error(location, "IO error occured getting deserializer list");
        }
    }

    public String getDeserializer(String className) {
        if (emitter == null) {
            return null;
        }
        return emitter.getClassDeserializer(className);
    }

    private void findDeserializableClasses(DependencyAgent agent, ProgramReader program) {
        VariableGraphBuilder builder = new VariableGraphBuilder(program.variableCount());
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlockReader block = program.basicBlockAt(i);
            block.readAllInstructions(builder);
            for (PhiReader phi : block.readPhis()) {
                for (IncomingReader incoming : phi.readIncomings()) {
                    builder.edge(incoming.getValue().getIndex(), phi.getReceiver().getIndex());
                }
            }
        }

        for (ValueType valueType : builder.build()) {
            if (valueType instanceof ValueType.Object) {
                ValueType.Object objType = (ValueType.Object)valueType;
                deserializableClasses.propagate(agent.getType(objType.getClassName()));
            }
        }
    }

    private void findDeserializeMethods(ClassLoader classLoader, Diagnostics diagnostics) {
        if (deserializeMethods != null) {
            return;
        }
        deserializeMethods = new HashMap<>();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/flavour/deserialize-methods");
            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.openStream()))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        int colonIndex = line.lastIndexOf(':');
                        if (colonIndex < 0) {
                            diagnostics.error(null, "Invalid deserializer method: " + line);
                            continue;
                        }

                        MethodReference methodRef;
                        try {
                            methodRef = MethodReference.parse(line.substring(0, colonIndex));
                        } catch (RuntimeException e) {
                            diagnostics.error(null, "Invalid deserializer method: " + line);
                            continue;
                        }

                        int argIndex;
                        try {
                            argIndex = Integer.parseInt(line.substring(colonIndex + 1));
                        } catch (RuntimeException e) {
                            diagnostics.error(null, "Invalid deserializer method: " + line);
                            continue;
                        }

                        deserializeMethods.put(methodRef, argIndex);
                    }
                }
            }
        } catch (IOException e) {
            diagnostics.error(null, "IO error occured getting deserializer method list");
        }
    }

    static class Step {
        int variable;
        ValueType type;
        public Step(int variable, ValueType type) {
            this.variable = variable;
            this.type = type;
        }
    }

    class VariableGraphBuilder implements InstructionReader {
        private GraphBuilder graphBuilder;
        private List<Set<ValueType>> sets = new ArrayList<>();
        private Set<Integer> interestingVariables = new HashSet<>();
        private List<Step> initialSteps = new ArrayList<>();

        public VariableGraphBuilder(int sz) {
            graphBuilder = new GraphBuilder(sz);
            for (int i = 0; i < sz; ++i) {
                sets.add(new HashSet<ValueType>());
            }
        }

        public void edge(int from, int to) {
            graphBuilder.addEdge(from, to);
        }

        public Set<ValueType> build() {
            Graph graph = graphBuilder.build();
            Set<ValueType> types = new HashSet<>();
            Queue<Step> queue = new ArrayDeque<>();
            queue.addAll(initialSteps);

            while (!queue.isEmpty()) {
                Step step = queue.remove();
                if (!sets.get(step.variable).add(step.type)) {
                    continue;
                }
                if (interestingVariables.contains(step.variable)) {
                    types.add(step.type);
                }
                for (int succ : graph.outgoingEdges(step.variable)) {
                    queue.add(new Step(succ, step.type));
                }
            }
            return types;
        }

        @Override
        public void location(InstructionLocation location) {
        }

        @Override
        public void nop() {
        }

        @Override
        public void classConstant(VariableReader receiver, ValueType cst) {
            initialSteps.add(new Step(receiver.getIndex(), cst));
        }

        @Override
        public void nullConstant(VariableReader receiver) {
        }

        @Override
        public void integerConstant(VariableReader receiver, int cst) {
        }

        @Override
        public void longConstant(VariableReader receiver, long cst) {
        }

        @Override
        public void floatConstant(VariableReader receiver, float cst) {
        }

        @Override
        public void doubleConstant(VariableReader receiver, double cst) {
        }

        @Override
        public void stringConstant(VariableReader receiver, String cst) {
        }

        @Override
        public void binary(BinaryOperation op, VariableReader receiver, VariableReader first, VariableReader second,
                NumericOperandType type) {
        }

        @Override
        public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) {
        }

        @Override
        public void assign(VariableReader receiver, VariableReader assignee) {
            if (receiver != null) {
                graphBuilder.addEdge(assignee.getIndex(), receiver.getIndex());
            }
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, ValueType targetType) {
            graphBuilder.addEdge(value.getIndex(), receiver.getIndex());
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType,
                NumericOperandType targetType) {
        }

        @Override
        public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type,
                CastIntegerDirection targetType) {
        }

        @Override
        public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent,
                BasicBlockReader alternative) {
        }

        @Override
        public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second,
                BasicBlockReader consequent, BasicBlockReader alternative) {
        }

        @Override
        public void jump(BasicBlockReader target) {
        }

        @Override
        public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table,
                BasicBlockReader defaultTarget) {
        }

        @Override
        public void exit(VariableReader valueToReturn) {
        }

        @Override
        public void raise(VariableReader exception) {
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) {
        }

        @Override
        public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) {
        }

        @Override
        public void create(VariableReader receiver, String type) {
        }

        @Override
        public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) {
        }

        @Override
        public void putField(VariableReader instance, FieldReference field, VariableReader value,
                ValueType fieldType) {
        }

        @Override
        public void arrayLength(VariableReader receiver, VariableReader array) {
        }

        @Override
        public void cloneArray(VariableReader receiver, VariableReader array) {
        }

        @Override
        public void unwrapArray(VariableReader receiver, VariableReader array, ArrayElementType elementType) {
        }

        @Override
        public void getElement(VariableReader receiver, VariableReader array, VariableReader index) {
        }

        @Override
        public void putElement(VariableReader array, VariableReader index, VariableReader value) {
        }

        @Override
        public void invoke(VariableReader receiver, VariableReader instance, MethodReference methodRef,
                List<? extends VariableReader> arguments, InvocationType type) {
            Integer argIndex = deserializeMethods.get(methodRef);
            if (argIndex == null) {
                return;
            }
            interestingVariables.add(arguments.get(argIndex).getIndex());
        }

        @Override
        public void invokeDynamic(VariableReader receiver, VariableReader instance, MethodDescriptor method,
                List<? extends VariableReader> arguments, MethodHandle bootstrapMethod,
                List<RuntimeConstant> bootstrapArguments) {
        }

        @Override
        public void isInstance(VariableReader receiver, VariableReader value, ValueType type) {
        }

        @Override
        public void initClass(String className) {
        }

        @Override
        public void nullCheck(VariableReader receiver, VariableReader value) {
        }

        @Override
        public void monitorEnter(VariableReader objectRef) {
        }

        @Override
        public void monitorExit(VariableReader objectRef) {
        }
    }
}
