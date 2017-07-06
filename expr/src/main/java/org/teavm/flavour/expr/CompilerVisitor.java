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
package org.teavm.flavour.expr;

import static org.teavm.flavour.expr.CompilerCommons.methodToDesc;
import static org.teavm.flavour.expr.CompilerCommons.typeToString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.flavour.expr.ast.AssignmentExpr;
import org.teavm.flavour.expr.ast.BinaryExpr;
import org.teavm.flavour.expr.ast.BinaryOperation;
import org.teavm.flavour.expr.ast.BoundVariable;
import org.teavm.flavour.expr.ast.CastExpr;
import org.teavm.flavour.expr.ast.ConstantExpr;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.ExprVisitor;
import org.teavm.flavour.expr.ast.InstanceOfExpr;
import org.teavm.flavour.expr.ast.InvocationExpr;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.ast.PropertyExpr;
import org.teavm.flavour.expr.ast.StaticInvocationExpr;
import org.teavm.flavour.expr.ast.StaticPropertyExpr;
import org.teavm.flavour.expr.ast.TernaryConditionExpr;
import org.teavm.flavour.expr.ast.ThisExpr;
import org.teavm.flavour.expr.ast.UnaryExpr;
import org.teavm.flavour.expr.ast.VariableExpr;
import org.teavm.flavour.expr.plan.ArithmeticCastPlan;
import org.teavm.flavour.expr.plan.ArithmeticType;
import org.teavm.flavour.expr.plan.ArrayConstructionPlan;
import org.teavm.flavour.expr.plan.ArrayLengthPlan;
import org.teavm.flavour.expr.plan.BinaryPlan;
import org.teavm.flavour.expr.plan.BinaryPlanType;
import org.teavm.flavour.expr.plan.CastFromIntegerPlan;
import org.teavm.flavour.expr.plan.CastPlan;
import org.teavm.flavour.expr.plan.CastToIntegerPlan;
import org.teavm.flavour.expr.plan.ConditionalPlan;
import org.teavm.flavour.expr.plan.ConstantPlan;
import org.teavm.flavour.expr.plan.ConstructionPlan;
import org.teavm.flavour.expr.plan.FieldAssignmentPlan;
import org.teavm.flavour.expr.plan.FieldPlan;
import org.teavm.flavour.expr.plan.GetArrayElementPlan;
import org.teavm.flavour.expr.plan.InstanceOfPlan;
import org.teavm.flavour.expr.plan.IntegerSubtype;
import org.teavm.flavour.expr.plan.InvocationPlan;
import org.teavm.flavour.expr.plan.LambdaPlan;
import org.teavm.flavour.expr.plan.LogicalBinaryPlan;
import org.teavm.flavour.expr.plan.LogicalBinaryPlanType;
import org.teavm.flavour.expr.plan.NegatePlan;
import org.teavm.flavour.expr.plan.NotPlan;
import org.teavm.flavour.expr.plan.Plan;
import org.teavm.flavour.expr.plan.ReferenceEqualityPlan;
import org.teavm.flavour.expr.plan.ReferenceEqualityPlanType;
import org.teavm.flavour.expr.plan.ThisPlan;
import org.teavm.flavour.expr.plan.VariablePlan;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericField;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.NullType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.PrimitiveKind;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeInferenceStatePoint;
import org.teavm.flavour.expr.type.TypeUtils;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.ValueTypeFormatter;
import org.teavm.flavour.expr.type.Variance;
import org.teavm.flavour.expr.type.meta.ClassDescriber;

class CompilerVisitor implements ExprVisitor<TypedPlan> {
    private GenericTypeNavigator navigator;
    private Scope scope;
    private Map<String, ValueType> boundVars = new HashMap<>();
    private Map<String, String> boundVarRenamings = new HashMap<>();
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private ClassResolver classResolver;
    private ValueType lambdaReturnType;
    ValueType expectedType;

