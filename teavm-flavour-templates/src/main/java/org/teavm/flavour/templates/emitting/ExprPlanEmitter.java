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

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.expr.plan.ArithmeticCastPlan;
import org.teavm.flavour.expr.plan.ArithmeticType;
import org.teavm.flavour.expr.plan.ArrayLengthPlan;
import org.teavm.flavour.expr.plan.BinaryPlan;
import org.teavm.flavour.expr.plan.BinaryPlanType;
import org.teavm.flavour.expr.plan.CastFromIntegerPlan;
import org.teavm.flavour.expr.plan.CastPlan;
import org.teavm.flavour.expr.plan.CastToIntegerPlan;
import org.teavm.flavour.expr.plan.ConditionalPlan;
import org.teavm.flavour.expr.plan.ConstantPlan;
import org.teavm.flavour.expr.plan.ConstructionPlan;
import org.teavm.flavour.expr.plan.FieldPlan;
import org.teavm.flavour.expr.plan.GetArrayElementPlan;
import org.teavm.flavour.expr.plan.InstanceOfPlan;
import org.teavm.flavour.expr.plan.InvocationPlan;
import org.teavm.flavour.expr.plan.LogicalBinaryPlan;
import org.teavm.flavour.expr.plan.NegatePlan;
import org.teavm.flavour.expr.plan.NotPlan;
import org.teavm.flavour.expr.plan.Plan;
import org.teavm.flavour.expr.plan.PlanVisitor;
import org.teavm.flavour.expr.plan.ReferenceEqualityPlan;
import org.teavm.flavour.expr.plan.ReferenceEqualityPlanType;
import org.teavm.flavour.expr.plan.VariablePlan;
import org.teavm.flavour.templates.Action;
import org.teavm.flavour.templates.Computation;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.Incoming;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ArrayLengthInstruction;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CastIntegerDirection;
import org.teavm.model.instructions.CastIntegerInstruction;
import org.teavm.model.instructions.CastNumberInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.GetElementInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.IsInstanceInstruction;
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.NegateInstruction;
import org.teavm.model.instructions.NullConstantInstruction;
import org.teavm.model.instructions.NumericOperandType;
import org.teavm.model.instructions.StringConstantInstruction;

/**
 *
 * @author Alexey Andreev
 */
class ExprPlanEmitter implements PlanVisitor {
    private EmitContext context;
    String thisClassName;
    Program program;
    Variable var;
    BasicBlock block;
    private BranchingConsumer branching;
    Variable thisVar;

    public ExprPlanEmitter(EmitContext context) {
        this.context = context;
    }

