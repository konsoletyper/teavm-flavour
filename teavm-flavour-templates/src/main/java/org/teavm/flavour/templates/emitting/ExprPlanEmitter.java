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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.flavour.expr.Location;
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
import org.teavm.flavour.expr.plan.LambdaPlan;
import org.teavm.flavour.expr.plan.LogicalBinaryPlan;
import org.teavm.flavour.expr.plan.NegatePlan;
import org.teavm.flavour.expr.plan.NotPlan;
import org.teavm.flavour.expr.plan.Plan;
import org.teavm.flavour.expr.plan.PlanVisitor;
import org.teavm.flavour.expr.plan.ReferenceEqualityPlan;
import org.teavm.flavour.expr.plan.ReferenceEqualityPlanType;
import org.teavm.flavour.expr.plan.ThisPlan;
import org.teavm.flavour.expr.plan.VariablePlan;
import org.teavm.flavour.templates.Templates;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ForkEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.BinaryBranchingCondition;
import org.teavm.model.instructions.BinaryBranchingInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.IntegerSubtype;
import org.teavm.model.instructions.NumericOperandType;

/**
 *
 * @author Alexey Andreev
 */
class ExprPlanEmitter implements PlanVisitor {
    private EmitContext context;
    String thisClassName;
    ProgramEmitter pe;
    ValueEmitter var;
    private ForkEmitter branching;
    ValueEmitter thisVar;
    Set<String> innerClosure = new HashSet<>();
    List<String> innerClosureList = new ArrayList<>();
    private Map<String, BoundVariableEmitter> boundVars = new HashMap<>();
    private Map<String, ValueType> boundVarTypes = new HashMap<>();

    public ExprPlanEmitter(EmitContext context) {
        this.context = context;
    }

    @Override
    public void visit(ConstantPlan plan) {
        context.location(pe, plan.getLocation());
        Object value = plan.getValue();
        if (value == null) {
            var = pe.constantNull();
        } else if (value instanceof Boolean) {
            var = pe.constant((Boolean)value ? 1 : 0);
        } else if (value instanceof Byte) {
            var = pe.constant(((Byte)value).intValue());
        } else if (value instanceof Short) {
            var = pe.constant(((Short)value).intValue());
        } else if (value instanceof Character) {
            var = pe.constant(((Character)value).charValue());
        } else if (value instanceof Integer) {
            var = pe.constant((Integer)value);
        } else if (value instanceof Long) {
            var = pe.constant((Long)value);
        } else if (value instanceof Float) {
            var = pe.constant((Float)value);
        } else if (value instanceof Double) {
            var = pe.constant((Double)value);
        } else if (value instanceof String) {
            var = pe.constant((String)value);
        }
    }

    @Override
    public void visit(VariablePlan plan) {
        context.location(pe, plan.getLocation());
        emitVariable(plan.getName());
    }

    @Override
    public void visit(ThisPlan plan) {
        context.location(pe, plan.getLocation());
        emitVariable("this");
    }

    private void emitVariable(String name) {
        if (boundVars.containsKey(name)) {
            boundVars.get(name).emit();
            if (innerClosure.add(name)) {
                innerClosureList.add(name);
            }
            return;
        }

        EmittedVariable emitVar = context.getVariable(name);

        String lastClass = thisClassName;
        var = thisVar;
        int bottom = context.classStack.size() - 1;
        for (int i = context.classStack.size() - 1; i >= bottom; --i) {
            var = var.getField(new FieldReference(lastClass, "this$owner"),
                    ValueType.object(context.classStack.get(i)));
            lastClass = context.classStack.get(i);
        }
        var = var.getField(new FieldReference(lastClass, "cache$" + name), emitVar.type);
    }

    @Override
    public void visit(BinaryPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        requireValue();
        ValueEmitter first = var;
        plan.getSecondOperand().acceptVisitor(this);
        requireValue();
        ValueEmitter second = var;

        BinaryOperation op = mapBinary(plan.getType());
        if (op != null) {
            context.location(pe, plan.getLocation());
            var = first.binary(op, mapArithmetic(plan.getValueType()), second);
            return;
        }
        BinaryBranchingCondition binaryCond = mapBinaryCondition(plan.getType());
        if (binaryCond != null) {
            context.location(pe, plan.getLocation());
            branching = first.fork(binaryCond, second);
            return;
        }
        context.location(pe, plan.getLocation());
        branching = first.compare(mapArithmetic(plan.getValueType()), second).fork(mapCondition(plan.getType()));
    }

