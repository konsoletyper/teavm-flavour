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
package org.teavm.flavour.mp.impl.meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.common.DisjointSet;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.Reflected;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
import org.teavm.model.Instruction;
import org.teavm.model.InvokeDynamicInstruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ClassConstantInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InstructionVisitor;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.PutElementInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;
import org.teavm.model.instructions.SwitchInstruction;
import org.teavm.model.instructions.UnwrapArrayInstruction;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyUsageFinder {
    private ProxyDescriber describer;
    private Diagnostics diagnostics;
    private int suffixGenerator;

    public ProxyUsageFinder(ProxyDescriber describer, Diagnostics diagnostics) {
        this.describer = describer;
        this.diagnostics = diagnostics;
    }

    public void findUsages(ClassHolder cls, MethodHolder method) {
        Program program = method.getProgram();
        ProgramAnalyzer analyzer = new ProgramAnalyzer();
        for (int i = 0; i < program.variableCount(); ++i) {
            analyzer.variableSets.create();
        }
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            analyzer.block = block;
            for (int j = 0; j < block.getInstructions().size(); ++j) {
                analyzer.index = j;
                block.getInstructions().get(j).acceptVisitor(analyzer);
            }
        }

        for (Invocation invocation : analyzer.invocations) {
            processUsage(cls, method, analyzer, invocation);
        }
    }

    private void processUsage(ClassHolder cls, MethodHolder method, ProgramAnalyzer analyzer, Invocation invocation) {
        CallLocation location = new CallLocation(method.getReference(), invocation.insn.getLocation());
        List<ProxyParameter> parameters = invocation.proxy.getParameters();
        List<Object> constants = new ArrayList<>();
        List<ValueType> usageSignatureBuilder = new ArrayList<>();
        for (int i = 0; i < parameters.size(); ++i) {
            ProxyParameter proxyParam = parameters.get(i);
            Variable arg = invocation.insn.getArguments().get(i);
            if (proxyParam.getKind() == ParameterKind.CONSTANT) {
                Object cst = analyzer.constant(arg.getIndex());
                if (cst == null) {
                    diagnostics.error(location, "Parameter " + (i + 1) + " of proxy method has type {{c0}}, "
                            + "this means that you can only pass constant literals to parameter " + i
                            + "of method {{m1}}", invocation.proxy.getProxyMethod().parameterType(i + 1),
                            invocation.proxy.getMethod());
                }
                constants.add(cst);
            } else {
                constants.add(null);
                usageSignatureBuilder.add(parameters.get(i).getType());
            }
        }
        usageSignatureBuilder.add(method.getResultType());
        ValueType[] usageSignature = usageSignatureBuilder.toArray(new ValueType[0]);
        if (usageSignature.length == method.parameterCount()) {
            return;
        }

        MethodDescriptor descriptor;
        do {
            String name = method.getName() + "$usage" + suffixGenerator++;
            descriptor = new MethodDescriptor(name, usageSignature);
        } while (cls.getMethod(descriptor) != null);

        MethodHolder usageMethod = new MethodHolder(descriptor);
        usageMethod.getModifiers().add(ElementModifier.STATIC);
        usageMethod.getModifiers().add(ElementModifier.NATIVE);
        usageMethod.getAnnotations().add(new AnnotationHolder(Reflected.class.getName()));
        Program usageProgram = new Program();
        usageMethod.setProgram(usageProgram);
        BasicBlock block = usageProgram.createBasicBlock();
        usageProgram.createVariable();
        for (int i = 0; i < usageMethod.parameterCount(); ++i) {
            usageProgram.createVariable();
        }

        InvokeInstruction invoke = new InvokeInstruction();
        invoke.setReceiver(method.getResultType() != ValueType.VOID ? usageProgram.createVariable() : null);
        invoke.setMethod(new MethodReference(cls.getName(), descriptor));
        invoke.setType(InvocationType.SPECIAL);

        ExitInstruction exit = new ExitInstruction();
        exit.setValueToReturn(invoke.getReceiver());

        int j = 1;
        for (int i = 0; i < parameters.size(); ++i) {
            ProxyParameter param = parameters.get(i);
            if (param.getKind() == ParameterKind.CONSTANT) {
                if (constants.get(i) == null) {
                    invoke.getArguments().add(emitDefault(block, param.getType()));
                } else {
                    invoke.getArguments().add(emitConstant(block, constants));
                }
            } else {
                invoke.getArguments().add(usageProgram.variableAt(j++));
            }
        }

        block.getInstructions().add(invoke);
        block.getInstructions().add(exit);

        for (Instruction insn : block.getInstructions()) {
            insn.setLocation(invocation.insn.getLocation());
        }

        cls.addMethod(usageMethod);
    }

    private Variable emitConstant(BasicBlock block, Object constant) {
        Program program = block.getProgram();
        Variable var = program.createVariable();
        if (constant instanceof Boolean) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Boolean) constant ? 1 : 0);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Byte) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Byte) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Short) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Short) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Character) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Character) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Integer) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Integer) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Long) {
            LongConstantInstruction insn = new LongConstantInstruction();
            insn.setConstant((Long) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Float) {
            FloatConstantInstruction insn = new FloatConstantInstruction();
            insn.setConstant((Float) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof Double) {
            DoubleConstantInstruction insn = new DoubleConstantInstruction();
            insn.setConstant((Double) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof String) {
            StringConstantInstruction insn = new StringConstantInstruction();
            insn.setConstant((String) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else if (constant instanceof ValueType) {
            ClassConstantInstruction insn = new ClassConstantInstruction();
            insn.setConstant((ValueType) constant);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        } else {
            NullConstantInstruction insn = new NullConstantInstruction();
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return var;
        }
    }

    private Variable emitDefault(BasicBlock block, ValueType type) {
        Program program = block.getProgram();
        Variable var = program.createVariable();
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case CHARACTER:
                case INTEGER: {
                    IntegerConstantInstruction insn = new IntegerConstantInstruction();
                    insn.setConstant(0);
                    insn.setReceiver(var);
                    block.getInstructions().add(insn);
                    return var;
                }
                case LONG: {
                    LongConstantInstruction insn = new LongConstantInstruction();
                    insn.setConstant(0);
                    insn.setReceiver(var);
                    block.getInstructions().add(insn);
                    return var;
                }
                case FLOAT: {
                    FloatConstantInstruction insn = new FloatConstantInstruction();
                    insn.setConstant(0);
                    insn.setReceiver(var);
                    block.getInstructions().add(insn);
                    return var;
                }
                case DOUBLE: {
                    DoubleConstantInstruction insn = new DoubleConstantInstruction();
                    insn.setConstant(0);
                    insn.setReceiver(var);
                    block.getInstructions().add(insn);
                    return var;
                }
            }
        }
        NullConstantInstruction insn = new NullConstantInstruction();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
        return var;
    }

    class ProgramAnalyzer implements InstructionVisitor {
        DisjointSet variableSets = new DisjointSet();
        Map<Integer, Object> constants = new HashMap<>();
        List<Invocation> invocations = new ArrayList<>();
        BasicBlock block;
        int index;

        Object constant(int index) {
            return constants.get(variableSets.find(index));
        }

        @Override
        public void visit(EmptyInstruction insn) {
        }

        @Override
        public void visit(ClassConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), insn.getConstant());
        }

        @Override
        public void visit(NullConstantInstruction insn) {
        }

        @Override
        public void visit(IntegerConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), insn.getConstant());
        }

        @Override
        public void visit(LongConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), insn.getConstant());
        }

        @Override
        public void visit(FloatConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), insn.getConstant());
        }

        @Override
        public void visit(DoubleConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), insn.getConstant());
        }

        @Override
        public void visit(StringConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), insn.getConstant());
        }

        @Override
        public void visit(BinaryInstruction insn) {
        }

        @Override
        public void visit(NegateInstruction insn) {
        }

        @Override
        public void visit(AssignInstruction insn) {
            Object cst = constants.get(variableSets.find(insn.getReceiver().getIndex()));
            if (cst == null) {
                cst = constants.get(variableSets.find(insn.getAssignee().getIndex()));
            }
            int result = variableSets.union(insn.getAssignee().getIndex(), insn.getReceiver().getIndex());
            if (cst != null) {
                constants.put(result, cst);
            }
        }

        @Override
        public void visit(CastInstruction insn) {
        }

        @Override
        public void visit(CastNumberInstruction insn) {
        }

        @Override
        public void visit(CastIntegerInstruction insn) {
        }

        @Override
        public void visit(BranchingInstruction insn) {
        }

        @Override
        public void visit(BinaryBranchingInstruction insn) {
        }

        @Override
        public void visit(JumpInstruction insn) {
        }

        @Override
        public void visit(SwitchInstruction insn) {
        }

        @Override
        public void visit(ExitInstruction insn) {
        }

        @Override
        public void visit(RaiseInstruction insn) {
        }

        @Override
        public void visit(ConstructArrayInstruction insn) {
        }

        @Override
        public void visit(ConstructInstruction insn) {
        }

        @Override
        public void visit(ConstructMultiArrayInstruction insn) {
        }

        @Override
        public void visit(GetFieldInstruction insn) {
        }

        @Override
        public void visit(PutFieldInstruction insn) {
        }

        @Override
        public void visit(ArrayLengthInstruction insn) {
        }

        @Override
        public void visit(CloneArrayInstruction insn) {
        }

        @Override
        public void visit(UnwrapArrayInstruction insn) {
        }

        @Override
        public void visit(GetElementInstruction insn) {
        }

        @Override
        public void visit(PutElementInstruction insn) {
        }

        @Override
        public void visit(InvokeInstruction insn) {
            if (insn.getType() != InvocationType.SPECIAL || insn.getInstance() == null) {
                return;
            }
            Invocation invocation = new Invocation();
            invocation.insn = insn;
            invocation.index = index;
            invocation.block = block;
            invocation.proxy = describer.getProxy(insn.getMethod());
            invocations.add(invocation);
        }

        @Override
        public void visit(InvokeDynamicInstruction insn) {
        }

        @Override
        public void visit(IsInstanceInstruction insn) {
        }

        @Override
        public void visit(InitClassInstruction insn) {
        }

        @Override
        public void visit(NullCheckInstruction insn) {
        }

        @Override
        public void visit(MonitorEnterInstruction insn) {
        }

        @Override
        public void visit(MonitorExitInstruction insn) {
        }
    }

    static class Invocation {
        InvokeInstruction insn;
        ProxyModel proxy;
        BasicBlock block;
        int index;
    }
}
