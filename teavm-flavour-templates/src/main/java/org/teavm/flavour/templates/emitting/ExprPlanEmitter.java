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
package org.teavm.flavour.templates.emitting;

import org.teavm.flavour.expr.plan.*;
import org.teavm.model.*;
import org.teavm.model.instructions.*;
import org.teavm.model.instructions.IntegerSubtype;

/**
 *
 * @author Alexey Andreev
 */
class ExprPlanEmitter implements PlanVisitor {
    private Program program;
    private Variable var;
    private BasicBlock block;
    private BranchingConsumer branching;

    @Override
    public void visit(ConstantPlan plan) {
        var = program.createVariable();
        Object value = plan.getValue();
        if (value == null) {
            NullConstantInstruction insn = new NullConstantInstruction();
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Boolean) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Boolean)value ? 1 : 0);
            block.getInstructions().add(insn);
        } else if (value instanceof Byte) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant(((Byte)value).intValue());
            block.getInstructions().add(insn);
        } else if (value instanceof Short) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant(((Short)value).intValue());
            block.getInstructions().add(insn);
        } else if (value instanceof Character) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant(((Character)value).charValue());
            block.getInstructions().add(insn);
        } else if (value instanceof Integer) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Integer)value);
            block.getInstructions().add(insn);
        } else if (value instanceof Float) {
            FloatConstantInstruction insn = new FloatConstantInstruction();
            insn.setConstant((Float)value);
            block.getInstructions().add(insn);
        } else if (value instanceof Double) {
            DoubleConstantInstruction insn = new DoubleConstantInstruction();
            insn.setConstant((Double)value);
            block.getInstructions().add(insn);
        } else if (value instanceof String) {
            StringConstantInstruction insn = new StringConstantInstruction();
            insn.setConstant((String)value);
            block.getInstructions().add(insn);
        }
    }

    @Override
    public void visit(VariablePlan plan) {

    }

    @Override
    public void visit(BinaryPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        requireValue();
        Variable first = var;
        plan.getSecondOperand().acceptVisitor(this);
        requireValue();
        Variable second = var;

        BinaryOperation op = mapBinary(plan.getType());
        if (op != null) {
            var = program.createVariable();
            BinaryInstruction insn = new BinaryInstruction(op, mapArithmetic(plan.getValueType()));
            insn.setFirstOperand(first);
            insn.setSecondOperand(second);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            return;
        }
        BinaryBranchingCondition binaryCond = mapBinaryCondition(plan.getType());
        if (binaryCond != null) {
            final BinaryBranchingInstruction insn = new BinaryBranchingInstruction(binaryCond);
            insn.setFirstOperand(first);
            insn.setSecondOperand(second);
            block.getInstructions().add(insn);
            branching = new BinaryInstructionBranchingConsumer(insn);
            return;
        }
        BinaryInstruction comparison = new BinaryInstruction(BinaryOperation.COMPARE,
                mapArithmetic(plan.getValueType()));
        comparison.setFirstOperand(first);
        comparison.setSecondOperand(second);
        var = program.createVariable();
        comparison.setReceiver(var);
        block.getInstructions().add(comparison);

        final BranchingInstruction insn = new BranchingInstruction(mapCondition(plan.getType()));
        insn.setOperand(var);
        block.getInstructions().add(insn);
        branching = new BranchingConsumer() {
            @Override public void setThen(BasicBlock block) {
                insn.setConsequent(block);
            }
            @Override public void setElse(BasicBlock block) {
                insn.setAlternative(block);
            }
        };
    }

    @Override
    public void visit(NegatePlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();

        NegateInstruction insn = new NegateInstruction(mapArithmetic(plan.getValueType()));
        insn.setOperand(var);
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(ReferenceEqualityPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        requireValue();
        Variable first = var;
        plan.getSecondOperand().acceptVisitor(this);
        requireValue();
        Variable second = var;

        final BinaryBranchingInstruction insn = new BinaryBranchingInstruction(mapBinaryCondition(plan.getType()));
        insn.setFirstOperand(first);
        insn.setSecondOperand(second);
        block.getInstructions().add(insn);
        branching = new BranchingConsumer() {
            @Override public void setThen(BasicBlock block) {
                insn.setConsequent(block);
            }
            @Override public void setElse(BasicBlock block) {
                insn.setAlternative(block);
            }
        };
    }

    @Override
    public void visit(LogicalBinaryPlan plan) {
        switch (plan.getType()) {
            case AND: {
                plan.getFirstOperand().acceptVisitor(this);
                valueToBranching();
                final BranchingConsumer firstBranching = branching;
                block = program.createBasicBlock();
                firstBranching.setThen(block);
                plan.getSecondOperand().acceptVisitor(this);
                valueToBranching();
                final BranchingConsumer secondBranching = branching;
                branching = new BranchingConsumer() {
                    @Override public void setThen(BasicBlock block) {
                        secondBranching.setThen(block);
                    }
                    @Override public void setElse(BasicBlock block) {
                        firstBranching.setElse(block);
                        secondBranching.setElse(block);
                    }
                };
                break;
            }
            case OR: {
                plan.getFirstOperand().acceptVisitor(this);
                valueToBranching();
                final BranchingConsumer firstBranching = branching;
                block = program.createBasicBlock();
                firstBranching.setElse(block);
                plan.getSecondOperand().acceptVisitor(this);
                valueToBranching();
                final BranchingConsumer secondBranching = branching;
                branching = new BranchingConsumer() {
                    @Override public void setThen(BasicBlock block) {
                        firstBranching.setThen(block);
                        secondBranching.setThen(block);
                    }
                    @Override public void setElse(BasicBlock block) {
                        secondBranching.setElse(block);
                    }
                };
                break;
            }
        }
    }

    @Override
    public void visit(NotPlan plan) {
        plan.getOperand().acceptVisitor(this);
        valueToBranching();
        final BranchingConsumer oldBranching = branching;
        branching = new BranchingConsumer() {
            @Override public void setThen(BasicBlock block) {
                oldBranching.setElse(block);
            }
            @Override public void setElse(BasicBlock block) {
                oldBranching.setThen(block);
            }
        };
    }

    @Override
    public void visit(CastPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        CastInstruction insn = new CastInstruction();
        insn.setTargetType(ValueType.parse(plan.getTargetType()));
        insn.setValue(var);
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(ArithmeticCastPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        CastNumberInstruction insn = new CastNumberInstruction(mapArithmetic(plan.getSourceType()),
                mapArithmetic(plan.getTargetType()));
        insn.setValue(var);
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(CastFromIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        CastIntegerInstruction insn = new CastIntegerInstruction(mapInteger(plan.getType()),
                CastIntegerDirection.FROM_INTEGER);
        insn.setValue(var);
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(CastToIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        CastIntegerInstruction insn = new CastIntegerInstruction(mapInteger(plan.getType()),
                CastIntegerDirection.TO_INTEGER);
        insn.setValue(var);
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(GetArrayElementPlan plan) {
        plan.getArray().acceptVisitor(this);
        requireValue();
        Variable array = var;
        plan.getIndex().acceptVisitor(this);
        requireValue();
        Variable index = var;

        GetElementInstruction insn = new GetElementInstruction();
        insn.setArray(array);
        insn.setIndex(index);
        var = program.createVariable();
        insn.setReceiver(index);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(ArrayLengthPlan plan) {
        plan.getArray().acceptVisitor(this);
        requireValue();

        ArrayLengthInstruction insn = new ArrayLengthInstruction();
        insn.setArray(var);
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(FieldPlan plan) {
        Variable instance = null;
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
            instance = var;
        }

        GetFieldInstruction insn = new GetFieldInstruction();
        insn.setInstance(instance);
        insn.setField(new FieldReference(plan.getClassName(), plan.getFieldName()));
        insn.setFieldType(ValueType.parse(plan.getFieldDesc()));
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(InstanceOfPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();

        IsInstanceInstruction insn = new IsInstanceInstruction();
        insn.setValue(var);
        insn.setType(ValueType.parse(plan.getClassName()));
        var = program.createVariable();
        insn.setReceiver(var);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(InvocationPlan plan) {
    }

    @Override
    public void visit(ConstructionPlan plan) {
    }

    void valueToBranching() {
        if (branching != null) {
            return;
        }

        Variable constVar = program.createVariable();
        IntegerConstantInstruction constant = new IntegerConstantInstruction();
        constant.setConstant(0);
        constant.setReceiver(constVar);
        block.getInstructions().add(constant);

        final BinaryBranchingInstruction insn = new BinaryBranchingInstruction(BinaryBranchingCondition.EQUAL);
        insn.setFirstOperand(var);
        insn.setSecondOperand(constVar);
        block.getInstructions().add(insn);
        branching = new BinaryInstructionBranchingConsumer(insn);
    }

    void requireValue() {
        if (branching == null) {
            return;
        }

        BasicBlock thenBlock = program.createBasicBlock();
        Variable trueVar = program.createVariable();
        IntegerConstantInstruction insn = new IntegerConstantInstruction();
        insn.setConstant(1);
        insn.setReceiver(trueVar);
        thenBlock.getInstructions().add(insn);
        JumpInstruction jump = new JumpInstruction();
        jump.setTarget(block);
        thenBlock.getInstructions().add(jump);

        BasicBlock elseBlock = program.createBasicBlock();
        Variable falseVar = program.createVariable();
        insn = new IntegerConstantInstruction();
        insn.setConstant(0);
        insn.setReceiver(falseVar);
        elseBlock.getInstructions().add(insn);
        jump = new JumpInstruction();
        jump.setTarget(block);
        elseBlock.getInstructions().add(jump);

        var = program.createVariable();
        Phi phi = new Phi();
        Incoming trueIncoming = new Incoming();
        trueIncoming.setSource(thenBlock);
        trueIncoming.setValue(trueVar);
        phi.getIncomings().add(trueIncoming);
        Incoming falseIncoming = new Incoming();
        falseIncoming.setSource(elseBlock);
        falseIncoming.setValue(falseVar);
        phi.getIncomings().add(falseIncoming);
        block.getPhis().add(phi);

        branching.setThen(thenBlock);
        branching.setElse(elseBlock);
        branching = null;
    }

    private NumericOperandType mapArithmetic(ArithmeticType type) {
        switch (type) {
            case INT:
                return NumericOperandType.INT;
            case LONG:
                return NumericOperandType.LONG;
            case FLOAT:
                return NumericOperandType.FLOAT;
            case DOUBLE:
                return NumericOperandType.DOUBLE;
            default:
                throw new AssertionError();
        }
    }

    private IntegerSubtype mapInteger(org.teavm.flavour.expr.plan.IntegerSubtype type) {
        switch (type) {
            case BYTE:
                return IntegerSubtype.BYTE;
            case CHAR:
                return IntegerSubtype.CHARACTER;
            case SHORT:
                return IntegerSubtype.SHORT;
            default:
                throw new AssertionError();
        }
    }

    private BinaryOperation mapBinary(BinaryPlanType type) {
        switch (type) {
            case ADD:
                return BinaryOperation.ADD;
            case SUBTRACT:
                return BinaryOperation.SUBTRACT;
            case MULTIPLY:
                return BinaryOperation.MULTIPLY;
            case DIVIDE:
                return BinaryOperation.DIVIDE;
            case REMAINDER:
                return BinaryOperation.MODULO;
            case EQUAL:
            case NOT_EQUAL:
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL:
                return null;
            default:
                throw new AssertionError();
        }
    }

    private BinaryBranchingCondition mapBinaryCondition(BinaryPlanType type) {
        switch (type) {
            case EQUAL:
                return BinaryBranchingCondition.EQUAL;
            case NOT_EQUAL:
                return BinaryBranchingCondition.NOT_EQUAL;
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER:
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL:
                return null;
            default:
                throw new AssertionError();
        }
    }

    private BinaryBranchingCondition mapBinaryCondition(ReferenceEqualityPlanType type) {
        switch (type) {
            case EQUAL:
                return BinaryBranchingCondition.EQUAL;
            case NOT_EQUAL:
                return BinaryBranchingCondition.NOT_EQUAL;
            default:
                throw new AssertionError();
        }
    }

    private BranchingCondition mapCondition(BinaryPlanType type) {
        switch (type) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER:
            case EQUAL:
            case NOT_EQUAL:
                return null;
            case LESS:
                return BranchingCondition.LESS;
            case LESS_OR_EQUAL:
                return BranchingCondition.LESS_OR_EQUAL;
            case GREATER:
                return BranchingCondition.GREATER;
            case GREATER_OR_EQUAL:
                return BranchingCondition.GREATER_OR_EQUAL;
            default:
                throw new AssertionError();
        }
    }

    static class BinaryInstructionBranchingConsumer implements BranchingConsumer {
        private BinaryBranchingInstruction insn;

        public BinaryInstructionBranchingConsumer(BinaryBranchingInstruction insn) {
            this.insn = insn;
        }

        @Override
        public void setThen(BasicBlock block) {
            insn.setConsequent(block);
        }

        @Override
        public void setElse(BasicBlock block) {
            insn.setAlternative(block);
        }
    }

    interface BranchingConsumer {
        void setThen(BasicBlock block);

        void setElse(BasicBlock block);
    }
}