    @Override
    public void visit(NegatePlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.neg(mapArithmetic(plan.getValueType()));
    }

    @Override
    public void visit(ReferenceEqualityPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        requireValue();
        ValueEmitter first = var;
        plan.getSecondOperand().acceptVisitor(this);
        requireValue();
        ValueEmitter second = var;

        context.location(pe, plan.getLocation());
        branching = first.fork(mapBinaryCondition(plan.getType()), second);
    }

    @Override
    public void visit(LogicalBinaryPlan plan) {
        context.location(pe, plan.getLocation());
        switch (plan.getType()) {
            case AND: {
                plan.getFirstOperand().acceptVisitor(this);
                valueToBranching();
                ForkEmitter first = branching;
                BasicBlock block = pe.createBlock();
                plan.getSecondOperand().acceptVisitor(this);
                valueToBranching();
                ForkEmitter second = branching;
                context.location(pe, plan.getLocation());
                branching = first.and(block, second);
                break;
            }
            case OR: {
                plan.getFirstOperand().acceptVisitor(this);
                valueToBranching();
                ForkEmitter first = branching;
                BasicBlock block = pe.createBlock();
                plan.getSecondOperand().acceptVisitor(this);
                valueToBranching();
                ForkEmitter second = branching;
                context.location(pe, plan.getLocation());
                branching = first.or(block, second);
                break;
            }
        }
    }

    @Override
    public void visit(NotPlan plan) {
        context.location(pe, plan.getLocation());
        plan.getOperand().acceptVisitor(this);
        valueToBranching();
        context.location(pe, plan.getLocation());
        branching = branching.not();
    }

    @Override
    public void visit(CastPlan plan) {
        context.location(pe, plan.getLocation());
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.cast(ValueType.parse(plan.getTargetType()));
    }

    @Override
    public void visit(ArithmeticCastPlan plan) {
        context.location(pe, plan.getLocation());
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.cast(mapArithmetic(plan.getSourceType()), mapArithmetic(plan.getTargetType()));
    }

    @Override
    public void visit(CastFromIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.fromInteger(mapInteger(plan.getType()));
    }

    @Override
    public void visit(CastToIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.toInteger(mapInteger(plan.getType()));
    }

    @Override
    public void visit(GetArrayElementPlan plan) {
        plan.getArray().acceptVisitor(this);
        requireValue();
        ValueEmitter array = var;
        plan.getIndex().acceptVisitor(this);
        requireValue();
        ValueEmitter index = var;
        context.location(pe, plan.getLocation());
        var = array.getElement(index);
    }

    @Override
    public void visit(ArrayLengthPlan plan) {
        plan.getArray().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.arrayLength();
    }

