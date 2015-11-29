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
package org.teavm.flavour.mp.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.common.DisjointSet;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.CallLocation;
import org.teavm.model.FieldReference;
import org.teavm.model.InstructionLocation;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHandle;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
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
public class ProxyUsageFinder {
    private ProxyDescriber describer;
    private Diagnostics diagnostics;

    public ProxyUsageFinder(ProxyDescriber describer, Diagnostics diagnostics) {
        this.describer = describer;
        this.diagnostics = diagnostics;
    }

    public void findUsages(MethodDependency dependency) {
        MethodReader method = dependency.getMethod();
        ProgramReader program = method.getProgram();
        ProgramAnalyzer analyzer = new ProgramAnalyzer();
        for (int i = 0; i < program.variableCount(); ++i) {
            analyzer.variableSets.create();
        }
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlockReader block = program.basicBlockAt(i);
            block.readAllInstructions(analyzer);
        }

        for (Invocation invocation : analyzer.invocations) {
            invocation.proxy.getUsages().add(getUsage(dependency, analyzer, invocation));
        }
    }

    private ProxyUsage getUsage(MethodDependency dependency, ProgramAnalyzer analyzer, Invocation invocation) {
        CallLocation location = new CallLocation(dependency.getReference(), invocation.location);
        List<ProxyParameter> parameters = invocation.proxy.getParameters();
        List<Object> constants = new ArrayList<>();
        List<DependencyNode> nodes = new ArrayList<>();
        boolean error = false;
        for (int i = 0; i < parameters.size(); ++i) {
            ProxyParameter proxyParam = parameters.get(i);
            VariableReader arg = invocation.arguments.get(i);
            if (proxyParam.getKind() == ParameterKind.CONSTANT) {
                Object cst = analyzer.constant(arg.getIndex());
                if (cst == null) {
                    error = true;
                    diagnostics.error(location, "Parameter " + (i + 1) + " of proxy method has type {{c0}}, "
                            + "this means that you can only pass constant literals to parameter " + i
                            + "of method {{m1}}", invocation.proxy.getProxyMethod().parameterType(i + 1),
                            invocation.proxy.getMethod());
                }
                constants.add(cst);
                nodes.add(null);
            } else if (proxyParam.getKind() == ParameterKind.REFLECT_VALUE) {
                constants.add(null);
                nodes.add(dependency.getVariable(arg.getIndex()));
            } else {
                constants.add(null);
                nodes.add(null);
            }
        }

        return !error ? new ProxyUsage(location, constants, nodes) : null;
    }

    class ProgramAnalyzer implements InstructionReader {
        private InstructionLocation location;
        DisjointSet variableSets = new DisjointSet();
        Map<Integer, Object> constants = new HashMap<>();
        List<Invocation> invocations = new ArrayList<>();

        Object constant(int index) {
            return constants.get(variableSets.find(index));
        }

        @Override public void location(InstructionLocation location) {
            this.location = location;
        }
        @Override public void nop() { }

        @Override public void classConstant(VariableReader receiver, ValueType cst) {
            constants.put(variableSets.find(receiver.getIndex()), cst);
        }
        @Override public void nullConstant(VariableReader receiver) { }
        @Override public void integerConstant(VariableReader receiver, int cst) {
            constants.put(variableSets.find(receiver.getIndex()), cst);
        }
        @Override public void longConstant(VariableReader receiver, long cst) {
            constants.put(variableSets.find(receiver.getIndex()), cst);
        }
        @Override public void floatConstant(VariableReader receiver, float cst) {
            constants.put(variableSets.find(receiver.getIndex()), cst);
        }
        @Override public void doubleConstant(VariableReader receiver, double cst) {
            constants.put(variableSets.find(receiver.getIndex()), cst);
        }
        @Override public void stringConstant(VariableReader receiver, String cst) {
            constants.put(variableSets.find(receiver.getIndex()), cst);
        }

        @Override public void binary(BinaryOperation op, VariableReader receiver, VariableReader first,
                VariableReader second, NumericOperandType type) { }
        @Override public void negate(VariableReader receiver, VariableReader operand, NumericOperandType type) { }

        @Override public void assign(VariableReader receiver, VariableReader assignee) {
            Object cst = constants.get(variableSets.find(receiver.getIndex()));
            if (cst == null) {
                cst = constants.get(variableSets.find(assignee.getIndex()));
            }
            int result = variableSets.union(receiver.getIndex(), assignee.getIndex());
            if (cst != null) {
                constants.put(result, cst);
            }
        }

        @Override public void cast(VariableReader receiver, VariableReader value, ValueType targetType) { }
        @Override public void cast(VariableReader receiver, VariableReader value, NumericOperandType sourceType,
                NumericOperandType targetType) { }
        @Override public void cast(VariableReader receiver, VariableReader value, IntegerSubtype type,
                CastIntegerDirection targetType) { }
        @Override public void jumpIf(BranchingCondition cond, VariableReader operand, BasicBlockReader consequent,
                BasicBlockReader alternative) { }
        @Override public void jumpIf(BinaryBranchingCondition cond, VariableReader first, VariableReader second,
                BasicBlockReader consequent, BasicBlockReader alternative) { }
        @Override public void jump(BasicBlockReader target) { }
        @Override public void choose(VariableReader condition, List<? extends SwitchTableEntryReader> table,
                BasicBlockReader defaultTarget) { }
        @Override public void exit(VariableReader valueToReturn) { }
        @Override public void raise(VariableReader exception) { }
        @Override public void createArray(VariableReader receiver, ValueType itemType, VariableReader size) { }
        @Override public void createArray(VariableReader receiver, ValueType itemType,
                List<? extends VariableReader> dimensions) { }
        @Override public void create(VariableReader receiver, String type) { }
        @Override public void getField(VariableReader receiver, VariableReader instance, FieldReference field,
                ValueType fieldType) { }
        @Override public void putField(VariableReader instance, FieldReference field, VariableReader value,
                ValueType fieldType) { }
        @Override public void arrayLength(VariableReader receiver, VariableReader array) { }
        @Override public void cloneArray(VariableReader receiver, VariableReader array) { }
        @Override public void unwrapArray(VariableReader receiver, VariableReader array,
                ArrayElementType elementType) { }
        @Override public void getElement(VariableReader receiver, VariableReader array, VariableReader index) { }
        @Override public void putElement(VariableReader array, VariableReader index, VariableReader value) { }

        @Override public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                List<? extends VariableReader> arguments, InvocationType type) {
            ProxyModel model = describer.getProxy(method);
            if (model == null) {
                return;
            }

            Invocation invocation = new Invocation();
            invocation.receiver = receiver;
            invocation.instance = instance;
            invocation.proxy = model;
            invocation.arguments = arguments;
            invocation.location = this.location;
            invocations.add(invocation);
        }

        @Override public void invokeDynamic(VariableReader receiver, VariableReader instance, MethodDescriptor method,
                List<? extends VariableReader> arguments, MethodHandle bootstrapMethod,
                List<RuntimeConstant> bootstrapArguments) { }
        @Override public void isInstance(VariableReader receiver, VariableReader value, ValueType type) { }
        @Override public void initClass(String className) { }
        @Override public void nullCheck(VariableReader receiver, VariableReader value) { }
        @Override public void monitorEnter(VariableReader objectRef) { }
        @Override public void monitorExit(VariableReader objectRef) { }
    }

    static class Invocation {
        VariableReader receiver;
        VariableReader instance;
        ProxyModel proxy;
        List<? extends VariableReader> arguments;
        InstructionLocation location;
    }
}
