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
package org.teavm.flavour.rest.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.flavour.rest.RESTClient;
import org.teavm.flavour.rest.ResourceFactory;
import org.teavm.flavour.rest.impl.model.BeanRepository;
import org.teavm.flavour.rest.impl.model.ResourceModelRepository;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.CallLocation;
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
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.StringChooseEmitter;
import org.teavm.model.emit.ValueEmitter;
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
class RESTDependencyListener extends AbstractDependencyListener {
    private FactoryEmitter emitter;
    private Set<MethodReference> processedMethods = new HashSet<>();
    private Map<String, String> proxyClassNames = new HashMap<>();
    private DependencyNode proxiedClasses;

    @Override
    public void started(DependencyAgent agent) {
        BeanRepository beanRepository = new BeanRepository(agent.getClassSource(), agent.getDiagnostics());
        ResourceModelRepository resourceRepository = new ResourceModelRepository(agent.getDiagnostics(),
                agent.getClassSource(), beanRepository);
        emitter = new FactoryEmitter(resourceRepository, agent);
        proxiedClasses = agent.createNode();
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method, CallLocation location) {
        if (method.getReference().getClassName().equals(RESTClient.class.getName())
                && method.getReference().getName().equals("factory")) {
            proxiedClasses.addConsumer(type -> registerType(agent, type.getName(), location));
        } else if (method.getMethod() != null) {
            if (!processedMethods.add(method.getReference())) {
                return;
            }
            MethodReader methodReader = method.getMethod();
            if (methodReader.getProgram() != null) {
                findProxiedClasses(agent, methodReader.getProgram());
            }
        }
    }

    private void registerType(DependencyAgent agent, String typeName, CallLocation location) {
        MethodDependency implMethod = agent.linkMethod(new MethodReference(RESTClient.class, "factoryImpl",
                String.class, ResourceFactory.class), location);

        String factoryClass = proxyClassNames.computeIfAbsent(typeName, emitter::emitFactory);
        MethodDependency ctor = agent.linkMethod(new MethodReference(factoryClass, "<init>", ValueType.VOID), location)
                .propagate(0, factoryClass);
        ctor.getThrown().connect(implMethod.getThrown());
        ctor.use();
        implMethod.getResult().propagate(agent.getType(factoryClass));

        MethodDependency equalsDep = agent.linkMethod(new MethodReference(String.class, "equals", Object.class,
                boolean.class), location);
        equalsDep.getThrown().connect(implMethod.getThrown());
        equalsDep.use();

        MethodDependency hashCodeDep = agent.linkMethod(new MethodReference(String.class, "hashCode", int.class),
                location);
        hashCodeDep.getThrown().connect(implMethod.getThrown());
        hashCodeDep.use();
    }

    @Override
    public void completing(DependencyAgent agent) {
        MethodReference method = new MethodReference(RESTClient.class, "factoryImpl", String.class,
                ResourceFactory.class);

        ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassSource());
        ValueEmitter typeVar = pe.var(1, String.class);

        StringChooseEmitter choice = pe.stringChoice(typeVar);
        for (String factoryImpl : proxyClassNames.keySet()) {
            String implementorType = proxyClassNames.get(factoryImpl);
            choice.option(factoryImpl, () -> {
                pe.construct(implementorType).returnValue();
            });
        }
        choice.otherwise(() -> pe.constantNull(ResourceFactory.class).returnValue());

        agent.submitMethod(method, pe.getProgram());
    }

    private void findProxiedClasses(DependencyAgent agent, ProgramReader program) {
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
                ValueType.Object objType = (ValueType.Object) valueType;
                proxiedClasses.propagate(agent.getType(objType.getClassName()));
            }
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
            if (methodRef.getClassName().equals(RESTClient.class.getName())
                    && methodRef.getName().equals("factory")) {
                interestingVariables.add(arguments.get(0).getIndex());
            }
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
