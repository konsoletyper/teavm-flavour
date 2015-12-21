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
package org.teavm.flavour.mp.impl.optimize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.common.DisjointSet;
import org.teavm.model.BasicBlock;
import org.teavm.model.Incoming;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.PrimitiveType;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.AssignInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.EmptyInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.util.ListingBuilder;
import org.teavm.model.util.UsageExtractor;

/**
 *
 * @author Alexey Andreev
 */
public class CompositeProgramOptimizer {
    private static Set<String> wrapperClasses = Arrays.asList(Boolean.class, Byte.class, Short.class,
            Character.class, Integer.class, Long.class, Float.class, Double.class).stream()
            .map(cls -> cls.getName())
            .collect(Collectors.toSet());
    private DisjointSet set = new DisjointSet();
    private Program program;
    private List<Wrapper> wrappers = new ArrayList<>();
    private Set<Integer> provenTargets = new HashSet<>();

    public void optimize(Program program) {
        this.program = program;
        System.out.println("Before optimization:");
        System.out.println(new ListingBuilder().buildListing(program, "    "));
        initDisjointSet();
        findPotentialWrappers();
        excludeNonWrappers();
        removeInstructions();
        System.out.println("After optimization:");
        System.out.println(new ListingBuilder().buildListing(program, "    "));
        System.out.println();
    }

    private void initDisjointSet() {
        for (int i = 0; i < program.variableCount(); ++i) {
            set.create();
        }
    }

