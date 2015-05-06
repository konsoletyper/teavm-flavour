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
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
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
import org.teavm.model.instructions.PutFieldInstruction;
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
    Set<String> innerClosure = new HashSet<>();
    List<String> innerClosureList = new ArrayList<>();
    private Map<String, BoundVariableEmitter> boundVars = new HashMap<>();
    private Map<String, ValueType> boundVarTypes = new HashMap<>();

    public ExprPlanEmitter(EmitContext context) {
        this.context = context;
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
        emitVariable(plan.getName());
    }

    @Override
    public void visit(ThisPlan plan) {
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
        getVarInsn.setField(new FieldReference(lastClass, "cache$" + name));
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

    @Override
    public void visit(LambdaPlan plan) {
        Map<String, ClosureEmitter> innerBoundVars = new HashMap<>();
        for (String boundVar : boundVars.keySet()) {
            innerBoundVars.put(boundVar, new ClosureEmitter(boundVar, boundVarTypes.get(boundVar)));
        }

        ExprPlanEmitter innerEmitter = new ExprPlanEmitter(context);
        String lambdaClass = innerEmitter.emitLambdaClass(plan.getClassName(), plan.getMethodName(),
                plan.getMethodDesc(), plan.getBody(), plan.getBoundVars(), innerBoundVars);

        String ownerCls = context.classStack.get(context.classStack.size() - 1);

        Variable lambda = program.createVariable();
        ConstructInstruction constructLambda = new ConstructInstruction();
        constructLambda.setType(lambdaClass);
        constructLambda.setReceiver(lambda);
        block.getInstructions().add(constructLambda);

        List<ValueType> ctorArgTypes = new ArrayList<>();
        List<Variable> ctorArgs = new ArrayList<>();

        ctorArgTypes.add(ValueType.object(ownerCls));
        Variable ownerVar = program.createVariable();
        GetFieldInstruction getOwner = new GetFieldInstruction();
        getOwner.setField(new FieldReference(thisClassName, "this$owner"));
        getOwner.setFieldType(ValueType.object(ownerCls));
        getOwner.setInstance(thisVar);
        getOwner.setReceiver(ownerVar);
        block.getInstructions().add(getOwner);
        ctorArgs.add(ownerVar);

        for (int i = 0; i < innerEmitter.innerClosureList.size(); ++i) {
            String closedVar = innerEmitter.innerClosureList.get(i);
            boundVars.get(closedVar).emit();
            ctorArgTypes.add(boundVarTypes.get(closedVar));
            ctorArgs.add(var);
        }
        ctorArgTypes.add(ValueType.VOID);

        InvokeInstruction initLambda = new InvokeInstruction();
        initLambda.setInstance(lambda);
        initLambda.setType(InvocationType.SPECIAL);
        initLambda.setMethod(new MethodReference(lambdaClass, "<init>", ctorArgTypes.toArray(new ValueType[0])));
        initLambda.getArguments().addAll(ctorArgs);
        block.getInstructions().add(initLambda);

        var = lambda;
    }

    private String emitLambdaClass(String className, String methodName, String methodDesc, Plan body,
            List<String> boundVarList, Map<String, ClosureEmitter> outerBoundVars) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(className);

        MethodHolder workerMethod = new MethodHolder(MethodDescriptor.parse(methodName + methodDesc));
        workerMethod.setLevel(AccessLevel.PUBLIC);
        program = new Program();
        thisVar = program.createVariable();
        for (ClosureEmitter outerClosure : outerBoundVars.values()) {
            boundVarTypes.put(outerClosure.name, outerClosure.type);
        }
        boundVars.putAll(outerBoundVars);
        for (int i = 0; i < workerMethod.parameterCount(); ++i) {
            String varName = boundVarList.get(i);
            if (!varName.isEmpty()) {
                boundVars.put(varName, new ParamEmitter(i + 1));
                boundVarTypes.put(varName, workerMethod.parameterType(i));
            }
        }
        block = program.createBasicBlock();
        thisClassName = cls.getName();
        body.acceptVisitor(this);
        requireValue();
        ExitInstruction exit = new ExitInstruction();
        if (workerMethod.getResultType() != ValueType.VOID) {
            exit.setValueToReturn(var);
        }
        block.getInstructions().add(exit);
        workerMethod.setProgram(program);
        cls.addMethod(workerMethod);

        emitLambdaConstructor(cls);

        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void emitLambdaConstructor(ClassHolder cls) {
        String ownerCls = context.classStack.get(context.classStack.size() - 1);

        List<ValueType> ctorArgTypes = new ArrayList<>();
        ctorArgTypes.add(ValueType.object(ownerCls));
        for (int i = 0; i < innerClosureList.size(); ++i) {
            String closedVar = innerClosureList.get(i);
            ctorArgTypes.add(boundVarTypes.get(closedVar));
        }
        ctorArgTypes.add(ValueType.VOID);

        MethodHolder ctor = new MethodHolder("<init>", ctorArgTypes.toArray(new ValueType[0]));
        ctor.setLevel(AccessLevel.PUBLIC);
        program = new Program();
        block = program.createBasicBlock();
        thisVar = program.createVariable();

        InvokeInstruction initSuper = new InvokeInstruction();
        initSuper.setInstance(thisVar);
        initSuper.setMethod(new MethodReference(Object.class, "<init>", void.class));
        initSuper.setType(InvocationType.SPECIAL);
        block.getInstructions().add(initSuper);

        FieldHolder ownerField = new FieldHolder("this$owner");
        ownerField.setLevel(AccessLevel.PUBLIC);
        ownerField.setType(ValueType.object(ownerCls));
        cls.addField(ownerField);
        Variable ownerVar = program.createVariable();
        PutFieldInstruction setOwner = new PutFieldInstruction();
        setOwner.setField(ownerField.getReference());
        setOwner.setFieldType(ownerField.getType());
        setOwner.setInstance(thisVar);
        setOwner.setValue(ownerVar);
        block.getInstructions().add(setOwner);

        for (int i = 0; i < innerClosureList.size(); ++i) {
            String closedVar = innerClosureList.get(i);
            FieldHolder closureField = new FieldHolder("closure$" + closedVar);
            closureField.setLevel(AccessLevel.PUBLIC);
            closureField.setType(boundVarTypes.get(closedVar));
            cls.addField(closureField);
            Variable closureVar = program.createVariable();
            PutFieldInstruction setClosure = new PutFieldInstruction();
            setClosure.setField(closureField.getReference());
            setClosure.setFieldType(closureField.getType());
            setClosure.setInstance(thisVar);
            setClosure.setValue(closureVar);
            block.getInstructions().add(setClosure);
        }

        block.getInstructions().add(new ExitInstruction());

        ctor.setProgram(program);
        cls.addMethod(ctor);
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
            var = program.variableAt(index);
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
            var = program.createVariable();
            GetFieldInstruction insn = new GetFieldInstruction();
            insn.setInstance(thisVar);
            insn.setField(new FieldReference(thisClassName, "closure$" + name));
            insn.setFieldType(type);
            insn.setReceiver(var);
            block.getInstructions().add(insn);
        }
    }
}