    public String emitComputation(Plan plan) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(Computation.class.getName());
        cls.setLevel(AccessLevel.PUBLIC);
        context.addConstructor(cls);
        emitPerformMethod(cls, plan, true);
        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    public String emitAction(Plan plan) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(Action.class.getName());
        cls.setLevel(AccessLevel.PUBLIC);
        context.addConstructor(cls);
        emitPerformMethod(cls, plan, false);
        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void emitPerformMethod(ClassHolder cls, Plan plan, boolean returnValue) {
        MethodHolder method = new MethodHolder("perform", returnValue ?
                ValueType.parse(Object.class) : ValueType.VOID);
        method.setLevel(AccessLevel.PUBLIC);
        program = new Program();
        thisVar = program.createVariable();
        block = program.createBasicBlock();
        thisClassName = cls.getName();
        plan.acceptVisitor(this);
        ExitInstruction exit = new ExitInstruction();
        if (returnValue) {
            exit.setValueToReturn(var);
        }
        block.getInstructions().add(exit);
        method.setProgram(program);
        cls.addMethod(method);
    }

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
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Byte) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant(((Byte)value).intValue());
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Short) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant(((Short)value).intValue());
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Character) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant(((Character)value).charValue());
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Integer) {
            IntegerConstantInstruction insn = new IntegerConstantInstruction();
            insn.setConstant((Integer)value);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Float) {
            FloatConstantInstruction insn = new FloatConstantInstruction();
            insn.setConstant((Float)value);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof Double) {
            DoubleConstantInstruction insn = new DoubleConstantInstruction();
            insn.setConstant((Double)value);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        } else if (value instanceof String) {
            StringConstantInstruction insn = new StringConstantInstruction();
            insn.setConstant((String)value);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        }
    }

    @Override
    public void visit(VariablePlan plan) {
        EmittedVariable emitVar = context.getVariable(plan.getName());

        String lastClass = thisClassName;
        var = thisVar;
        int bottom = context.classStack.size() - 2;
        for (int i = context.classStack.size() - 1; i >= bottom; --i) {
            GetFieldInstruction insn = new GetFieldInstruction();
            insn.setFieldType(ValueType.object(context.classStack.get(i)));
            insn.setInstance(var);
            insn.setField(new FieldReference(lastClass, "this$owner"));
            var = program.createVariable();
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            lastClass = context.classStack.get(i);
        }

        GetFieldInstruction getVarInsn = new GetFieldInstruction();
        getVarInsn.setField(new FieldReference(lastClass, "cache$" + plan.getName()));
        getVarInsn.setInstance(var);
        getVarInsn.setFieldType(emitVar.type);
        var = program.createVariable();
        getVarInsn.setReceiver(var);
        block.getInstructions().add(getVarInsn);
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
            requireValue();
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
        Variable instance = null;
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
            requireValue();
            instance = var;
        }

        List<Variable> arguments = new ArrayList<>();
        for (Plan argPlan : plan.getArguments()) {
            argPlan.acceptVisitor(this);
            requireValue();
            arguments.add(var);
        }

        InvokeInstruction insn = new InvokeInstruction();
        insn.setInstance(instance);
        insn.getArguments().addAll(arguments);
        insn.setMethod(new MethodReference(plan.getClassName(), MethodDescriptor.parse(
                plan.getMethodName() + plan.getMethodDesc())));
        insn.setType(plan.getInstance() != null ? InvocationType.VIRTUAL : InvocationType.SPECIAL);
        if (insn.getMethod().getReturnType() != ValueType.VOID) {
            var = program.createVariable();
            insn.setReceiver(var);
        } else {
            var = null;
        }
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(ConstructionPlan plan) {
        Variable result = program.createVariable();
        ConstructInstruction constructInsn = new ConstructInstruction();
        constructInsn.setReceiver(result);
        constructInsn.setType(plan.getClassName());
        block.getInstructions().add(constructInsn);

        List<Variable> arguments = new ArrayList<>();
        for (Plan argPlan : plan.getArguments()) {
            argPlan.acceptVisitor(this);
            requireValue();
            arguments.add(var);
        }
        InvokeInstruction insn = new InvokeInstruction();
        insn.setInstance(result);
        insn.setMethod(new MethodReference(plan.getClassName(), MethodDescriptor.parse(
                "<init>" + plan.getMethodDesc())));
        insn.setType(InvocationType.SPECIAL);
        insn.getArguments().addAll(arguments);

        block.getInstructions().add(insn);
        var = result;
    }

    @Override
    public void visit(ConditionalPlan plan) {
        plan.getCondition().acceptVisitor(this);
        valueToBranching();
        BranchingConsumer branching = this.branching;
        this.branching = null;

        BasicBlock thenBlock = program.createBasicBlock();
        BasicBlock elseBlock = program.createBasicBlock();
        BasicBlock joint = program.createBasicBlock();

        block = thenBlock;
        plan.getConsequent().acceptVisitor(this);
        requireValue();
        JumpInstruction jump = new JumpInstruction();
        jump.setTarget(joint);
        block.getInstructions().add(jump);
        Variable trueVar = var;

        block = elseBlock;
        plan.getAlternative().acceptVisitor(this);
        requireValue();
        jump = new JumpInstruction();
        jump.setTarget(joint);
        block.getInstructions().add(jump);
        Variable falseVar = var;

        branching.setThen(thenBlock);
        branching.setElse(elseBlock);

        block = joint;
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
        phi.setReceiver(var);
        block.getPhis().add(phi);
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

        block = program.createBasicBlock();

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
        phi.setReceiver(var);
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