    private void findPotentialWrappers() {
        wrappers.addAll(Collections.nCopies(program.variableCount(), (Wrapper) null));

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            for (Instruction insn : block.getInstructions()) {
                if (!(insn instanceof InvokeInstruction)) {
                    continue;
                }
                InvokeInstruction invoke = (InvokeInstruction) insn;
                MethodReference method = invoke.getMethod();
                if (!method.getName().equals("valueOf") || !wrapperClasses.contains(method.getClassName())
                        || invoke.getReceiver() == null) {
                    continue;
                }

                Wrapper wrapper = new Wrapper();
                wrapper.type = ((ValueType.Primitive) method.parameterType(0)).getKind();
                wrappers.set(invoke.getReceiver().getIndex(), wrapper);
            }
        }
    }

    private void excludeNonWrappers() {
        UsageExtractor use = new UsageExtractor();

        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            for (Instruction insn : block.getInstructions()) {
                if (insn instanceof AssignInstruction) {
                    AssignInstruction assign = (AssignInstruction) insn;
                    union(assign.getReceiver().getIndex(), assign.getAssignee().getIndex());
                    continue;
                } else if (insn instanceof CastInstruction) {
                    CastInstruction cast = (CastInstruction) insn;
                    Wrapper wrapper = wrappers.get(set.find(cast.getValue().getIndex()));
                    if (wrapper != null) {
                        if (!wrapper.excluded && isAcceptableCast(wrapper.type, cast.getTargetType())) {
                            union(cast.getValue().getIndex(), cast.getReceiver().getIndex());
                            continue;
                        }
                    }
                } else if (insn instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) insn;
                    MethodReference method = invoke.getMethod();
                    if (wrapperClasses.contains(method.getClassName()) && method.getName().endsWith("Value")
                            && method.parameterCount() == 0 && method.getReturnType() instanceof ValueType.Primitive) {
                        Wrapper wrapper = wrappers.get(set.find(invoke.getInstance().getIndex()));
                        if (wrapper != null && !wrapper.excluded && isAcceptableMethod(wrapper.type, method)) {
                            provenTargets.add(invoke.getReceiver().getIndex());
                            continue;
                        }
                    }
                }

                insn.acceptVisitor(use);
                for (Variable usedVar : use.getUsedVariables()) {
                    Wrapper wrapper = wrappers.get(set.find(usedVar.getIndex()));
                    if (wrapper != null) {
                        wrapper.excluded = true;
                    }
                }
            }

            for (Phi phi : block.getPhis()) {
                for (Incoming incoming : phi.getIncomings()) {
                    union(phi.getReceiver().getIndex(), incoming.getValue().getIndex());
                }
            }
        }
    }

    private void removeInstructions() {
        for (int i = 0; i < program.basicBlockCount(); ++i) {
            BasicBlock block = program.basicBlockAt(i);
            for (int j = 0; j < block.getInstructions().size(); ++j) {
                Instruction insn = block.getInstructions().get(j);
                if (insn instanceof CastInstruction) {
                    CastInstruction cast = (CastInstruction) insn;
                    Wrapper wrapper = wrappers.get(set.find(cast.getValue().getIndex()));
                    if (wrapper != null && !wrapper.excluded) {
                        AssignInstruction assign = new AssignInstruction();
                        assign.setReceiver(cast.getReceiver());
                        assign.setAssignee(cast.getValue());
                        block.getInstructions().set(j, assign);
                    }
                } else if (insn instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) insn;
                    MethodReference method = invoke.getMethod();
                    if (invoke.getReceiver() != null) {
                        Wrapper wrapper = wrappers.get(set.find(invoke.getReceiver().getIndex()));
                        if (wrapper != null && !wrapper.excluded) {
                            AssignInstruction assign = new AssignInstruction();
                            assign.setReceiver(invoke.getReceiver());
                            assign.setAssignee(invoke.getArguments().get(0));
                            block.getInstructions().set(j, assign);
                            continue;
                        }
                    }
                    if (wrapperClasses.contains(method.getClassName()) && method.getName().endsWith("Value")
                            && method.parameterCount() == 0 && method.getReturnType() instanceof ValueType.Primitive) {
                        Wrapper wrapper = wrappers.get(set.find(invoke.getInstance().getIndex()));
                        if (wrapper != null && !wrapper.excluded && isAcceptableMethod(wrapper.type, method)) {
                            if (invoke.getReceiver() != null) {
                                AssignInstruction assign = new AssignInstruction();
                                assign.setReceiver(invoke.getReceiver());
                                assign.setAssignee(invoke.getInstance());
                                block.getInstructions().set(j, assign);
                            } else {
                                block.getInstructions().set(j, new EmptyInstruction());
                            }
                        }
                    }
                }
            }
        }
    }

    private int union(int a, int b) {
        Wrapper wrapper;
        if (wrappers.get(a) == null) {
            wrapper = wrappers.get(b);
        } else if (wrappers.get(b) == null) {
            wrapper = wrappers.get(a);
        } else {
            wrapper = wrappers.get(a);
            wrapper.excluded |= wrappers.get(b).excluded;
            wrapper.excluded |= !wrappers.get(a).type.equals(wrappers.get(b).type);
        }
        int c = set.union(a, b);
        wrappers.set(c, wrapper);
        return c;
    }

    private boolean isAcceptableCast(PrimitiveType wrapperType, ValueType target) {
        if (target.isObject(Object.class)) {
            return true;
        }
        switch (wrapperType) {
            case BOOLEAN:
                return target.isObject(Boolean.class);
            case BYTE:
                return target.isObject(Byte.class) || target.isObject(Number.class);
            case SHORT:
                return target.isObject(Short.class) || target.isObject(Number.class);
            case CHARACTER:
                return target.isObject(Character.class);
            case INTEGER:
                return target.isObject(Integer.class) || target.isObject(Number.class);
            case LONG:
                return target.isObject(Long.class) || target.isObject(Number.class);
            case FLOAT:
                return target.isObject(Float.class) || target.isObject(Number.class);
            case DOUBLE:
                return target.isObject(Double.class) || target.isObject(Number.class);
            default:
                return false;
        }
    }

    private boolean isAcceptableMethod(PrimitiveType wrapperType, MethodReference method) {
        switch (wrapperType) {
            case BOOLEAN:
                return method.getClassName().equals(Boolean.class.getName());
            case BYTE:
                return method.getClassName().equals(Byte.class.getName());
            case SHORT:
                return method.getClassName().equals(Short.class.getName());
            case CHARACTER:
                return method.getClassName().equals(Character.class.getName());
            case INTEGER:
                return method.getClassName().equals(Integer.class.getName());
            case LONG:
                return method.getClassName().equals(Long.class.getName());
            case FLOAT:
                return method.getClassName().equals(Float.class.getName());
            case DOUBLE:
                return method.getClassName().equals(Double.class.getName());
            default:
                return false;
        }
    }

    static class Wrapper {
        PrimitiveType type;
        boolean excluded;
    }
}
