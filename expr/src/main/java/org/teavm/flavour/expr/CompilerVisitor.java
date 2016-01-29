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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.ast.BinaryExpr;
import org.teavm.flavour.expr.ast.BinaryOperation;
import org.teavm.flavour.expr.ast.BoundVariable;
import org.teavm.flavour.expr.ast.CastExpr;
import org.teavm.flavour.expr.ast.ConstantExpr;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.ExprVisitorStrict;
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
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveKind;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.ValueTypeFormatter;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

/**
 *
 * @author Alexey Andreev
 */
class CompilerVisitor implements ExprVisitorStrict<TypedPlan> {
    GenericTypeNavigator navigator;
    private Scope scope;
    private Map<String, ValueType> boundVars = new HashMap<>();
    private Map<String, String> boundVarRenamings = new HashMap<>();
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private TypeVar nullType = new TypeVar();
    private GenericReference nullTypeRef = new GenericReference(nullType);
    private ClassResolver classResolver;
    GenericMethod lambdaSam;
    ValueType lambdaReturnType;

    CompilerVisitor(GenericTypeNavigator navigator, ClassResolver classes, Scope scope) {
        this.navigator = navigator;
        this.classResolver = classes;
        this.scope = scope;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    @Override
    public void visit(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        firstOperand.acceptVisitor(this);
        secondOperand.acceptVisitor(this);
        switch (expr.getOperation()) {
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER: {
                ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                        getPlanType(expr.getOperation()), type);
                expr.setAttribute(new TypedPlan(plan, CompilerCommons.getType(type)));
                break;
            }
            case AND:
            case OR: {
                ensureBooleanType(firstOperand);
                ensureBooleanType(secondOperand);
                LogicalBinaryPlan plan = new LogicalBinaryPlan(firstOperand.getAttribute().plan,
                        secondOperand.getAttribute().plan, getLogicalPlanType(expr.getOperation()));
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                break;
            }
            case EQUAL:
            case NOT_EQUAL: {
                if (CompilerCommons.classesSuitableForComparison.contains(firstOperand.getAttribute().type)
                        && CompilerCommons.classesSuitableForComparison.contains(secondOperand.getAttribute().type)) {
                    ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                    BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan,
                            secondOperand.getAttribute().plan, getPlanType(expr.getOperation()), type);
                    expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                } else {
                    ReferenceEqualityPlan plan = new ReferenceEqualityPlan(firstOperand.getAttribute().plan,
                            secondOperand.getAttribute().plan,
                            expr.getOperation() == BinaryOperation.EQUAL ? ReferenceEqualityPlanType.EQUAL
                                    : ReferenceEqualityPlanType.NOT_EQUAL);
                    expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                }
                break;
            }
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL: {
                ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                        getPlanType(expr.getOperation()), type);
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                break;
            }
            case GET_ELEMENT:
                compileGetElement(expr);
                break;
            case ADD:
                compileAdd(expr);
                break;
        }
        copyLocation(expr);
    }

    private void compileAdd(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        ValueType firstType = firstOperand.getAttribute().type;
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        ValueType secondType = secondOperand.getAttribute().type;
        if (firstType.equals(CompilerCommons.stringClass) || secondType.equals(CompilerCommons.stringClass)) {
            Plan firstPlan = firstOperand.getAttribute().plan;
            if (firstPlan instanceof InvocationPlan) {
                InvocationPlan invocation = (InvocationPlan) firstPlan;
                if (invocation.getClassName().equals("java.lang.StringBuilder")
                        && invocation.getMethodName().equals("toString")) {
                    convertToString(secondOperand);
                    Plan instance = invocation.getInstance();
                    InvocationPlan append = new InvocationPlan("java.lang.StringBuilder", "append",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", instance,
                            secondOperand.getAttribute().plan);
                    invocation.setInstance(append);
                    expr.setAttribute(new TypedPlan(invocation, CompilerCommons.stringClass));
                    copyLocation(expr);
                    return;
                }
            }
            convertToString(firstOperand);
            convertToString(secondOperand);
            ConstructionPlan construction = new ConstructionPlan("java.lang.StringBuilder", "()V");
            InvocationPlan invocation = new InvocationPlan("java.lang.StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", construction,
                    firstOperand.getAttribute().plan);
            invocation = new InvocationPlan("java.lang.StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", invocation,
                    secondOperand.getAttribute().plan);
            invocation = new InvocationPlan("java.lang.StringBuilder", "toString", "()Ljava/lang/String;",
                    invocation);
            expr.setAttribute(new TypedPlan(invocation, CompilerCommons.stringClass));
        } else {
            ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
            BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                    BinaryPlanType.ADD, type);
            expr.setAttribute(new TypedPlan(plan, CompilerCommons.getType(type)));
        }
        copyLocation(expr);
    }

    private void compileGetElement(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        ValueType firstType = firstOperand.getAttribute().type;
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        ValueType secondType = secondOperand.getAttribute().type;
        if (firstType instanceof GenericArray) {
            GenericArray arrayType = (GenericArray) firstType;
            ensureIntType(secondOperand);
            GetArrayElementPlan plan = new GetArrayElementPlan(firstOperand.getAttribute().plan,
                    secondOperand.getAttribute().plan);
            expr.setAttribute(new TypedPlan(plan, arrayType.getElementType()));
            copyLocation(expr);
            return;
        } else if (firstType instanceof GenericClass) {
            TypeVar k = new TypeVar("K");
            TypeVar v = new TypeVar("V");
            GenericClass mapClass = new GenericClass("java.util.Map", new GenericReference(k),
                    new GenericReference(v));
            TypeInference inference = new TypeInference(navigator);
            if (inference.subtypeConstraint((GenericClass) firstType, mapClass)) {
                GenericType returnType = new GenericReference(v).substitute(inference.getSubstitutions());
                InvocationPlan plan = new InvocationPlan("java.util.Map", "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        firstOperand.getAttribute().plan, secondOperand.getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, new GenericClass("java.lang.Object")));
                copyLocation(expr);
                cast(expr, returnType);
                return;
            }

            v = new TypeVar("V");
            GenericClass listClass = new GenericClass("java.util.List", new GenericReference(v));
            inference = new TypeInference(navigator);
            if (inference.subtypeConstraint((GenericClass) firstType, listClass)) {
                GenericType returnType = new GenericReference(v).substitute(inference.getSubstitutions());
                ensureIntType(secondOperand);
                InvocationPlan plan = new InvocationPlan("java.util.List", "get", "(I)Ljava/lang/Object;",
                        firstOperand.getAttribute().plan, secondOperand.getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, new GenericClass("java.lang.Object")));
                copyLocation(expr);
                cast(expr, returnType);
                return;
            }
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericClass("java.lang.Object")));
        copyLocation(expr);
        error(expr, "Can't apply subscript operator to " + firstType + " with argument of "  + secondType);
    }

    @Override
    public void visit(CastExpr<TypedPlan> expr) {
        expr.getValue().acceptVisitor(this);
        expr.setAttribute(expr.getValue().getAttribute());
        expr.setTargetType(resolveType(expr.getTargetType(), expr));
        cast(expr, expr.getTargetType());
    }

    private void cast(Expr<TypedPlan> expr, ValueType type) {
        TypedPlan plan = expr.getAttribute();
        plan = tryCast(plan, type);
        if (plan == null) {
            error(expr, "Can't cast " + expr.getAttribute().type + " to " + type);
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), type));
            copyLocation(expr);
            return;
        }
        expr.setAttribute(plan);
        copyLocation(expr);
    }

    private TypedPlan tryCast(TypedPlan plan, ValueType targetType) {
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
            plan = box(plan);
        }

        TypeInference inference = new TypeInference(navigator);
        if (!inference.subtypeConstraint((GenericType) plan.type, (GenericType) targetType)) {
            GenericType erasure = ((GenericType) targetType).erasure();
            plan = new TypedPlan(new CastPlan(plan.plan, typeToString(erasure)),
                    ((GenericType) targetType).substitute(inference.getSubstitutions()));
        }

        return plan;
    }

    @Override
    public void visit(InstanceOfExpr<TypedPlan> expr) {
        expr.setCheckedType((GenericType) resolveType(expr.getCheckedType(), expr));
        Expr<TypedPlan> value = expr.getValue();
        value.acceptVisitor(this);
        GenericType checkedType = expr.getCheckedType();

        if (!(value.getAttribute().type instanceof GenericClass)) {
            error(expr, "Can't check against " + checkedType);
            expr.setAttribute(new TypedPlan(new ConstantPlan(false), Primitive.BOOLEAN));
            copyLocation(expr);
            return;
        }

        GenericType sourceType = (GenericType) value.getAttribute().type;
        TypeInference inference = new TypeInference(navigator);
        if (inference.subtypeConstraint(sourceType, checkedType)) {
            expr.setAttribute(new TypedPlan(new ConstantPlan(true), Primitive.BOOLEAN));
        } else {
            GenericType erasure = checkedType.erasure();
            InstanceOfPlan plan = new InstanceOfPlan(value.getAttribute().plan, typeToString(erasure));
            expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
        }
        copyLocation(expr);
    }

    @Override
    public void visit(InvocationExpr<TypedPlan> expr) {
        TypedPlan instance;
        if (expr.getInstance() != null) {
            expr.getInstance().acceptVisitor(this);
            instance = expr.getInstance().getAttribute();
        } else {
            instance = new TypedPlan(new ThisPlan(), scope.variableType("this"));
        }

        if (instance.type instanceof Primitive) {
            instance = box(instance);
        }
        List<GenericClass> classes = new ArrayList<>();
        if (instance.type instanceof GenericClass) {
            classes.add((GenericClass) instance.type);
        } else if (instance.type instanceof GenericReference) {
            TypeVar var = ((GenericReference) instance.type).getVar();
            if (!var.getUpperBound().isEmpty()) {
                classes.addAll(var.getUpperBound().stream()
                        .filter(bound -> bound instanceof GenericClass)
                        .map(bound -> (GenericClass) bound)
                        .collect(Collectors.toList()));
            }
        }
        if (classes.isEmpty()) {
            classes.add(new GenericClass("java.lang.Object"));
        }

        compileInvocation(expr, instance, classes, expr.getMethodName(), expr.getArguments());
        copyLocation(expr);
    }

    @Override
    public void visit(StaticInvocationExpr<TypedPlan> expr) {
        compileInvocation(expr, null, Collections.singleton(navigator.getGenericClass(expr.getClassName())),
                expr.getMethodName(), expr.getArguments());
        copyLocation(expr);
    }

    private void compileInvocation(Expr<TypedPlan> expr, TypedPlan instance, Collection<GenericClass> classes,
            String methodName, List<Expr<TypedPlan>> argumentExprList) {
        TypeEstimator estimator = new TypeEstimator(classResolver, navigator, new BoundScope());
        ValueType[] estimateTypes = argumentExprList.stream().map(estimator::estimate)
                .toArray(sz -> new ValueType[sz]);

        MethodLookup lookup = new MethodLookup(navigator);
        GenericMethod method;
        if (instance != null) {
            method = lookup.lookupVirtual(classes, methodName, estimateTypes);
        } else {
            method = lookup.lookupStatic(classes, methodName, estimateTypes);
        }

        if (method == null) {
            reportMissingMethod(expr, methodName, estimateTypes, lookup, classes, instance == null);
            return;
        }

        ValueType[] argTypes = method.getActualArgumentTypes();
        TypedPlan[] actualArguments = new TypedPlan[argumentExprList.size()];
        for (int i = 0; i < actualArguments.length; ++i) {
            Expr<TypedPlan> arg = argumentExprList.get(i);
            if (arg instanceof LambdaExpr<?>) {
                LambdaExpr<TypedPlan> lambda = (LambdaExpr<TypedPlan>) arg;
                GenericMethod sam = navigator.findSingleAbstractMethod((GenericClass) argTypes[i]);
                for (int j = 0; j < lambda.getBoundVariables().size(); ++j) {
                    BoundVariable boundVar = lambda.getBoundVariables().get(j);
                    resolveType(boundVar.getType(), lambda);
                }
                lambdaSam = sam;
                visit(lambda);
            } else {
                arg.acceptVisitor(this);
                convert(arg, argTypes[i], new TypeInference(navigator));
            }
        }

        String className = method.getDescriber().getOwner().getName();
        String desc = methodToDesc(method.getDescriber());
        Plan[] convertedArguments = argumentExprList.stream().map(arg -> arg.getAttribute().getPlan())
                .toArray(sz -> new Plan[sz]);
        Plan plan = new InvocationPlan(className, methodName, desc, instance != null ? instance.plan : null,
                convertedArguments);
        expr.setAttribute(new TypedPlan(plan, method.getActualReturnType()));
    }

    private void reportMissingMethod(Expr<TypedPlan> expr, String methodName, ValueType[] estimateTypes,
            MethodLookup lookup, Collection<GenericClass> classes, boolean isStatic) {
        expr.setAttribute(new TypedPlan(new ConstantPlan(null), nullTypeRef));

        MethodLookup altLookup = new MethodLookup(navigator);
        GenericMethod altMethod = isStatic ? altLookup.lookupVirtual(classes, methodName, estimateTypes)
                : altLookup.lookupStatic(classes, methodName, estimateTypes);
        if (altMethod != null) {
            if (isStatic) {
                error(expr, "Method should be called as an instance method: " + altMethod);
            } else {
                error(expr, "Method should be called as a static method: " + altMethod);
            }
            return;
        }

        StringBuilder sb = new StringBuilder();
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        if (estimateTypes.length > 0) {
            formatter.format(estimateTypes[0], sb);
            for (int i = 1; i < estimateTypes.length; ++i) {
                sb.append(", ");
                if (estimateTypes[i] != null) {
                    formatter.format(estimateTypes[i], sb);
                } else {
                    sb.append('?');
                }
            }
        }
        if (lookup.getCandidates().isEmpty()) {
            error(expr, "Method not found: " + methodName);
        } else if (lookup.getCandidates().size() == 1) {
            error(expr, "Method " + lookup.getCandidates().get(0) + " is not applicable to (" + sb + ")");
        } else {
            error(expr, "Ambigous method invocation " + methodName + "(" + sb + ")");
        }
    }

    @Override
    public void visit(PropertyExpr<TypedPlan> expr) {
        expr.getInstance().acceptVisitor(this);
        TypedPlan instance = expr.getInstance().getAttribute();

        if (instance.type instanceof GenericArray && expr.getPropertyName().equals("length")) {
            expr.setAttribute(new TypedPlan(new ArrayLengthPlan(instance.plan), Primitive.INT));
            copyLocation(expr);
            return;
        }

        if (!(instance.type instanceof GenericClass)) {
            error(expr, "Can't get property of non-class value: " + instance.type);
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericClass("java.lang.Object")));
            copyLocation(expr);
            return;
        }

        GenericClass cls = (GenericClass) instance.type;
        compilePropertyAccess(expr, instance, cls, expr.getPropertyName());
        copyLocation(expr);
    }

    private GenericMethod findGetter(GenericClass cls, String name) {
        GenericMethod method = navigator.getMethod(cls, getGetterName(name));
        if (method == null) {
            method = navigator.getMethod(cls, getBooleanGetterName(name));
            if (method != null && method.getActualReturnType() != Primitive.BOOLEAN) {
                method = null;
            }
        }
        return method;
    }

    private String getGetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "get";
        }
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private String getBooleanGetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "is";
        }
        return "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    @Override
    public void visit(StaticPropertyExpr<TypedPlan> expr) {
        compilePropertyAccess(expr, null, navigator.getGenericClass(expr.getClassName()), expr.getPropertyName());
        copyLocation(expr);
    }

    private void compilePropertyAccess(Expr<TypedPlan> expr, TypedPlan instance, GenericClass cls,
            String propertyName) {
        GenericField field = navigator.getField(cls, propertyName);
        boolean isStatic = instance == null;
        if (field != null) {
            if (isStatic == field.getDescriber().isStatic()) {
                expr.setAttribute(new TypedPlan(new FieldPlan(instance != null ? instance.plan : null,
                        field.getDescriber().getOwner().getName(), field.getDescriber().getName(),
                        typeToString(field.getDescriber().getRawType())), field.getActualType()));
                return;
            } else {
                error(expr, "Field " + propertyName + " should " + (!isStatic ? "not " : "") + "be static");
            }
        } else {
            GenericMethod getter = findGetter(cls, propertyName);
            if (getter != null) {
                if (isStatic == getter.getDescriber().isStatic()) {
                    String desc = "()" + typeToString(getter.getDescriber().getRawReturnType());
                    expr.setAttribute(new TypedPlan(new InvocationPlan(getter.getDescriber().getOwner().getName(),
                            getter.getDescriber().getName(), desc, instance != null ? instance.plan : null),
                            getter.getActualReturnType()));
                    return;
                } else {
                    error(expr, "Method " + getter.getDescriber().getName() + " should "
                            + (!isStatic ? "not " : "") + "be static");
                }
            } else {
                if (instance.plan instanceof ThisPlan) {
                    error(expr, "Variable " + propertyName + " was not found");
                } else {
                    error(expr, "Property " + propertyName + " was not found");
                }
            }
        }

        expr.setAttribute(new TypedPlan(new ConstantPlan(null), nullTypeRef));
    }

    @Override
    public void visit(UnaryExpr<TypedPlan> expr) {
        expr.getOperand().acceptVisitor(this);
        switch (expr.getOperation()) {
            case NEGATE: {
                ArithmeticType type = getArithmeticType(expr.getOperand());
                NegatePlan plan = new NegatePlan(expr.getOperand().getAttribute().plan, type);
                expr.setAttribute(new TypedPlan(plan, CompilerCommons.getType(type)));
                copyLocation(expr);
                break;
            }
            case NOT: {
                ensureBooleanType(expr.getOperand());
                NotPlan plan = new NotPlan(expr.getOperand().getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                copyLocation(expr);
                break;
            }
        }
    }

    @Override
    public void visit(VariableExpr<TypedPlan> expr) {
        ValueType type = boundVars.get(expr.getName());
        if (type != null) {
            String boundName = boundVarRenamings.get(expr.getName());
            expr.setAttribute(new TypedPlan(new VariablePlan(boundName), type));
            return;
        }
        type = scope.variableType(expr.getName());
        if (type == null) {
            type = scope.variableType("this");
            compilePropertyAccess(expr, new TypedPlan(new ThisPlan(), type), (GenericClass) type, expr.getName());
            copyLocation(expr);
            return;
        }
        expr.setAttribute(new TypedPlan(new VariablePlan(expr.getName()), type));
        copyLocation(expr);
    }

    @Override
    public void visit(ThisExpr<TypedPlan> expr) {
        ValueType type = scope.variableType("this");
        expr.setAttribute(new TypedPlan(new ThisPlan(), type));
        copyLocation(expr);
    }

    @Override
    public void visit(LambdaExpr<TypedPlan> expr) {
        if (lambdaSam == null) {
            error(expr, "Unexpected lambda here. Lambdas can only be passed for SAM classes");
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), nullTypeRef));
            copyLocation(expr);
            return;
        }
        GenericMethod lambdaSam = this.lambdaSam;
        this.lambdaSam = null;
        ValueType[] actualArgTypes = lambdaSam.getActualArgumentTypes();

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
                    ValueType boundVarType = actualArgTypes[i];
                    boundVars.put(boundVar.getName(), boundVarType);
                    String renaming = "$" + boundVarRenamings.size();
                    boundVarRenamings.put(boundVar.getName(), renaming);
                    boundVarNames.add(renaming);
                }
            } else {
                boundVarNames.add("");
            }
        }

        expr.getBody().acceptVisitor(this);
        if (lambdaSam.getActualReturnType() != null) {
            convert(expr.getBody(), lambdaSam.getActualReturnType(), new TypeInference(navigator));
        } else {
            lambdaReturnType = null;
        }
        TypedPlan body = expr.getBody().getAttribute();
        String className = lambdaSam.getDescriber().getOwner().getName();
        String methodName = lambdaSam.getDescriber().getName();
        String methodDesc = methodToDesc(lambdaSam.getDescriber());

        LambdaPlan lambda = new LambdaPlan(body.plan, className, methodName, methodDesc, boundVarNames);
        expr.setAttribute(new TypedPlan(lambda, lambdaSam.getActualOwner()));

        for (int i = 0; i < oldVarTypes.length; ++i) {
            BoundVariable boundVar = expr.getBoundVariables().get(i);
            if (!boundVar.getName().isEmpty()) {
                boundVars.put(boundVar.getName(), oldVarTypes[i]);
                boundVarRenamings.put(boundVar.getName(), oldRenamings[i]);
            }
        }
        copyLocation(expr);
    }

    @Override
    public void visit(ConstantExpr<TypedPlan> expr) {
        ValueType type;
        if (expr.getValue() == null) {
            type = new GenericReference(nullType);
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
            type = CompilerCommons.stringClass;
        } else {
            throw new IllegalArgumentException("Don't know how to compile constant: " + expr.getValue());
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(expr.getValue()), type));
        copyLocation(expr);
    }

    @Override
    public void visit(TernaryConditionExpr<TypedPlan> expr) {
        expr.getCondition().acceptVisitor(this);
        convert(expr.getCondition(), Primitive.BOOLEAN);

        expr.getConsequent().acceptVisitor(this);
        expr.getAlternative().acceptVisitor(this);

        ValueType a = expr.getConsequent().getAttribute().type;
        ValueType b = expr.getAlternative().getAttribute().type;
        ValueType type = CompilerCommons.commonSupertype(a, b, navigator);
        if (type == null) {
            expr.setAttribute(new TypedPlan(new ConstantPlan(nullType), nullTypeRef));
            ValueTypeFormatter formatter = new ValueTypeFormatter();
            error(expr, "Clauses of ternary conditional operator are not compatible: "
                    + formatter.format(a) + " vs. " + formatter.format(b));
            copyLocation(expr);
            return;
        }
        convert(expr.getConsequent(), type);
        convert(expr.getAlternative(), type);
        TypedPlan plan = new TypedPlan(new ConditionalPlan(expr.getCondition().getAttribute().plan,
                expr.getConsequent().getAttribute().plan, expr.getAlternative().getAttribute().plan), type);
        expr.setAttribute(plan);
        copyLocation(expr);
    }

    private void ensureBooleanType(Expr<TypedPlan> expr) {
        convert(expr, Primitive.BOOLEAN);
    }

    private void ensureIntType(Expr<TypedPlan> expr) {
        convert(expr, Primitive.INT);
    }

    private void convertToString(Expr<TypedPlan> expr) {
        if (expr.getAttribute().getType().equals(CompilerCommons.stringClass)) {
            return;
        }
        ValueType type = expr.getAttribute().type;
        Plan plan = expr.getAttribute().plan;
        if (type instanceof Primitive) {
            GenericClass wrapperClass = CompilerCommons.primitivesToWrappers.get(type);
            plan = new InvocationPlan(wrapperClass.getName(), "toString", "(" + typeToString(type)
                    + ")Ljava/lang/String;", null, plan);
        } else {
            plan = new InvocationPlan("java.lang.String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;",
                    null, plan);
        }
        expr.setAttribute(new TypedPlan(plan, CompilerCommons.stringClass));
    }

    private ArithmeticType getArithmeticType(Expr<TypedPlan> expr) {
        TypedPlan plan = expr.getAttribute();
        if (!(plan.getType() instanceof Primitive)) {
            plan = unbox(plan);
        }
        if (plan != null) {
            PrimitiveKind kind = ((Primitive) plan.type).getKind();
            IntegerSubtype subtype = CompilerCommons.getIntegerSubtype(kind);
            if (subtype != null) {
                expr.setAttribute(new TypedPlan(new CastToIntegerPlan(subtype, plan.plan), Primitive.INT));
                plan = expr.getAttribute();
                kind = ((Primitive) plan.type).getKind();
            }
            ArithmeticType type = CompilerCommons.getArithmeticType(kind);
            if (type != null) {
                expr.setAttribute(plan);
                return type;
            }
        }
        error(expr, "Invalid operand type: " + expr.getAttribute().type);
        expr.setAttribute(new TypedPlan(new ConstantPlan(0), Primitive.INT));
        return ArithmeticType.INT;
    }

    private ArithmeticType getAritmeticTypeForPair(Expr<TypedPlan> firstExpr, Expr<TypedPlan> secondExpr) {
        ArithmeticType firstType = getArithmeticType(firstExpr);
        ArithmeticType secondType = getArithmeticType(secondExpr);
        ArithmeticType common = ArithmeticType.values()[Math.max(firstType.ordinal(), secondType.ordinal())];
        if (firstType != common) {
            firstExpr.setAttribute(new TypedPlan(new ArithmeticCastPlan(firstType, common,
                    firstExpr.getAttribute().plan), CompilerCommons.getType(common)));
        }
        if (secondType != common) {
            secondExpr.setAttribute(new TypedPlan(new ArithmeticCastPlan(secondType, common,
                    secondExpr.getAttribute().plan), CompilerCommons.getType(common)));
        }
        return common;
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

    void convert(Expr<TypedPlan> expr, ValueType targetType) {
        convert(expr, targetType, new TypeInference(navigator));
    }

    void convert(Expr<TypedPlan> expr, ValueType targetType, TypeInference inference) {
        TypedPlan plan = expr.getAttribute();
        plan = tryConvert(plan, targetType, inference);
        if (plan != null) {
            expr.setAttribute(plan);
        } else {
            error(expr, "Can't convert " + expr.getAttribute().type + " to " + targetType);
            expr.setAttribute(new TypedPlan(new ConstantPlan(getDefaultConstant(targetType)), targetType));
        }
    }

    TypedPlan tryConvert(TypedPlan plan, ValueType targetType) {
        return tryConvert(plan, targetType, new TypeInference(navigator));
    }

    TypedPlan tryConvert(TypedPlan plan, ValueType targetType, TypeInference inference) {
        if (plan.getType() == null) {
            return null;
        }
        if (plan.getType().equals(targetType)) {
            return plan;
        }
        if (plan.getType().equals(nullTypeRef)) {
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
            plan = box(plan);
            if (plan == null) {
                return null;
            }
        }

        if (!inference.subtypeConstraint((GenericType) plan.type, (GenericType) targetType)) {
            return null;
        }

        return new TypedPlan(plan.plan, ((GenericType) targetType).substitute(inference.getSubstitutions()));
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
                    .filter(CompilerCommons.wrappersToPrimitives::containsKey)
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

        Primitive primitive = CompilerCommons.wrappersToPrimitives.get(cls);
        if (primitive == null) {
            return null;
        }
        String methodName = primitive.getKind().name().toLowerCase() + "Value";
        return new TypedPlan(new InvocationPlan(cls.getName(), methodName, "()" + typeToString(primitive),
                plan.plan), primitive);
    }

    private TypedPlan box(TypedPlan plan) {
        if (!(plan.type instanceof Primitive)) {
            return null;
        }
        GenericClass wrapper = CompilerCommons.primitivesToWrappers.get(plan.type);
        if (wrapper == null) {
            return null;
        }
        return new TypedPlan(new InvocationPlan(wrapper.getName(), "valueOf", "(" + typeToString(plan.type)
                + ")" + typeToString(wrapper), null, plan.plan), wrapper);
    }

    private Object getDefaultConstant(ValueType type) {
        if (type instanceof Primitive) {
            switch (((Primitive) type).getKind()) {
                case BOOLEAN:
                    return false;
                case CHAR:
                    return '\0';
                case BYTE:
                    return (byte) 0;
                case SHORT:
                    return (short) 0;
                case INT:
                    return 0;
                case LONG:
                    return 0L;
                case FLOAT:
                    return 0F;
                case DOUBLE:
                    return 0.0;
            }
        }
        return null;
    }

    private String typeToString(ValueType type) {
        StringBuilder sb = new StringBuilder();
        typeToString(type, sb);
        return sb.toString();
    }

    private void typeToString(ValueType type, StringBuilder sb) {
        if (type instanceof Primitive) {
            switch (((Primitive) type).getKind()) {
                case BOOLEAN:
                    sb.append('Z');
                    break;
                case CHAR:
                    sb.append('C');
                    break;
                case BYTE:
                    sb.append('B');
                    break;
                case SHORT:
                    sb.append('S');
                    break;
                case INT:
                    sb.append('I');
                    break;
                case LONG:
                    sb.append('J');
                    break;
                case FLOAT:
                    sb.append('F');
                    break;
                case DOUBLE:
                    sb.append('D');
                    break;
            }
        } else if (type instanceof GenericArray) {
            sb.append('[');
            typeToString(((GenericArray) type).getElementType(), sb);
        } else if (type instanceof GenericClass) {
            sb.append('L').append(((GenericClass) type).getName().replace('.', '/')).append(';');
        }
    }

    private ValueType resolveType(ValueType type, Expr<TypedPlan> expr) {
        if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass) type;
            String resolvedName = classResolver.findClass(cls.getName());
            if (resolvedName == null) {
                error(expr, "Class not found: " + cls.getName());
                return type;
            }
            boolean changed = !resolvedName.equals(cls.getName());
            List<GenericType> arguments = new ArrayList<>();
            for (GenericType arg : cls.getArguments()) {
                GenericType resolvedArg = (GenericType) resolveType(arg, expr);
                if (resolvedArg != arg) {
                    changed = true;
                }
            }
            return !changed ? type : new GenericClass(resolvedName, arguments);
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray) type;
            ValueType elementType = resolveType(array.getElementType(), expr);
            return elementType == array.getElementType() ? type : new GenericArray(elementType);
        } else {
            return type;
        }
    }

    String methodToDesc(MethodDescriber method) {
        StringBuilder desc = new StringBuilder().append('(');
        for (ValueType argType : method.getRawArgumentTypes()) {
            desc.append(typeToString(argType));
        }
        desc.append(')');
        if (method.getRawReturnType() != null) {
            desc.append(typeToString(method.getRawReturnType()));
        } else {
            desc.append('V');
        }
        return desc.toString();
    }

    private void error(Expr<TypedPlan> expr, String message) {
        diagnostics.add(new Diagnostic(expr.getStart(), expr.getEnd(), message));
    }

    private void copyLocation(Expr<? extends TypedPlan> expr) {
        expr.getAttribute().plan.setLocation(new Location(expr.getStart(), expr.getEnd()));
    }

    class BoundScope implements Scope {
        @Override
        public ValueType variableType(String variableName) {
            ValueType result = boundVars.get(variableName);
            if (result == null) {
                result = scope.variableType(variableName);
            }
            return result;
        }
    }
}