    @Override
    public void visit(FieldPlan plan) {
        FieldReference field = new FieldReference(plan.getClassName(), plan.getFieldName());
        ValueType type = ValueType.parse(plan.getFieldDesc());

        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
            requireValue();
            context.location(pe, plan.getLocation());
            var = var.getField(field, type);
        } else {
            context.location(pe, plan.getLocation());
            var = pe.getField(field, type);
        }
    }

    @Override
    public void visit(InstanceOfPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.instanceOf(ValueType.parse(plan.getClassName()));
    }

    @Override
    public void visit(InvocationPlan plan) {
        ValueEmitter instance = null;
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
            requireValue();
            instance = var;
        }

        ValueEmitter[] arguments = new ValueEmitter[plan.getArguments().size()];
        for (int i = 0; i < plan.getArguments().size(); ++i) {
            plan.getArguments().get(i).acceptVisitor(this);
            requireValue();
            arguments[i] = var;
        }

        MethodReference method = new MethodReference(plan.getClassName(), MethodDescriptor.parse(
                plan.getMethodName() + plan.getMethodDesc()));

        if (instance != null) {
            context.location(pe, plan.getLocation());
            var = instance.invokeVirtual(method, arguments);
        } else {
            context.location(pe, plan.getLocation());
            var = pe.invoke(method, arguments);
        }
    }

    @Override
    public void visit(ConstructionPlan plan) {
        MethodReference ctor = new MethodReference(plan.getClassName(), MethodDescriptor.parse(
                "<init>" + plan.getMethodDesc()));
        ValueEmitter[] arguments = new ValueEmitter[plan.getArguments().size()];
        for (int i = 0; i < plan.getArguments().size(); ++i) {
            plan.getArguments().get(i).acceptVisitor(this);
            requireValue();
            arguments[i] = var;
        }
        context.location(pe, plan.getLocation());
        var = pe.construct(ctor, arguments);
    }

    @Override
    public void visit(ConditionalPlan plan) {
        plan.getCondition().acceptVisitor(this);
        valueToBranching();
        ForkEmitter branching = this.branching;
        this.branching = null;
        BasicBlock joint = pe.getProgram().createBasicBlock();

        BasicBlock thenBlock = pe.createBlock();
        plan.getConsequent().acceptVisitor(this);
        requireValue();
        pe.jump(joint);
        ValueEmitter trueVar = var;

        BasicBlock elseBlock = pe.createBlock();
        plan.getAlternative().acceptVisitor(this);
        requireValue();
        pe.jump(joint);
        ValueEmitter falseVar = var;

        branching.setThen(thenBlock);
        branching.setElse(elseBlock);

        pe.setBlock(joint);
        var = trueVar.join(thenBlock, falseVar, elseBlock);
    }

    @Override
    public void visit(LambdaPlan plan) {
        emit(plan, false);
    }

    public void emit(LambdaPlan plan, boolean updateTemplates) {
        Map<String, ClosureEmitter> innerBoundVars = new HashMap<>();
        for (String boundVar : boundVars.keySet()) {
            innerBoundVars.put(boundVar, new ClosureEmitter(boundVar, boundVarTypes.get(boundVar)));
        }

        ExprPlanEmitter innerEmitter = new ExprPlanEmitter(context);
        String lambdaClass = innerEmitter.emitLambdaClass(plan.getClassName(), plan.getMethodName(),
                plan.getMethodDesc(), plan.getBody(), plan.getBoundVars(), innerBoundVars, updateTemplates,
                plan.getLocation());

        String ownerCls = context.classStack.get(context.classStack.size() - 1);
        FieldReference ownerField = new FieldReference(thisClassName, "this$owner");
        ValueType ownerType = ValueType.object(ownerCls);
        List<ValueType> ctorArgTypes = new ArrayList<>();
        List<ValueEmitter> ctorArgs = new ArrayList<>();

        ctorArgTypes.add(ownerType);
        ctorArgs.add(thisVar.getField(ownerField, ownerType));
        Set<String> localBoundVars = new HashSet<>(plan.getBoundVars());
        for (int i = 0; i < innerEmitter.innerClosureList.size(); ++i) {
            String closedVar = innerEmitter.innerClosureList.get(i);
            if (!localBoundVars.contains(closedVar)) {
                boundVars.get(closedVar).emit();
                ctorArgTypes.add(boundVarTypes.get(closedVar));
                ctorArgs.add(var);
            }
        }
        ctorArgTypes.add(ValueType.VOID);
        MethodReference ctor = new MethodReference(lambdaClass, "<init>", ctorArgTypes.toArray(new ValueType[0]));

        context.location(pe, plan.getLocation());
        var = pe.construct(ctor, ctorArgs.toArray(new ValueEmitter[0]));
    }

    private String emitLambdaClass(String className, String methodName, String methodDesc, Plan body,
            List<String> boundVarList, Map<String, ClosureEmitter> outerBoundVars, boolean updateTemplates,
            Location location) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(className);

        MethodHolder workerMethod = new MethodHolder(MethodDescriptor.parse(methodName + methodDesc));
        workerMethod.setLevel(AccessLevel.PUBLIC);
        pe = ProgramEmitter.create(workerMethod);
        thisVar = pe.newVar();
        for (ClosureEmitter outerClosure : outerBoundVars.values()) {
            boundVarTypes.put(outerClosure.name, outerClosure.type);
        }
        boundVars.putAll(outerBoundVars);
        for (int i = 0; i < workerMethod.parameterCount(); ++i) {
            String varName = boundVarList.get(i);
            if (!varName.isEmpty()) {
                boundVars.put(varName, new ParamEmitter(i + 1));
                pe.newVar();
                boundVarTypes.put(varName, workerMethod.parameterType(i));
            }
        }

        thisClassName = cls.getName();
        body.acceptVisitor(this);
        requireValue();

        if (updateTemplates) {
            context.location(pe, location);
            pe.invoke(new MethodReference(Templates.class, "update", void.class));
        }

        if (workerMethod.getResultType() != ValueType.VOID) {
            var.returnValue();
        } else {
            pe.exit();
        }

        /*if (updateTemplates) {
            BasicBlock catchBlock = program.createBasicBlock();
            Variable exceptionVar = program.createVariable();
            TryCatchBlock tryCatch = new TryCatchBlock();
            tryCatch.setHandler(catchBlock);
            tryCatch.setExceptionVariable(exceptionVar);
            block.getTryCatchBlocks().add(tryCatch);
            block = catchBlock;

            emitSetRoot(emitNull());
            RaiseInstruction rethrow = new RaiseInstruction();
            rethrow.setException(exceptionVar);
            block.getInstructions().add(rethrow);
        }*/

        cls.addMethod(workerMethod);

        List<String> closedVars = new ArrayList<>(innerClosureList.size());
        Set<String> boundVars = new HashSet<>(boundVarList);
        for (String var : innerClosureList) {
            if (!boundVars.contains(var)) {
                closedVars.add(var);
            }
        }
        emitLambdaConstructor(cls, closedVars, location);

        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void emitLambdaConstructor(ClassHolder cls, List<String> closedVars, Location location) {
        String ownerCls = context.classStack.get(context.classStack.size() - 1);

        List<ValueType> ctorArgTypes = new ArrayList<>();
        ctorArgTypes.add(ValueType.object(ownerCls));
        for (int i = 0; i < closedVars.size(); ++i) {
            String closedVar = closedVars.get(i);
            ctorArgTypes.add(boundVarTypes.get(closedVar));
        }
        ctorArgTypes.add(ValueType.VOID);

        MethodHolder ctor = new MethodHolder("<init>", ctorArgTypes.toArray(new ValueType[0]));
        ctor.setLevel(AccessLevel.PUBLIC);
        pe = ProgramEmitter.create(ctor);
        thisVar = pe.newVar();
        ValueEmitter ownerVar = pe.newVar();

        context.location(pe, location);
        thisVar.invokeSpecial(new MethodReference(Object.class, "<init>", void.class));
        FieldHolder ownerField = new FieldHolder("this$owner");
        ownerField.setLevel(AccessLevel.PUBLIC);
        ownerField.setType(ValueType.object(ownerCls));
        cls.addField(ownerField);
        thisVar.setField(ownerField.getReference(), ownerField.getType(), ownerVar);
        for (int i = 0; i < closedVars.size(); ++i) {
            String closedVar = closedVars.get(i);
            FieldHolder closureField = new FieldHolder("closure$" + closedVar);
            closureField.setLevel(AccessLevel.PUBLIC);
            closureField.setType(boundVarTypes.get(closedVar));
            cls.addField(closureField);
            thisVar.setField(closureField.getReference(), closureField.getType(), pe.newVar());
        }
        pe.exit();

        cls.addMethod(ctor);
    }

    void valueToBranching() {
        if (branching != null) {
            return;
        }

        branching = var.fork(BinaryBranchingCondition.NOT_EQUAL, pe.constant(0));
    }

    void requireValue() {
        if (branching == null) {
            return;
        }

        ForkEmitter branching = this.branching;
        this.branching = null;
        BasicBlock joint = pe.getProgram().createBasicBlock();

        BasicBlock thenBlock = pe.createBlock();
        ValueEmitter trueVar = pe.constant(1);
        pe.jump(joint);

        BasicBlock elseBlock = pe.createBlock();
        ValueEmitter falseVar = pe.constant(0);
        pe.jump(joint);

        branching.setThen(thenBlock);
        branching.setElse(elseBlock);
        pe.setBlock(joint);
        var = trueVar.join(thenBlock, falseVar, elseBlock);
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

    interface BoundVariableEmitter {
        void emit();
    }

    class ParamEmitter implements BoundVariableEmitter {
        private int index;

        public ParamEmitter(int index) {
            this.index = index;
        }

        @Override
        public void emit() {
            var = pe.var(pe.getProgram().variableAt(index));
        }
    }

    class ClosureEmitter implements BoundVariableEmitter {
        final String name;
        final ValueType type;

        public ClosureEmitter(String name, ValueType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public void emit() {
            var = thisVar.getField(new FieldReference(thisClassName, "closure$" + name), type);
        }
    }
}
