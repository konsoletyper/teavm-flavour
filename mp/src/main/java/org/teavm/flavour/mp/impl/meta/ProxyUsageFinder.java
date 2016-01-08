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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.teavm.common.DisjointSet;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.impl.CapturedValue;
import org.teavm.flavour.mp.impl.ProxyMethod;
import org.teavm.model.AnnotationHolder;
import org.teavm.model.AnnotationValue;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ElementModifier;
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
    Map<MethodReference, List<Runnable>> deferredErrors = new HashMap<>();

    public ProxyUsageFinder(ProxyDescriber describer, Diagnostics diagnostics) {
        this.describer = describer;
        this.diagnostics = diagnostics;
    }

    public List<Runnable> getDeferredErrors(MethodReference method) {
        return deferredErrors.getOrDefault(method, Collections.emptyList());
    }

    public void findUsages(ClassHolder cls, MethodReference method, Program program) {
        if (program == null) {
            return;
        }

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

    private void processUsage(ClassHolder cls, MethodReference method, ProgramAnalyzer analyzer,
            Invocation invocation) {
        CallLocation location = new CallLocation(method, invocation.insn.getLocation());
        List<ProxyParameter> parameters = invocation.proxy.getParameters();
        List<CapturedValue> constants = new ArrayList<>();
        List<ValueType> usageSignatureBuilder = new ArrayList<>();
        boolean errors = false;
        for (int i = 0; i < parameters.size(); ++i) {
            ProxyParameter proxyParam = parameters.get(i);
            Variable arg = invocation.insn.getArguments().get(i);
            if (proxyParam.getKind() == ParameterKind.CONSTANT) {
                CapturedValue cst = analyzer.constant(arg.getIndex());
                if (cst == null) {
                    errors = true;
                    List<Runnable> deferredErrorList = deferredErrors.computeIfAbsent(method,
                            m -> new ArrayList<>());
                    int index = i;
                    deferredErrorList.add(() -> {
                        diagnostics.error(location, "Parameter " + (index + 1) + " of proxy method has type {{t0}}, "
                                + "this means that you can only pass constant literals to parameter " + index + " "
                                + "of method {{m1}}", invocation.proxy.getProxyMethod().parameterType(index + 1),
                                invocation.proxy.getMethod());
                    });
                }
                constants.add(cst);
            } else {
                constants.add(null);
                usageSignatureBuilder.add(parameters.get(i).getType());
            }
        }
        if (usageSignatureBuilder.size() == invocation.insn.getMethod().parameterCount() || errors) {
            return;
        }

        usageSignatureBuilder.add(invocation.insn.getMethod().getReturnType());
        ValueType[] usageSignature = usageSignatureBuilder.toArray(new ValueType[0]);
        MethodDescriptor descriptor;
        do {
            String name = invocation.insn.getMethod().getName() + "$usage" + suffixGenerator++;
            descriptor = new MethodDescriptor(name, usageSignature);
        } while (cls.getMethod(descriptor) != null);

        MethodHolder usageMethod = new MethodHolder(descriptor);
        usageMethod.getModifiers().add(ElementModifier.STATIC);
        usageMethod.getModifiers().add(ElementModifier.NATIVE);
        usageMethod.getAnnotations().add(new AnnotationHolder(Reflected.class.getName()));

        int j = 0;
        List<String> textualArguments = new ArrayList<>();
        List<Variable> nonConstantArgs = new ArrayList<>();
        for (int i = 0; i < parameters.size(); ++i) {
            ProxyParameter param = parameters.get(i);
            if (param.getKind() == ParameterKind.CONSTANT) {
                textualArguments.add(emitConstant(constants.get(i)));
            } else {
                nonConstantArgs.add(invocation.insn.getArguments().get(i));
                textualArguments.add(String.valueOf(j++));
            }
        }

        cls.addMethod(usageMethod);
        invocation.insn.setMethod(usageMethod.getReference());
        invocation.insn.getArguments().clear();
        invocation.insn.getArguments().addAll(nonConstantArgs);

        AnnotationHolder proxyMethodAnnot = new AnnotationHolder(ProxyMethod.class.getName());
        proxyMethodAnnot.getValues().put("value", new AnnotationValue(invocation.proxy.getProxyMethod().toString()));
        proxyMethodAnnot.getValues().put("arguments", new AnnotationValue(textualArguments.stream()
                .map(arg -> new AnnotationValue(arg))
                .collect(Collectors.toList())));
        usageMethod.getAnnotations().add(proxyMethodAnnot);
    }

    private String emitConstant(CapturedValue constantWrapper) {
        Object constant = constantWrapper.obj;
        if (constant == null) {
            return "null";
        }
        if (constant instanceof Boolean
                || constant instanceof Byte
                || constant instanceof Short
                || constant instanceof Character
                || constant instanceof Integer
                || constant instanceof Long
                || constant instanceof Float
                || constant instanceof Double
                || constant instanceof String
                || constant instanceof ValueType) {
            return "#" + String.valueOf(constant);
        }
        return "";
    }

    class ProgramAnalyzer implements InstructionVisitor {
        DisjointSet variableSets = new DisjointSet();
        Map<Integer, CapturedValue> constants = new HashMap<>();
        List<Invocation> invocations = new ArrayList<>();
        BasicBlock block;
        int index;

        CapturedValue constant(int index) {
            return constants.get(variableSets.find(index));
        }

        @Override
        public void visit(EmptyInstruction insn) {
        }

        @Override
        public void visit(ClassConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(insn.getConstant(),
                    false));
        }

        @Override
        public void visit(NullConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(null, false));
        }

        @Override
        public void visit(IntegerConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(insn.getConstant(),
                    true));
        }

        @Override
        public void visit(LongConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(insn.getConstant(),
                    true));
        }

        @Override
        public void visit(FloatConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(insn.getConstant(),
                    true));
        }

        @Override
        public void visit(DoubleConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(insn.getConstant(),
                    true));
        }

        @Override
        public void visit(StringConstantInstruction insn) {
            constants.put(variableSets.find(insn.getReceiver().getIndex()), new CapturedValue(insn.getConstant(),
                    false));
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
                constants.put(result, new CapturedValue(cst, false));
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
            if (insn.getType() != InvocationType.SPECIAL || insn.getInstance() != null) {
                return;
            }
            Invocation invocation = new Invocation();
            invocation.insn = insn;
            invocation.index = index;
            invocation.block = block;
            invocation.proxy = describer.getProxy(insn.getMethod());
            if (invocation.proxy != null) {
                invocations.add(invocation);
            }
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
