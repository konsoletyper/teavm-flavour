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
import org.teavm.model.emit.ComputationEmitter;
import org.teavm.model.emit.ConditionEmitter;
import org.teavm.model.emit.PhiEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.model.instructions.IntegerSubtype;

/**
 *
 * @author Alexey Andreev
 */
class ExprPlanEmitter implements PlanVisitor {
    private EmitContext context;
    String thisClassName;
    ProgramEmitter pe;
    ValueEmitter var;
    private ConditionEmitter branching;
    ValueEmitter thisVar;
    Set<String> innerClosure = new HashSet<>();
    List<String> innerClosureList = new ArrayList<>();
    private Map<String, ComputationEmitter> boundVars = new HashMap<>();
    private Map<String, ValueType> boundVarTypes = new HashMap<>();

    public ExprPlanEmitter(EmitContext context) {
        this.context = context;
    }

    @Override
    public void visit(ConstantPlan plan) {
        context.location(pe, plan.getLocation());
        Object value = plan.getValue();
        if (value == null) {
            var = pe.constantNull(ValueType.parse(Object.class));
        } else if (value instanceof Boolean) {
            var = pe.constant((Boolean) value ? 1 : 0);
        } else if (value instanceof Byte) {
            var = pe.constant(((Byte) value).intValue());
        } else if (value instanceof Short) {
            var = pe.constant(((Short) value).intValue());
        } else if (value instanceof Character) {
            var = pe.constant(((Character) value).charValue());
        } else if (value instanceof Integer) {
            var = pe.constant((Integer) value);
        } else if (value instanceof Long) {
            var = pe.constant((Long) value);
        } else if (value instanceof Float) {
            var = pe.constant((Float) value);
        } else if (value instanceof Double) {
            var = pe.constant((Double) value);
        } else if (value instanceof String) {
            var = pe.constant((String) value);
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

        var = thisVar;
        int bottom = context.classStack.size() - 1;
        for (int i = context.classStack.size() - 1; i >= bottom; --i) {
            var = var.getField("this$owner", ValueType.object(context.classStack.get(i)));
        }
        var = var.getField("cache$" + name, emitVar.type);
    }

    @Override
    public void visit(BinaryPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        requireValue();
        ValueEmitter first = var;
        plan.getSecondOperand().acceptVisitor(this);
        requireValue();
        ValueEmitter second = var;

        switch (plan.getType()) {
            case ADD:
                var = first.add(second);
                break;
            case SUBTRACT:
                var = first.sub(second);
                break;
            case MULTIPLY:
                var = first.mul(second);
                break;
            case DIVIDE:
                var = first.div(second);
                break;
            case REMAINDER:
                var = first.rem(second);
                break;
            case EQUAL:
                branching = first.isEqualTo(second);
                break;
            case NOT_EQUAL:
                branching = first.isNotEqualTo(second);
                break;
            case GREATER:
                branching = first.isGreaterThan(second);
                break;
            case GREATER_OR_EQUAL:
                branching = first.isGreaterOrEqualTo(second);
                break;
            case LESS:
                branching = first.isLessThan(second);
                break;
            case LESS_OR_EQUAL:
                branching = first.isLessOrEqualTo(second);
                break;
        }
    }

    @Override
    public void visit(NegatePlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.neg();
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
        switch (plan.getType()) {
            case EQUAL:
                branching = first.isSame(second);
                break;
            case NOT_EQUAL:
                branching = first.isNotSame(second);
                break;
        }
    }

    @Override
    public void visit(LogicalBinaryPlan plan) {
        context.location(pe, plan.getLocation());
        switch (plan.getType()) {
            case AND:
                plan.getFirstOperand().acceptVisitor(this);
                valueToBranching();
                branching.and(() -> {
                    plan.getSecondOperand().acceptVisitor(this);
                    valueToBranching();
                    return branching;
                });
                break;
            case OR:
                plan.getFirstOperand().acceptVisitor(this);
                valueToBranching();
                branching.or(() -> {
                    plan.getSecondOperand().acceptVisitor(this);
                    valueToBranching();
                    return branching;
                });
                break;
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
        var = var.cast(mapArithmetic(plan.getTargetType()));
    }

    @Override
    public void visit(CastFromIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.castFromInteger(mapInteger(plan.getType()));
    }

    @Override
    public void visit(CastToIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        requireValue();
        context.location(pe, plan.getLocation());
        var = var.cast(ValueType.INTEGER);
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
            var = var.cast(ValueType.object(field.getClassName())).getField(field.getFieldName(), type);
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
            instance = var.cast(ValueType.object(plan.getClassName()));
        }

        MethodReference method = new MethodReference(plan.getClassName(), MethodDescriptor.parse(
                plan.getMethodName() + plan.getMethodDesc()));
        ValueEmitter[] arguments = new ValueEmitter[plan.getArguments().size()];
        for (int i = 0; i < plan.getArguments().size(); ++i) {
            plan.getArguments().get(i).acceptVisitor(this);
            requireValue();
            arguments[i] = var.cast(method.parameterType(i));
        }

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
            arguments[i] = var.cast(ctor.parameterType(i));
        }
        context.location(pe, plan.getLocation());
        var = pe.construct(plan.getClassName(), arguments);
    }

    @Override
    public void visit(ConditionalPlan plan) {
        plan.getCondition().acceptVisitor(this);
        valueToBranching();

        BasicBlock join = pe.prepareBlock();
        PhiEmitter result = pe.phi(ValueType.object("java.lang.Object"), join);
        pe.when(branching)
                .thenDo(() -> {
                    plan.getConsequent().acceptVisitor(this);
                    requireValue();
                    var.propagateTo(result);
                    pe.jump(join);
                })
                .elseDo(() -> {
                    plan.getAlternative().acceptVisitor(this);
                    requireValue();
                    var.propagateTo(result);
                    pe.jump(join);
                });

        pe.enter(join);
        var = result.getValue();
    }