    CompilerVisitor(GenericTypeNavigator navigator, ClassResolver classes, Scope scope) {
        this.navigator = navigator;
        this.classResolver = classes;
        this.scope = scope;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    @Override
    public TypedPlan visit(BinaryExpr expr) {
        Expr firstOperand = expr.getFirstOperand();
        Expr secondOperand = expr.getSecondOperand();
        expectedType = null;
        TypedPlan firstPlan = firstOperand.acceptVisitor(this);
        expectedType = null;
        secondOperand.acceptVisitor(this);
        TypedPlan secondPlan = secondOperand.acceptVisitor(this);
        switch (expr.getOperation()) {
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER: {
                ArithmeticTypeAndPlans result = getArithmeticTypeForPair(firstOperand, firstPlan,
                        secondOperand, secondPlan);
                BinaryPlan plan = new BinaryPlan(result.first.getPlan(), result.second.getPlan(),
                        getPlanType(expr.getOperation()), result.arithmeticType);
                return planWithLocation(plan, CompilerCommons.getType(result.arithmeticType), expr);
            }
            case AND:
            case OR: {
                firstPlan = ensureBooleanType(firstOperand, firstPlan);
                secondPlan = ensureBooleanType(secondOperand, secondPlan);
                LogicalBinaryPlan plan = new LogicalBinaryPlan(firstPlan.getPlan(), secondPlan.getPlan(),
                        getLogicalPlanType(expr.getOperation()));
                return planWithLocation(plan, Primitive.BOOLEAN, expr);
            }
            case EQUAL:
            case NOT_EQUAL: {
                if (CompilerCommons.classesSuitableForComparison.contains(firstPlan.getType())
                        && CompilerCommons.classesSuitableForComparison.contains(secondPlan.getType())) {
                    ArithmeticTypeAndPlans result = getArithmeticTypeForPair(firstOperand, firstPlan,
                            secondOperand, secondPlan);
                    BinaryPlan plan = new BinaryPlan(result.first.getPlan(), result.second.getPlan(),
                            getPlanType(expr.getOperation()), result.arithmeticType);
                    return planWithLocation(plan, Primitive.BOOLEAN, expr);
                } else {
                    ReferenceEqualityPlan plan = new ReferenceEqualityPlan(firstPlan.getPlan(), secondPlan.getPlan(),
                            expr.getOperation() == BinaryOperation.EQUAL ? ReferenceEqualityPlanType.EQUAL
                                    : ReferenceEqualityPlanType.NOT_EQUAL);
                    return planWithLocation(plan, Primitive.BOOLEAN, expr);
                }
            }
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL: {
                ArithmeticTypeAndPlans result = getArithmeticTypeForPair(firstOperand, firstPlan,
                        secondOperand, secondPlan);
                BinaryPlan plan = new BinaryPlan(result.first.getPlan(), result.second.getPlan(),
                        getPlanType(expr.getOperation()), result.arithmeticType);
                return planWithLocation(plan, Primitive.BOOLEAN, expr);
            }
            case GET_ELEMENT:
                return compileGetElement(expr);
            case ADD:
                return compileAdd(expr);
            default:
                throw new AssertionError();
        }
    }

    private TypedPlan compileAdd(BinaryExpr expr) {
        Expr firstOperand = expr.getFirstOperand();
        TypedPlan firstPlan = firstOperand.acceptVisitor(this);
        ValueType firstType = firstPlan.getType();
        Expr secondOperand = expr.getSecondOperand();
        TypedPlan secondPlan = secondOperand.acceptVisitor(this);
        ValueType secondType = secondPlan.getType();

        if (firstType.equals(TypeUtils.STRING_CLASS) || secondType.equals(TypeUtils.STRING_CLASS)) {
            if (firstPlan.getPlan() instanceof InvocationPlan) {
                InvocationPlan invocation = (InvocationPlan) firstPlan.getPlan();
                if (invocation.getClassName().equals("java.lang.StringBuilder")
                        && invocation.getMethodName().equals("toString")) {
                    secondPlan = convertToString(expr.getSecondOperand(), secondPlan);
                    Plan instance = invocation.getInstance();
                    InvocationPlan append = new InvocationPlan("java.lang.StringBuilder", "append",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", instance, secondPlan.getPlan());
                    invocation.setInstance(append);
                    return planWithLocation(invocation, TypeUtils.STRING_CLASS, expr);
                }
            }
            firstPlan = convertToString(expr.getFirstOperand(), firstPlan);
            secondPlan = convertToString(expr.getSecondOperand(), secondPlan);
            ConstructionPlan construction = new ConstructionPlan("java.lang.StringBuilder", "()V");
            InvocationPlan invocation = new InvocationPlan("java.lang.StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", construction, firstPlan.getPlan());
            invocation = new InvocationPlan("java.lang.StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", invocation, secondPlan.getPlan());
            invocation = new InvocationPlan("java.lang.StringBuilder", "toString", "()Ljava/lang/String;",
                    invocation);
            return planWithLocation(invocation, TypeUtils.STRING_CLASS, expr);
        } else {
            ArithmeticTypeAndPlans result = getArithmeticTypeForPair(firstOperand, firstPlan,
                    secondOperand, secondPlan);
            BinaryPlan plan = new BinaryPlan(result.first.getPlan(), result.second.getPlan(),
                    BinaryPlanType.ADD, result.arithmeticType);
            return planWithLocation(plan, CompilerCommons.getType(result.arithmeticType), expr);
        }
    }

    private TypedPlan compileGetElement(BinaryExpr expr) {
        Expr firstOperand = expr.getFirstOperand();
        TypedPlan firstPlan = firstOperand.acceptVisitor(this);
        ValueType firstType = firstPlan.getType();

        Expr secondOperand = expr.getSecondOperand();
        TypedPlan secondPlan = secondOperand.acceptVisitor(this);
        ValueType secondType = secondPlan.getType();

        if (firstType instanceof GenericArray) {
            GenericArray arrayType = (GenericArray) firstType;
            secondPlan = ensureIntType(secondOperand, secondPlan);
            GetArrayElementPlan plan = new GetArrayElementPlan(firstPlan.getPlan(), secondPlan.getPlan());
            return planWithLocation(plan, arrayType.getElementType(), expr);
        } else if (firstType instanceof PrimitiveArray) {
            PrimitiveArray arrayType = (PrimitiveArray) firstType;
            secondPlan = ensureIntType(secondOperand, secondPlan);
            GetArrayElementPlan plan = new GetArrayElementPlan(firstPlan.getPlan(), secondPlan.getPlan());
            return planWithLocation(plan, arrayType.getElementType(), expr);
        } else if (firstType instanceof GenericClass) {
            Collection<GenericClass> classes = CompilerCommons.extractClasses(firstType);
            return compileInvocation(expr, firstPlan, classes, "get", Arrays.asList(secondOperand), expectedType);
        }
        return errorAndFakeResult(expr, "Can't apply subscript operator to " + firstType + " with argument of "
                + secondType);
    }

    private TypedPlan errorAndFakeResult(Expr expr, String message) {
        error(expr, message);
        return planWithLocation(new ConstantPlan(null), NullType.INSTANCE, expr);
    }

    @Override
    public TypedPlan visit(CastExpr expr) {
        expectedType = null;
        TypedPlan result = expr.getValue().acceptVisitor(this);
        return cast(expr, result, resolveType(expr.getTargetType(), expr));
    }

    private TypedPlan cast(Expr expr, TypedPlan plan, ValueType type) {
        ValueType sourceType = plan.getType();
        plan = tryCast(expr, plan, type);
        return plan != null ? plan : errorAndFakeResult(expr, "Can't cast " + sourceType + " to " + type);
    }

    private TypedPlan tryCast(Expr expr, TypedPlan plan, ValueType targetType) {
        if (plan.getType().equals(targetType)) {
            return plan;
        }

        if (targetType instanceof Primitive) {
            if (!(plan.type instanceof Primitive)) {
                plan = unbox(plan);
                if (plan == null) {
                    return null;
                }
            }
            plan = tryCastPrimitive(plan, (Primitive) targetType);
            if (plan == null) {
                return null;
            }
            return plan;
        }
        if (plan.type instanceof Primitive) {
            plan = box(expr, plan);
        }

        if (!CompilerCommons.isSuperType(targetType, plan.type, navigator)) {
            GenericType erasure = ((GenericType) targetType).erasure();
            plan = new TypedPlan(new CastPlan(plan.plan, typeToString(erasure)), targetType);
        }

        return planWithLocation(plan.getPlan(), targetType, expr);
    }

    @Override
    public TypedPlan visit(InstanceOfExpr expr) {
        expectedType = null;
        expr.setCheckedType((GenericType) resolveType(expr.getCheckedType(), expr));
        Expr value = expr.getValue();
        TypedPlan valuePlan = value.acceptVisitor(this);
        GenericType checkedType = expr.getCheckedType();

        ValueType sourceType = valuePlan.getType();
        if (!(sourceType instanceof GenericClass)) {
            error(expr, "Can't check against " + checkedType);
            return planWithLocation(new ConstantPlan(false), Primitive.BOOLEAN, expr);
        }

        GenericType erasure = checkedType.erasure();
        InstanceOfPlan plan = new InstanceOfPlan(valuePlan.getPlan(), typeToString(erasure));
        return planWithLocation(plan, Primitive.BOOLEAN, expr);
    }

    @Override
    public TypedPlan visit(InvocationExpr expr) {
        ValueType expectedType = this.expectedType;

        TypedPlan instance;
        if (expr.getInstance() != null) {
            this.expectedType = null;
            instance = expr.getInstance().acceptVisitor(this);
        } else {
            instance = new TypedPlan(new ThisPlan(), scope.variableType("this"));
        }

        if (instance.type instanceof Primitive) {
            instance = box(expr.getInstance(), instance);
        }
        Collection<GenericClass> classes = CompilerCommons.extractClasses(instance.type);
        return compileInvocation(expr, instance, classes, expr.getMethodName(), expr.getArguments(), expectedType);
    }

    @Override
    public TypedPlan visit(StaticInvocationExpr expr) {
        ValueType expectedType = this.expectedType;
        return compileInvocation(expr, null, Collections.singleton(navigator.getGenericClass(expr.getClassName())),
                expr.getMethodName(), expr.getArguments(), expectedType);
    }

    private TypedPlan compileInvocation(Expr expr, TypedPlan instance, Collection<GenericClass> classes,
            String methodName, List<Expr> argumentExprList, ValueType expectedType) {
        TypeInference inference = new TypeInference(navigator);

        MethodLookup lookup = new MethodLookup(inference, classResolver, navigator, scope);
        GenericMethod method;
        if (instance != null) {
            method = lookup.lookupVirtual(classes, methodName, argumentExprList);
        } else {
            method = lookup.lookupStatic(classes, methodName, argumentExprList);
        }

        if (method == null) {
            return reportMissingMethod(expr, methodName, argumentExprList, lookup, classes, instance == null);
        }

        ValueType returnType = method.getActualReturnType();
        ValueType[] capturedReturnType = new ValueType[1];
        if (!addReturnTypeConstraint(method.getActualReturnType(), expectedType, inference, capturedReturnType)) {
            return errorAndFakeResult(expr, "Expected type " + expectedType + " does not match actual return type "
                    + method.getActualReturnType());
        }
        if (capturedReturnType[0] != null) {
            returnType = capturedReturnType[0];
        }

        ValueType[] argTypes = method.getActualParameterTypes();
        ValueType[] matchParamTypes = new ValueType[argumentExprList.size()];

        TypeInferenceStatePoint statePointAfterLookup = inference.createStatePoint();
        inference.resolve();

        TypedPlan[] rawArguments = new TypedPlan[argumentExprList.size()];
        for (int i = 0; i < argumentExprList.size(); ++i) {
            Expr arg = argumentExprList.get(i);
            ValueType paramType;
            if (lookup.isVarArgs() && i >= argTypes.length - 1) {
                ValueType lastArg = argTypes[argTypes.length - 1];
                if (lastArg instanceof PrimitiveArray) {
                    paramType = ((PrimitiveArray) lastArg).getElementType();
                } else {
                    paramType = ((GenericArray) lastArg).getElementType();
                }
            } else {
                paramType = argTypes[i];
            }

            matchParamTypes[i] = paramType;

            if (!(arg instanceof LambdaExpr)) {
                if (paramType instanceof GenericType) {
                    paramType = ((GenericType) paramType).substitute(inference.getSubstitutions());
                }
                this.expectedType = paramType;
                TypedPlan argPlan = arg.acceptVisitor(this);
                rawArguments[i] = argPlan;
            }
        }

        statePointAfterLookup.restoreTo();

        for (int i = 0; i < argumentExprList.size(); ++i) {
            if (rawArguments[i] != null) {
                if (!inference.subtypeConstraint(rawArguments[i].getType(), matchParamTypes[i])) {
                    return errorAndFakeResult(expr, "Argument " + (i + 1) + " type " + rawArguments[i].getType()
                            + " does not match parameter type " + matchParamTypes[i]);
                }
            }
        }

        TypeInferenceStatePoint statePointBeforeLambdas = inference.createStatePoint();
        if (!inference.resolve()) {
            return errorAndFakeResult(expr, "Could not infer type");
        }

        ValueType[] lambdaReturnTypes = new ValueType[rawArguments.length];
        for (int i = 0; i < argumentExprList.size(); ++i) {
            Expr arg = argumentExprList.get(i);
            if (arg instanceof LambdaExpr) {
                ValueType paramType = matchParamTypes[i];
                if (paramType instanceof GenericType) {
                    paramType = ((GenericType) paramType).substitute(inference.getSubstitutions());
                }

                this.expectedType = paramType;
                TypedPlan lambdaPlan = arg.acceptVisitor(this);
                rawArguments[i] = lambdaPlan;
                lambdaReturnTypes[i] = lambdaReturnType;
            }
        }

        statePointBeforeLambdas.restoreTo();
        for (int i = 0; i < argumentExprList.size(); ++i) {
            Expr arg = argumentExprList.get(i);
            if (!(arg instanceof LambdaExpr)) {
                continue;
            }

            LambdaExpr lambda = (LambdaExpr) arg;
            ValueType paramType = matchParamTypes[i];
            if (!(paramType instanceof GenericClass)) {
                continue;
            }
            GenericMethod paramSam = navigator.findSingleAbstractMethod((GenericClass) paramType);
            if (paramSam == null) {
                continue;
            }

            ValueType[] paramParamTypes = paramSam.getActualParameterTypes();
            for (int j = 0; j < paramParamTypes.length; ++j) {
                ValueType lambdaArgType = lambda.getBoundVariables().get(j).getType();
                if (lambdaArgType != null
                        && !inference.subtypeConstraint(paramParamTypes[j], lambdaArgType)) {
                    return errorAndFakeResult(expr, "Could not infer type");
                }
            }

            ValueType lambdaReturnType = lambdaReturnTypes[i];
            if (paramSam.getActualReturnType() != null && lambdaReturnType != null) {
                if (!inference.subtypeConstraint(lambdaReturnType, paramSam.getActualReturnType())) {
                    return errorAndFakeResult(expr, "Could not infer type");
                }
            }
        }

        if (!inference.resolve()) {
            return errorAndFakeResult(expr, "Could not infer type");
        }

        for (int i = 0; i < matchParamTypes.length; ++i) {
            if (matchParamTypes[i] instanceof GenericType) {
                matchParamTypes[i] = ((GenericType) matchParamTypes[i]).substitute(inference.getSubstitutions());
            }
        }
        for (int i = 0; i < argTypes.length; ++i) {
            if (argTypes[i] instanceof GenericType) {
                argTypes[i] = ((GenericType) argTypes[i]).substitute(inference.getSubstitutions());
            }
        }

        Plan[] convertedArguments = new Plan[rawArguments.length];
        for (int i = 0; i < convertedArguments.length; ++i) {
            if (rawArguments[i] != null) {
                convertedArguments[i] = convert(argumentExprList.get(i), rawArguments[i], matchParamTypes[i]).getPlan();
            }
        }

        method = method.substitute(inference.getSubstitutions());
        if (returnType instanceof GenericType) {
            returnType = ((GenericType) returnType).substitute(inference.getSubstitutions());
        }

        String className = method.getDescriber().getOwner().getName();
        String desc = methodToDesc(method.getDescriber());
        if (lookup.isVarArgs()) {
            convertedArguments = convertVarArgs(convertedArguments, argTypes);
        }

        Plan plan = new InvocationPlan(className, methodName, desc, instance != null ? instance.plan : null,
                convertedArguments);
        return planWithLocation(plan, returnType, expr);
    }

    private boolean addReturnTypeConstraint(ValueType actualType, ValueType expectedType, TypeInference inference,
            ValueType[] newReturnTypeHolder) {
        if (actualType == null || expectedType == null) {
            return true;
        }

        if (actualType instanceof GenericClass) {
            GenericClass actualClass = (GenericClass) actualType;
            if (actualClass.getArguments().stream().anyMatch(arg -> arg.getVariance() != Variance.INVARIANT)) {
                ClassDescriber describer = navigator.getClassRepository().describe(actualClass.getName());
                List<? extends TypeVar> typeParameters = Arrays.asList(describer.getTypeVariables());
                List<? extends TypeArgument> capturedTypeArgs = inference.captureConversionConstraint(typeParameters,
                        actualClass.getArguments());
                if (capturedTypeArgs == null) {
                    return false;
                }

                newReturnTypeHolder[0] = new GenericClass(actualClass.getName(), capturedTypeArgs);
                return true;
            }
        }

        return inference.subtypeConstraint(actualType, expectedType);
    }

    private Plan[] convertVarArgs(Plan[] args, ValueType[] argTypes) {
        Plan[] varargs = new Plan[argTypes.length];
        for (int i = 0; i < varargs.length - 1; ++i) {
            varargs[i] = args[i];
        }
        Plan[] array = new Plan[args.length - varargs.length + 1];
        for (int i = 0; i < array.length; ++i) {
            array[i] = args[varargs.length - 1 + i];
        }

        ValueType lastArgType = argTypes[argTypes.length - 1];
        ValueType elementType;
        if (lastArgType instanceof PrimitiveArray) {
            elementType = ((PrimitiveArray) lastArgType).getElementType();
        } else {
            elementType = ((GenericArray) lastArgType).getElementType();
        }
        ArrayConstructionPlan arrayPlan = new ArrayConstructionPlan(typeToString(elementType));
        arrayPlan.getElements().addAll(Arrays.asList(array));
        varargs[varargs.length - 1] = arrayPlan;
        return varargs;
    }

    private TypedPlan reportMissingMethod(Expr expr, String methodName, List<Expr> args,
            MethodLookup lookup, Collection<GenericClass> classes, boolean isStatic) {
        TypedPlan result = planWithLocation(new ConstantPlan(null), NullType.INSTANCE, expr);

        TypeInference inference = new TypeInference(navigator);
        MethodLookup altLookup = new MethodLookup(inference, classResolver, navigator, scope);
        GenericMethod altMethod = isStatic ? altLookup.lookupVirtual(classes, methodName, args)
                : altLookup.lookupStatic(classes, methodName, args);
        if (altMethod != null && inference.resolve()) {
            if (isStatic) {
                error(expr, "Method should be called as an instance method: " + altMethod);
            } else {
                error(expr, "Method should be called as a static method: " + altMethod);
            }
            return result;
        }

        if (lookup.getCandidates().isEmpty()) {
            error(expr, "Method not found: " + methodName);
        } else if (lookup.getCandidates().size() == 1) {
            error(expr, "Method " + lookup.getCandidates().get(0) + " is not applicable to given arguments");
        } else {
            error(expr, "Ambiguous method invocation " + methodName);
        }

        return result;
    }

    @Override
    public TypedPlan visit(PropertyExpr expr) {
        expectedType = null;
        TypedPlan instance = expr.getInstance().acceptVisitor(this);

        if ((instance.type instanceof GenericArray || instance.type instanceof PrimitiveArray)
                && expr.getPropertyName().equals("length")) {
            return planWithLocation(new ArrayLengthPlan(instance.plan), Primitive.INT, expr);
        }

        if (instance.type instanceof Primitive) {
            instance = box(expr, instance);
        }
        Collection<GenericClass> classes = CompilerCommons.extractClasses(instance.type);
        return compilePropertyAccess(expr, instance, classes, expr.getPropertyName());
    }

    @Override
    public TypedPlan visit(StaticPropertyExpr expr) {
        Collection<GenericClass> classes = Collections.singleton(navigator.getGenericClass(expr.getClassName()));
        return compilePropertyAccess(expr, null, classes, expr.getPropertyName());
    }

    private TypedPlan compilePropertyAccess(Expr expr, TypedPlan instance, Collection<GenericClass> classes,
            String propertyName) {
        GenericField field = findField(classes, propertyName);
        boolean isStatic = instance == null;
        if (field != null) {
            if (isStatic == field.getDescriber().isStatic()) {
                FieldPlan plan = new FieldPlan(instance != null ? instance.plan : null,
                        field.getDescriber().getOwner().getName(), field.getDescriber().getName(),
                        typeToString(field.getDescriber().getRawType()));
                return planWithLocation(plan, field.getActualType(), expr);
            } else {
                return errorAndFakeResult(expr, "Field " + propertyName + " should " + (!isStatic ? "not " : "")
                        + "be static");
            }
        } else {
            GenericMethod getter = findGetter(classes, propertyName);
            if (getter != null) {
                if (isStatic == getter.getDescriber().isStatic()) {
                    String desc = "()" + typeToString(getter.getDescriber().getRawReturnType());
                    InvocationPlan plan = new InvocationPlan(getter.getDescriber().getOwner().getName(),
                            getter.getDescriber().getName(), desc, instance != null ? instance.plan : null);
                    return planWithLocation(plan, getter.getActualReturnType(), expr);
                } else {
                    return errorAndFakeResult(expr, "Method " + getter.getDescriber().getName() + " should "
                            + (!isStatic ? "not " : "") + "be static");
                }
            } else {
                if (instance.plan instanceof ThisPlan) {
                    return errorAndFakeResult(expr, "Variable " + propertyName + " was not found");
                } else {
                    return errorAndFakeResult(expr, "Property " + propertyName + " was not found");
                }
            }
        }
    }

    @Override
    public TypedPlan visit(UnaryExpr expr) {
        expectedType = null;
        TypedPlan operand = expr.getOperand().acceptVisitor(this);
        switch (expr.getOperation()) {
            case NEGATE: {
                ArithmeticTypeAndPlan result = getArithmeticType(expr, operand);
                NegatePlan plan = new NegatePlan(result.plan.getPlan(), result.type);
                return planWithLocation(plan, CompilerCommons.getType(result.type), expr);
            }
            case NOT: {
                operand = ensureBooleanType(expr, operand);
                NotPlan plan = new NotPlan(operand.getPlan());
                return planWithLocation(plan, Primitive.BOOLEAN, expr);
            }
            default:
                throw new AssertionError("Should not get here");
        }
    }

    @Override
    public TypedPlan visit(VariableExpr expr) {
        ValueType type = boundVars.get(expr.getName());
        if (type != null) {
            String boundName = boundVarRenamings.get(expr.getName());
            return planWithLocation(new VariablePlan(boundName), type, expr);
        }
        type = scope.variableType(expr.getName());
        if (type == null) {
            type = scope.variableType("this");
            return compilePropertyAccess(expr, new TypedPlan(new ThisPlan(), type),
                    CompilerCommons.extractClasses(type), expr.getName());
        }

        return planWithLocation(new VariablePlan(expr.getName()), type, expr);
    }

    @Override
    public TypedPlan visit(ThisExpr expr) {
        ValueType type = scope.variableType("this");
        return planWithLocation(new ThisPlan(), type, expr);
    }

    @Override
    public TypedPlan visit(LambdaExpr expr) {
        GenericMethod lambdaSam = null;
        if (expectedType instanceof GenericClass) {
            lambdaSam = navigator.findSingleAbstractMethod((GenericClass) expectedType);
        }
        if (lambdaSam == null) {
            return errorAndFakeResult(expr, "Can't infer type of the lambda expression");
        }

        ValueType[] actualArgTypes = lambdaSam.getActualParameterTypes();
        ValueType[] oldVarTypes = new ValueType[expr.getBoundVariables().size()];
        String[] oldRenamings = new String[oldVarTypes.length];
        Set<String> usedNames = new HashSet<>();
        List<String> boundVarNames = new ArrayList<>();
        for (int i = 0; i < oldVarTypes.length; ++i) {
            BoundVariable boundVar = expr.getBoundVariables().get(i);
            if (!boundVar.getName().isEmpty()) {
                oldVarTypes[i] = boundVars.get(boundVar.getName());
                oldRenamings[i] = boundVarRenamings.get(boundVar.getName());
                if (!usedNames.add(boundVar.getName())) {
                    error(expr, "Duplicate bound variable name: " + boundVar.getName());
                } else {
                    ValueType boundVarType = boundVar.getType();
                    if (boundVarType == null) {
                        boundVarType = actualArgTypes[i];
                    } else if (!CompilerCommons.isSuperType(boundVarType, actualArgTypes[i], navigator)) {
                        error(expr, "Expected parameter type " + actualArgTypes[i]
                                + " is not a subtype of actually declared parameterType" + boundVarType);
                    }
                    boundVars.put(boundVar.getName(), boundVarType);
                    String renaming = "$" + boundVarRenamings.size();
                    boundVarRenamings.put(boundVar.getName(), renaming);
                    boundVarNames.add(renaming);
                }
            } else {
                boundVarNames.add("");
            }
        }

        expectedType = lambdaSam.getActualReturnType();
        TypedPlan body = expr.getBody().acceptVisitor(this);
        lambdaReturnType = body.getType();
        if (lambdaSam.getActualReturnType() != null) {
            body = convert(expr.getBody(), body, lambdaSam.getActualReturnType());
        }
        String className = lambdaSam.getDescriber().getOwner().getName();
        String methodName = lambdaSam.getDescriber().getName();
        String methodDesc = methodToDesc(lambdaSam.getDescriber());

        LambdaPlan lambda = new LambdaPlan(body.plan, className, methodName, methodDesc, boundVarNames);

        for (int i = 0; i < oldVarTypes.length; ++i) {
            BoundVariable boundVar = expr.getBoundVariables().get(i);
            if (!boundVar.getName().isEmpty()) {
                boundVars.put(boundVar.getName(), oldVarTypes[i]);
                boundVarRenamings.put(boundVar.getName(), oldRenamings[i]);
            }
        }

        return planWithLocation(lambda, lambdaSam.getActualOwner(), expr);
    }

    @Override
    public TypedPlan visit(ConstantExpr expr) {
        ValueType type;
        if (expr.getValue() == null) {
            type = NullType.INSTANCE;
        } else if (expr.getValue() instanceof Boolean) {
            type = Primitive.BOOLEAN;
        } else if (expr.getValue() instanceof Character) {
            type = Primitive.CHAR;
        } else if (expr.getValue() instanceof Byte) {
            type = Primitive.BYTE;
        } else if (expr.getValue() instanceof Short) {
            type = Primitive.SHORT;
        } else if (expr.getValue() instanceof Integer) {
            type = Primitive.INT;
        } else if (expr.getValue() instanceof Long) {
            type = Primitive.LONG;
        } else if (expr.getValue() instanceof Float) {
            type = Primitive.FLOAT;
        } else if (expr.getValue() instanceof Double) {
            type = Primitive.DOUBLE;
        } else if (expr.getValue() instanceof String) {
            type = TypeUtils.STRING_CLASS;
        } else {
            throw new IllegalArgumentException("Don't know how to compile constant: " + expr.getValue());
        }

        return planWithLocation(new ConstantPlan(expr.getValue()), type, expr);
    }

    @Override
    public TypedPlan visit(TernaryConditionExpr expr) {
        ValueType expectedType = null;
        this.expectedType = Primitive.BOOLEAN;
        TypedPlan condition = expr.getCondition().acceptVisitor(this);
        condition = convert(expr.getCondition(), condition, Primitive.BOOLEAN);

        this.expectedType = expectedType;
        TypedPlan consequent = expr.getConsequent().acceptVisitor(this);
        this.expectedType = expectedType;
        TypedPlan alternative = expr.getAlternative().acceptVisitor(this);

        ValueType a = consequent.getType();
        ValueType b = alternative.getType();
        ValueType type = CompilerCommons.commonSupertype(a, b, navigator);
        if (type == null) {
            ValueTypeFormatter formatter = new ValueTypeFormatter();
            return errorAndFakeResult(expr, "Clauses of ternary conditional operator are not compatible: "
                    + formatter.format(a) + " vs. " + formatter.format(b));
        }
        consequent = convert(expr.getConsequent(), consequent, type);
        alternative = convert(expr.getAlternative(), alternative, type);
        return planWithLocation(new ConditionalPlan(condition.getPlan(), consequent.getPlan(), alternative.getPlan()),
                type, expr);
    }

    @Override
    public TypedPlan visit(AssignmentExpr expr) {
        if (expr.getTarget() instanceof VariableExpr) {
            ValueType instanceType = scope.variableType("this");
            String identifier = ((VariableExpr) expr.getTarget()).getName();
            TypedPlan instance = new TypedPlan(new ThisPlan(), instanceType);
            return compileAssignment(instance, CompilerCommons.extractClasses(instanceType), identifier,
                    expr.getValue(), expr);
        } else if (expr.getTarget() instanceof PropertyExpr) {
            PropertyExpr property = (PropertyExpr) expr.getTarget();
            TypedPlan instance = property.getInstance().acceptVisitor(this);
            ValueType instanceType = instance.getType();
            String identifier = property.getPropertyName();
            return compileAssignment(instance, CompilerCommons.extractClasses(instanceType), identifier,
                    expr.getValue(), expr);
        } else if (expr.getTarget() instanceof StaticPropertyExpr) {
            StaticPropertyExpr property = (StaticPropertyExpr) expr.getTarget();
            ValueType instanceType = navigator.getGenericClass(property.getClassName());
            String identifier = property.getPropertyName();
            return compileAssignment(null, CompilerCommons.extractClasses(instanceType), identifier,
                    expr.getValue(), expr);
        } else {
            error(expr.getTarget(), "Invalid left side of assignment");
            return planWithLocation(new ThisPlan(), voidType(), expr);
        }
    }

    private GenericType voidType() {
        return new GenericClass("java.lang.Void");
    }

    private TypedPlan compileAssignment(TypedPlan instance, Collection<GenericClass> classes, String name,
            Expr value, Expr expr) {
        TypedPlan valuePlan = value.acceptVisitor(this);
        if (valuePlan.getType() == null) {
            error(value, "Right side of assignment must return a value");
            return planWithLocation(new ThisPlan(), voidType(), expr);
        }

        GenericField field = findField(classes, name);
        if (field != null) {
            String owner = field.getDescriber().getOwner().getName();
            String fieldName = field.getDescriber().getName();
            String desc = typeToString(field.getDescriber().getRawType());
            return planWithLocation(new FieldAssignmentPlan(instance != null ? instance.getPlan() : null,
                    owner, fieldName, desc, valuePlan.getPlan()), voidType(), expr);
        }

        GenericMethod setter = findSetter(classes, name, valuePlan.getType());
        if (setter != null) {
            String owner = setter.getDescriber().getOwner().getName();
            String methodName = setter.getDescriber().getName();
            String methodDesc = methodToDesc(setter.getDescriber());
            return planWithLocation(new InvocationPlan(owner, methodName, methodDesc,
                    instance != null ? instance.getPlan() : null, valuePlan.getPlan()), voidType(), expr);
        }

        error(expr, "Property not found: " + name);
        return planWithLocation(new ThisPlan(), voidType(), expr);
    }

    private GenericField findField(Collection<GenericClass> classes, String name) {
        for (GenericClass cls : classes) {
            GenericField field = navigator.getField(cls, name);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private GenericMethod findGetter(Collection<GenericClass> classes, String name) {
        String getterName = getGetterName(name);
        String booleanGetterName = getBooleanGetterName(name);
        for (GenericClass cls : classes) {
            GenericMethod method = navigator.getMethod(cls, getterName);
            if (method == null) {
                method = navigator.getMethod(cls, booleanGetterName);
                if (method != null && method.getActualReturnType() != Primitive.BOOLEAN) {
                    method = null;
                }
            }
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private GenericMethod findSetter(Collection<GenericClass> classes, String propertyName, ValueType type) {
        String setterName = getSetterName(propertyName);
        for (GenericClass cls : classes) {
            for (GenericMethod method : navigator.findMethods(cls, setterName, 1)) {
                if (CompilerCommons.isLooselyCompatibleType(method.getActualParameterTypes()[0], type, navigator)) {
                    return method;
                }
            }
        }
        return null;
    }

    private String getGetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "get";
        }
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private String getSetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "set";
        }
        return "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private String getBooleanGetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "is";
        }
        return "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private TypedPlan ensureBooleanType(Expr expr, TypedPlan plan) {
        return convert(expr, plan, Primitive.BOOLEAN);
    }

    private TypedPlan ensureIntType(Expr expr, TypedPlan plan) {
        return convert(expr, plan, Primitive.INT);
    }

    private TypedPlan convertToString(Expr expr, TypedPlan value) {
        if (value.getType().equals(TypeUtils.STRING_CLASS)) {
            return value;
        }
        ValueType type = value.getType();
        Plan plan = value.getPlan();
        if (type instanceof Primitive) {
            GenericClass wrapperClass = (GenericClass) TypeUtils.tryBox(type);
            plan = new InvocationPlan(wrapperClass.getName(), "toString", "(" + typeToString(type)
                    + ")Ljava/lang/String;", null, plan);
        } else {
            plan = new InvocationPlan("java.lang.String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;",
                    null, plan);
        }

        return planWithLocation(plan, TypeUtils.STRING_CLASS, expr);
    }

    private ArithmeticTypeAndPlan getArithmeticType(Expr expr, TypedPlan plan) {
        ValueType initialType = plan.getType();

        if (!(plan.getType() instanceof Primitive)) {
            plan = unbox(plan);
        }
        if (plan != null) {
            PrimitiveKind kind = ((Primitive) plan.type).getKind();
            IntegerSubtype subtype = CompilerCommons.getIntegerSubtype(kind);
            if (subtype != null) {
                plan = planWithLocation(new CastToIntegerPlan(subtype, plan.plan), Primitive.INT, expr);
                kind = ((Primitive) plan.type).getKind();
            }
            ArithmeticType type = CompilerCommons.getArithmeticType(kind);
            if (type != null) {
                return new ArithmeticTypeAndPlan(type, plan);
            }
        }

        error(expr, "Invalid operand type: " + initialType);
        return new ArithmeticTypeAndPlan(ArithmeticType.INT, planWithLocation(
                new ConstantPlan(0), Primitive.INT, expr));
    }

    static class ArithmeticTypeAndPlan {
        ArithmeticType type;
        TypedPlan plan;

        ArithmeticTypeAndPlan(ArithmeticType type, TypedPlan plan) {
            this.type = type;
            this.plan = plan;
        }
    }

    private ArithmeticTypeAndPlans getArithmeticTypeForPair(Expr firstExpr, TypedPlan firstPlan, Expr secondExpr,
            TypedPlan secondPlan) {
        ArithmeticTypeAndPlan firstResult = getArithmeticType(firstExpr, firstPlan);
        ArithmeticTypeAndPlan secondResult = getArithmeticType(secondExpr, secondPlan);

        ArithmeticType firstType = firstResult.type;
        ArithmeticType secondType = secondResult.type;
        firstPlan = firstResult.plan;
        secondPlan = secondResult.plan;

        ArithmeticType common = ArithmeticType.values()[Math.max(firstType.ordinal(), secondType.ordinal())];
        if (firstType != common) {
            firstPlan = planWithLocation(new ArithmeticCastPlan(firstType, common, firstPlan.getPlan()),
                    CompilerCommons.getType(common), firstExpr);
        }
        if (secondType != common) {
            secondPlan = planWithLocation(new ArithmeticCastPlan(secondType, common, secondPlan.getPlan()),
                    CompilerCommons.getType(common), secondExpr);
        }

        return new ArithmeticTypeAndPlans(common, firstPlan, secondPlan);
    }

    static class ArithmeticTypeAndPlans {
        ArithmeticType arithmeticType;
        TypedPlan first;
        TypedPlan second;

        ArithmeticTypeAndPlans(ArithmeticType arithmeticType, TypedPlan first, TypedPlan second) {
            this.arithmeticType = arithmeticType;
            this.first = first;
            this.second = second;
        }
    }

    private BinaryPlanType getPlanType(BinaryOperation op) {
        switch (op) {
            case ADD:
                return BinaryPlanType.ADD;
            case SUBTRACT:
                return BinaryPlanType.SUBTRACT;
            case MULTIPLY:
                return BinaryPlanType.MULTIPLY;
            case DIVIDE:
                return BinaryPlanType.DIVIDE;
            case REMAINDER:
                return BinaryPlanType.REMAINDER;
            case EQUAL:
                return BinaryPlanType.EQUAL;
            case NOT_EQUAL:
                return BinaryPlanType.NOT_EQUAL;
            case LESS:
                return BinaryPlanType.LESS;
            case LESS_OR_EQUAL:
                return BinaryPlanType.LESS_OR_EQUAL;
            case GREATER:
                return BinaryPlanType.GREATER;
            case GREATER_OR_EQUAL:
                return BinaryPlanType.GREATER_OR_EQUAL;
            default:
                break;
        }
        throw new AssertionError("Don't know how to map binary operation " + op + " to plan");
    }

    private LogicalBinaryPlanType getLogicalPlanType(BinaryOperation op) {
        switch (op) {
            case AND:
                return LogicalBinaryPlanType.AND;
            case OR:
                return LogicalBinaryPlanType.OR;
            default:
                break;
        }
        throw new AssertionError("Don't know how to map binary operation " + op + " to plan");
    }

    TypedPlan convert(Expr expr, TypedPlan plan, ValueType targetType) {
        TypedPlan convertedPlan = tryConvert(expr, plan, targetType);
        return convertedPlan != null
                ? convertedPlan
                : errorAndFakeResult(expr, "Can't convert " + plan.getType() + " to " + targetType);
    }

    private TypedPlan tryConvert(Expr expr, TypedPlan plan, ValueType targetType) {
        if (plan.getType() == null) {
            return null;
        }
        if (plan.getType().equals(targetType)) {
            return plan;
        }
        if (plan.getType().equals(NullType.INSTANCE)) {
            return new TypedPlan(plan.plan, targetType);
        }

        if (targetType instanceof Primitive) {
            if (!(plan.type instanceof Primitive)) {
                plan = unbox(plan);
                if (plan == null) {
                    return null;
                }
            }
            if (!CompilerCommons.hasImplicitConversion(((Primitive) plan.type).getKind(),
                    ((Primitive) targetType).getKind())) {
                return null;
            }
            plan = tryCastPrimitive(plan, (Primitive) targetType);
            if (plan == null) {
                return null;
            }
            return plan;
        }
        if (plan.type instanceof Primitive) {
            plan = box(expr, plan);
            if (plan == null) {
                return null;
            }
        }

        if (!CompilerCommons.isSuperType(targetType, plan.type, navigator)) {
            return null;
        }

        return new TypedPlan(plan.plan, targetType);
    }

    private TypedPlan tryCastPrimitive(TypedPlan plan, Primitive targetType) {
        Primitive sourceType = (Primitive) plan.type;
        if (sourceType == targetType) {
            return plan;
        }
        if (sourceType.getKind() == PrimitiveKind.BOOLEAN) {
            if (targetType != Primitive.BOOLEAN) {
                return null;
            }
        } else {
            IntegerSubtype subtype = CompilerCommons.getIntegerSubtype(sourceType.getKind());
            if (subtype != null) {
                plan = new TypedPlan(new CastToIntegerPlan(subtype, plan.plan), Primitive.INT);
                sourceType = (Primitive) plan.type;
            }
            ArithmeticType sourceArithmetic = CompilerCommons.getArithmeticType(sourceType.getKind());
            if (sourceArithmetic == null) {
                return null;
            }
            subtype = CompilerCommons.getIntegerSubtype(targetType.getKind());
            ArithmeticType targetArithmetic = CompilerCommons.getArithmeticType(targetType.getKind());
            if (targetArithmetic == null) {
                if (subtype == null) {
                    return null;
                }
                targetArithmetic = ArithmeticType.INT;
            }
            plan = new TypedPlan(new ArithmeticCastPlan(sourceArithmetic, targetArithmetic, plan.plan),
                    CompilerCommons.getType(targetArithmetic));
            if (subtype != null) {
                plan = new TypedPlan(new CastFromIntegerPlan(subtype, plan.plan), targetType);
            }
        }
        return plan;
    }

    private TypedPlan unbox(TypedPlan plan) {
        GenericClass cls;
        if (plan.type instanceof GenericReference) {
            TypeVar v = ((GenericReference) plan.type).getVar();
            cls = (GenericClass) v.getLowerBound().stream()
                    .filter(bound -> TypeUtils.tryUnbox(bound) != bound)
                    .findFirst()
                    .orElse(null);
            if (cls == null) {
                return null;
            }
        } else if (plan.type instanceof GenericClass) {
            cls = (GenericClass) plan.type;
        } else {
            return null;
        }

        Primitive primitive = TypeUtils.unbox(cls);
        if (primitive == null) {
            return null;
        }
        String methodName = primitive.getKind().name().toLowerCase() + "Value";
        return new TypedPlan(new InvocationPlan(cls.getName(), methodName, "()" + typeToString(primitive),
                plan.plan), primitive);
    }

    private TypedPlan box(Expr expr, TypedPlan plan) {
        if (!(plan.type instanceof Primitive)) {
            return null;
        }
        GenericClass wrapper = TypeUtils.box(plan.type);
        if (wrapper == null) {
            return null;
        }
        return planWithLocation(new InvocationPlan(wrapper.getName(), "valueOf", "(" + typeToString(plan.type)
                + ")" + typeToString(wrapper), null, plan.plan), wrapper, expr);
    }

    private ValueType resolveType(ValueType type, Expr expr) {
        if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass) type;
            String resolvedName = classResolver.findClass(cls.getName());
            if (resolvedName == null) {
                error(expr, "Class not found: " + cls.getName());
                return type;
            }
            boolean changed = !resolvedName.equals(cls.getName());
            List<TypeArgument> arguments = new ArrayList<>();
            for (TypeArgument arg : cls.getArguments()) {
                TypeArgument resolvedArg = arg.mapBound(bound -> (GenericType) resolveType(bound, expr));
                arguments.add(resolvedArg);
                changed |= resolvedArg != arg;
            }
            return !changed ? type : new GenericClass(resolvedName, arguments);
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray) type;
            GenericType elementType = (GenericType) resolveType(array.getElementType(), expr);
            return elementType == array.getElementType() ? type : new GenericArray(elementType);
        } else {
            return type;
        }
    }

    private void error(Expr expr, String message) {
        diagnostics.add(new Diagnostic(expr.getStart(), expr.getEnd(), message));
    }

    private TypedPlan planWithLocation(Plan plan, ValueType type, Expr expr) {
        plan.setLocation(new Location(expr.getStart(), expr.getEnd()));
        return new TypedPlan(plan, type);
    }
}