    @Override
    public void visit(LambdaPlan plan) {
        emit(plan, false);
    }

    public void emit(LambdaPlan plan, boolean updateTemplates) {
        Map<String, ComputationEmitter> innerBoundVars = new HashMap<>();
        for (String boundVar : boundVars.keySet()) {
            innerBoundVars.put(boundVar, () -> thisVar.getField("closure$" + boundVar, boundVarTypes.get(boundVar)));
        }

        ExprPlanEmitter innerEmitter = new ExprPlanEmitter(context);
        String lambdaClass = innerEmitter.emitLambdaClass(plan.getClassName(), plan.getMethodName(),
                plan.getMethodDesc(), plan.getBody(), plan.getBoundVars(), innerBoundVars, boundVarTypes,
                updateTemplates, plan.getLocation());

        String ownerCls = context.classStack.get(context.classStack.size() - 1);
        FieldReference ownerField = new FieldReference(thisClassName, "this$owner");
        ValueType ownerType = ValueType.object(ownerCls);
        List<ValueType> ctorArgTypes = new ArrayList<>();
        List<ValueEmitter> ctorArgs = new ArrayList<>();

        ctorArgs.add(thisVar.getField(ownerField.getFieldName(), ownerType));
        Set<String> localBoundVars = new HashSet<>(plan.getBoundVars());
        for (int i = 0; i < innerEmitter.innerClosureList.size(); ++i) {
            String closedVar = innerEmitter.innerClosureList.get(i);
            if (!localBoundVars.contains(closedVar)) {
                ctorArgs.add(boundVars.get(closedVar).emit().cast(boundVarTypes.get(closedVar)));
            }
        }
        ctorArgTypes.add(ValueType.VOID);
        context.location(pe, plan.getLocation());
        var = pe.construct(lambdaClass, ctorArgs.toArray(new ValueEmitter[0]));
    }

    private String emitLambdaClass(String className, String methodName, String methodDesc, Plan body,
            List<String> boundVarList, Map<String, ComputationEmitter> outerBoundVars,
            Map<String, ValueType> outerBoundTypes, boolean updateTemplates, Location location) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(className);

        MethodHolder workerMethod = new MethodHolder(MethodDescriptor.parse(methodName + methodDesc));
        workerMethod.setLevel(AccessLevel.PUBLIC);
        pe = ProgramEmitter.create(workerMethod, context.dependencyAgent.getClassSource());
        thisVar = pe.newVar(cls);
        for (String outerClosure : outerBoundVars.keySet()) {
            boundVarTypes.put(outerClosure, outerBoundTypes.get(outerClosure));
        }
        boundVars.putAll(outerBoundVars);
        for (int i = 0; i < workerMethod.parameterCount(); ++i) {
            String varName = boundVarList.get(i);
            if (!varName.isEmpty()) {
                ValueType paramType = workerMethod.parameterType(i);
                int varIndex = i + 1;
                boundVars.put(varName, () -> pe.var(varIndex, paramType));
                boundVarTypes.put(varName, paramType);
            }
        }

        thisClassName = cls.getName();
        body.acceptVisitor(this);
        requireValue();

        if (updateTemplates) {
            context.location(pe, location);
            pe.invoke(Templates.class, "update");
        }

        if (workerMethod.getResultType() != ValueType.VOID) {
            var.returnValue();
        } else {
            pe.exit();
        }

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
        pe = ProgramEmitter.create(ctor, context.dependencyAgent.getClassSource());
        thisVar = pe.var(0, cls);
        ValueEmitter ownerVar = pe.var(1, ValueType.object(ownerCls));

        context.location(pe, location);
        thisVar.invokeSpecial(new MethodReference(Object.class, "<init>", void.class));
        FieldHolder ownerField = new FieldHolder("this$owner");
        ownerField.setLevel(AccessLevel.PUBLIC);
        ownerField.setType(ValueType.object(ownerCls));
        cls.addField(ownerField);
        thisVar.setField(ownerField.getName(), ownerVar);
        for (int i = 0; i < closedVars.size(); ++i) {
            String closedVar = closedVars.get(i);
            FieldHolder closureField = new FieldHolder("closure$" + closedVar);
            closureField.setLevel(AccessLevel.PUBLIC);
            closureField.setType(boundVarTypes.get(closedVar));
            cls.addField(closureField);
            thisVar.setField(closureField.getName(), pe.var(2 + i, closureField.getType()));
        }
        pe.exit();

        cls.addMethod(ctor);
    }

    void valueToBranching() {
        if (branching != null) {
            return;
        }

        branching = var.isTrue();
    }

    void requireValue() {
        if (branching == null) {
            return;
        }

        ConditionEmitter branching = this.branching;
        this.branching = null;

        BasicBlock join = pe.prepareBlock();
        PhiEmitter result = pe.phi(ValueType.INTEGER, join);
        pe.when(branching)
                .thenDo(() -> {
                    pe.constant(1).propagateTo(result);
                    pe.jump(join);
                })
                .elseDo(() -> {
                    pe.constant(0).propagateTo(result);
                    pe.jump(join);
                });

        pe.enter(join);
        var = result.getValue();
    }

    private ValueType mapArithmetic(ArithmeticType type) {
        switch (type) {
            case INT:
                return ValueType.INTEGER;
            case LONG:
                return ValueType.LONG;
            case FLOAT:
                return ValueType.FLOAT;
            case DOUBLE:
                return ValueType.DOUBLE;
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
}
