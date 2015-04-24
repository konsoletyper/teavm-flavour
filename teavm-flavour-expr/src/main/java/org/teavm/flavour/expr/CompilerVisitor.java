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

import java.util.*;
import org.teavm.flavour.expr.ast.*;
import org.teavm.flavour.expr.plan.*;
import org.teavm.flavour.expr.type.*;

/**
 *
 * @author Alexey Andreev
 */
class CompilerVisitor implements ExprVisitorStrict<TypedPlan> {
    private static final GenericClass booleanWrapperClass = new GenericClass("java.lang.Boolean");
    private static final GenericClass characterWrapperClass = new GenericClass("java.lang.Character");
    private static final GenericClass byteWrapperClass = new GenericClass("java.lang.Byte");
    private static final GenericClass shortWrapperClass = new GenericClass("java.lang.Short");
    private static final GenericClass integerWrapperClass = new GenericClass("java.lang.Integer");
    private static final GenericClass longWrapperClass = new GenericClass("java.lang.Long");
    private static final GenericClass floatWrapperClass = new GenericClass("java.lang.Float");
    private static final GenericClass doubleWrapperClass = new GenericClass("java.lang.Double");
    private static final GenericClass stringClass = new GenericClass("java.lang.String");
    private static final Set<ValueType> classesSuitableForArrayIndex = new HashSet<>(Arrays.<ValueType>asList(
            characterWrapperClass, byteWrapperClass, shortWrapperClass, integerWrapperClass, Primitive.BYTE,
            Primitive.CHAR, Primitive.SHORT, Primitive.INT));
    private static final Set<ValueType> classesSuitableForComparison = new HashSet<>(Arrays.<ValueType>asList(
            characterWrapperClass, byteWrapperClass, shortWrapperClass, integerWrapperClass, longWrapperClass,
            floatWrapperClass, doubleWrapperClass, Primitive.BYTE, Primitive.CHAR, Primitive.SHORT,
            Primitive.INT, Primitive.LONG, Primitive.FLOAT, Primitive.DOUBLE));

    private GenericTypeNavigator navigator;
    private Scope scope;
    private List<CompilerDiagnostic> diagnostics = new ArrayList<>();
    private TypeVar nullType = new TypeVar();

    public CompilerVisitor(GenericTypeNavigator navigator, Scope scope) {
        this.navigator = navigator;
        this.scope = scope;
    }

    public List<CompilerDiagnostic> getDiagnostics() {
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
                expr.setAttribute(new TypedPlan(plan, getType(type)));
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
                if (classesSuitableForComparison.contains(firstOperand.getAttribute().type) &&
                        classesSuitableForComparison.contains(secondOperand.getAttribute().type)) {
                    ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
                    BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan,
                            secondOperand.getAttribute().plan, getPlanType(expr.getOperation()), type);
                    expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                } else {
                    ReferenceEqualityPlan plan = new ReferenceEqualityPlan(firstOperand.getAttribute().plan,
                            secondOperand.getAttribute().plan,
                            expr.getOperation() == BinaryOperation.EQUAL ? ReferenceEqualityPlanType.EQUAL :
                                    ReferenceEqualityPlanType.NOT_EQUAL);
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
    }

    private void compileAdd(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        ValueType firstType = firstOperand.getAttribute().type;
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        ValueType secondType = secondOperand.getAttribute().type;
        if (firstType.equals(stringClass) || secondType.equals(stringClass)) {
            Plan firstPlan = firstOperand.getAttribute().plan;
            if (firstPlan instanceof InvocationPlan) {
                InvocationPlan invocation = (InvocationPlan)firstPlan;
                if (invocation.getClassName().equals("java.lang.StringBuilder") &&
                        invocation.getMethodName().equals("toString")) {
                    convertToString(secondOperand);
                    Plan instance = invocation.getArguments().get(0);
                    InvocationPlan append = new InvocationPlan("java/lang/StringBuilder", "append",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", instance,
                            secondOperand.getAttribute().plan);
                    invocation.getArguments().set(0, append);
                    expr.setAttribute(new TypedPlan(invocation, stringClass));
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
            expr.setAttribute(new TypedPlan(invocation, stringClass));
        } else {
            ArithmeticType type = getAritmeticTypeForPair(firstOperand, secondOperand);
            BinaryPlan plan = new BinaryPlan(firstOperand.getAttribute().plan, secondOperand.getAttribute().plan,
                    BinaryPlanType.ADD, type);
            expr.setAttribute(new TypedPlan(plan, getType(type)));
        }
    }

    private void compileGetElement(BinaryExpr<TypedPlan> expr) {
        Expr<TypedPlan> firstOperand = expr.getFirstOperand();
        ValueType firstType = firstOperand.getAttribute().type;
        Expr<TypedPlan> secondOperand = expr.getSecondOperand();
        ValueType secondType = secondOperand.getAttribute().type;
        if (firstType instanceof GenericArray && classesSuitableForArrayIndex.contains(secondType)) {
            GenericArray arrayType = (GenericArray)firstType;
            ensureIntType(secondOperand);
            GetArrayElementPlan plan = new GetArrayElementPlan(firstOperand.getAttribute().plan,
                    secondOperand.getAttribute().plan);
            expr.setAttribute(new TypedPlan(plan, arrayType.getElementType()));
            return;
        } else if (firstType instanceof GenericClass) {
            GenericClass mapClass = navigator.getGenericClass("java.util.Map");
            GenericClass listClass = navigator.getGenericClass("java.util.List");
            TypeUnifier unifier = new TypeUnifier(navigator.getClassRepository());
            if (unifier.unify(mapClass, (GenericType)firstType, true)) {
                TypeVar var = ((GenericReference)mapClass.getArguments().get(0)).getVar();
                GenericType returnType = unifier.getSubstitutions().get(var);
                InvocationPlan plan = new InvocationPlan("java.util.Map", "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        firstOperand.getAttribute().plan, secondOperand.getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, returnType));
                return;
            } else if (unifier.unify(listClass, (GenericType)firstType, false)) {
                TypeVar var = ((GenericReference)mapClass.getArguments().get(0)).getVar();
                GenericType returnType = unifier.getSubstitutions().get(var);
                ensureIntType(secondOperand);
                InvocationPlan plan = new InvocationPlan("java.util.List", "get", "(I)Ljava/lang/Object;",
                        firstOperand.getAttribute().plan, secondOperand.getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, returnType));
                return;
            }
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(null), new GenericClass("java.lang.Object")));
        error(expr, "Can't apply subscript operator to " + firstType + " with argument of "  + secondType);
    }

    @Override
    public void visit(CastExpr<TypedPlan> expr) {
        Expr<TypedPlan> value = expr.getValue();
        if (!(value.getAttribute().type instanceof GenericClass)) {
            error(expr, "Can't cast " + value.getAttribute().type + " to " + expr.getTargetType());
            expr.setAttribute(new TypedPlan(new ConstantPlan(null), expr.getTargetType()));
        }
        GenericType targetType = (GenericType)value.getAttribute().type;
    }

    @Override
    public void visit(InstanceOfExpr<TypedPlan> expr) {
    }

    @Override
    public void visit(InvocationExpr<TypedPlan> expr) {
    }

    @Override
    public void visit(StaticInvocationExpr<TypedPlan> expr) {
    }

    @Override
    public void visit(PropertyExpr<TypedPlan> expr) {
    }

    @Override
    public void visit(StaticPropertyExpr<TypedPlan> expr) {
    }

    @Override
    public void visit(UnaryExpr<TypedPlan> expr) {
        expr.getOperand().acceptVisitor(this);
        switch (expr.getOperation()) {
            case NEGATE: {
                ArithmeticType type = getArithmeticType(expr.getOperand());
                NegatePlan plan = new NegatePlan(expr.getOperand().getAttribute().plan, type);
                expr.setAttribute(new TypedPlan(plan, getType(type)));
                break;
            }
            case NOT: {
                ensureBooleanType(expr.getOperand());
                NotPlan plan = new NotPlan(expr.getOperand().getAttribute().plan);
                expr.setAttribute(new TypedPlan(plan, Primitive.BOOLEAN));
                break;
            }
        }
    }

    @Override
    public void visit(VariableExpr<TypedPlan> expr) {
        ValueType type = scope.variableType(expr.getName());
        if (type == null) {
            error(expr, "Variable not found");
            type = new GenericClass("java.lang.Object");
        }
        expr.setAttribute(new TypedPlan(new VariablePlan(expr.getName()), type));
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
            type = stringClass;
        } else {
            throw new IllegalArgumentException("Don't know how to compile constant: " + expr.getValue());
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(expr.getValue()), type));
    }

    private void ensureBooleanType(Expr<TypedPlan> expr) {
        TypedPlan plan = expr.getAttribute();
        if (plan.type.equals(booleanWrapperClass)) {
            expr.setAttribute(new TypedPlan(new InvocationPlan("java.lang.Boolean", "booleanValue",
                    "()Z", plan.plan), Primitive.BOOLEAN));
        } else if (plan.type != Primitive.BOOLEAN) {
            error(expr, "Value has value that is not suitable for boolean operation: " + plan.type);
            expr.setAttribute(new TypedPlan(new ConstantPlan(false), Primitive.BOOLEAN));
        }
    }

    private void ensureIntType(Expr<TypedPlan> expr) {
        TypedPlan plan = expr.getAttribute();
        if (plan.type.equals(integerWrapperClass)) {
            expr.setAttribute(new TypedPlan(new InvocationPlan("java.lang.Integer",
                    "intValue", "()I", plan.plan), Primitive.INT));
        } else if (plan.type.equals(byteWrapperClass)) {
            Plan unwrapPlan = new InvocationPlan("java.lang.Byte", "byteValue", "()B", plan.plan);
            expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.BYTE, unwrapPlan), Primitive.INT));
        } else if (plan.type.equals(characterWrapperClass)) {
            Plan unwrapPlan = new InvocationPlan("java.lang.Character", "charValue", "()C", plan.plan);
            expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.CHAR, unwrapPlan), Primitive.INT));
        } else if (plan.type.equals(characterWrapperClass)) {
            Plan unwrapPlan = new InvocationPlan("java.lang.Short", "shortValue", "()S", plan.plan);
            expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.SHORT, unwrapPlan), Primitive.INT));
        } else if (plan.type == Primitive.BYTE) {
            expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.BYTE, plan.plan), Primitive.INT));
        } else if (plan.type == Primitive.SHORT) {
            expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.SHORT, plan.plan), Primitive.INT));
        } else if (plan.type == Primitive.CHAR) {
            expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.CHAR, plan.plan), Primitive.INT));
        } else if (plan.type != Primitive.INT) {
            error(expr, "Expected int, occured " + plan.type);
            expr.setAttribute(new TypedPlan(new ConstantPlan(0), Primitive.INT));
        }
    }

    private void convertToString(Expr<TypedPlan> expr) {
        if (expr.getAttribute().getType().equals(stringClass)) {
            return;
        }
        ValueType type = expr.getAttribute().type;
        Plan plan = expr.getAttribute().plan;
        if (type instanceof Primitive) {
            PrimitiveKind kind = ((Primitive)type).getKind();
            switch (kind) {
                case BOOLEAN:
                    plan = new InvocationPlan("java.lang.Boolean", "toString", "(Z)Ljava/lang/String;", null, plan);
                    break;
                case BYTE:
                    plan = new InvocationPlan("java.lang.Byte", "toString", "(B)Ljava/lang/String;", null, plan);
                    break;
                case CHAR:
                    plan = new InvocationPlan("java.lang.Character", "toString", "(C)Ljava/lang/String;", null, plan);
                    break;
                case SHORT:
                    plan = new InvocationPlan("java.lang.Short", "toString", "(S)Ljava/lang/String;", null, plan);
                    break;
                case INT:
                    plan = new InvocationPlan("java.lang.Integer", "toString", "(I)Ljava/lang/String;", null, plan);
                    break;
                case LONG:
                    plan = new InvocationPlan("java.lang.Long", "toString", "(J)Ljava/lang/String;", null, plan);
                    break;
                case FLOAT:
                    plan = new InvocationPlan("java.lang.Float", "toString", "(F)Ljava/lang/String;", null, plan);
                    break;
                case DOUBLE:
                    plan = new InvocationPlan("java.lang.Double", "toString", "(D)Ljava/lang/String;", null, plan);
                    break;
            }
        } else {
            plan = new InvocationPlan("java.lang.String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;",
                    null, plan);
        }
        expr.setAttribute(new TypedPlan(plan, stringClass));
    }

    private ArithmeticType getArithmeticType(Expr<TypedPlan> expr) {
        TypedPlan plan = expr.getAttribute();
        if (plan.type instanceof Primitive) {
            PrimitiveKind kind = ((Primitive)plan.type).getKind();
            switch (kind) {
                case BYTE:
                    expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.BYTE, plan.plan),
                            Primitive.INT));
                    return ArithmeticType.INT;
                case SHORT:
                    expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.SHORT, plan.plan),
                            Primitive.INT));
                    return ArithmeticType.INT;
                case CHAR:
                    expr.setAttribute(new TypedPlan(new CastToIntegerPlan(IntegerSubtype.CHAR, plan.plan),
                            Primitive.CHAR));
                    return ArithmeticType.INT;
                case INT:
                    return ArithmeticType.INT;
                case LONG:
                    return ArithmeticType.LONG;
                case FLOAT:
                    return ArithmeticType.FLOAT;
                case DOUBLE:
                    return ArithmeticType.DOUBLE;
                default:
                    break;
            }
        } else if (plan.type instanceof GenericClass) {
            if (plan.equals(byteWrapperClass)) {
                Plan unwrapPlan = new InvocationPlan("java.lang.Byte", "byteValue", "()B", plan.plan);
                plan = new TypedPlan(new CastToIntegerPlan(IntegerSubtype.BYTE, unwrapPlan), Primitive.INT);
                expr.setAttribute(plan);
                return ArithmeticType.INT;
            } else if (plan.equals(shortWrapperClass)) {
                Plan unwrapPlan = new InvocationPlan("java.lang.Short", "shortValue", "()S", plan.plan);
                plan = new TypedPlan(new CastToIntegerPlan(IntegerSubtype.SHORT, unwrapPlan), Primitive.INT);
                expr.setAttribute(plan);
                return ArithmeticType.INT;
            } else if (plan.equals(characterWrapperClass)) {
                Plan unwrapPlan = new InvocationPlan("java.lang.Character", "charValue", "()S", plan.plan);
                plan = new TypedPlan(new CastToIntegerPlan(IntegerSubtype.CHAR, unwrapPlan), Primitive.INT);
                expr.setAttribute(plan);
                return ArithmeticType.INT;
            } else if (plan.equals(integerWrapperClass)) {
                plan = new TypedPlan(new InvocationPlan("java.lang.Integer", "intValue", "()I", plan.plan),
                        Primitive.INT);
                expr.setAttribute(plan);
                return ArithmeticType.INT;
            } else if (plan.equals(longWrapperClass)) {
                plan = new TypedPlan(new InvocationPlan("java.lang.Long", "longValue", "()L", plan.plan),
                        Primitive.LONG);
                expr.setAttribute(plan);
                return ArithmeticType.LONG;
            } else if (plan.equals(floatWrapperClass)) {
                plan = new TypedPlan(new InvocationPlan("java.lang.Float", "floatValue", "()F", plan.plan),
                        Primitive.FLOAT);
                expr.setAttribute(plan);
                return ArithmeticType.FLOAT;
            } else if (plan.equals(doubleWrapperClass)) {
                plan = new TypedPlan(new InvocationPlan("java.lang.Double", "doubleValue", "()D", plan.plan),
                        Primitive.DOUBLE);
                expr.setAttribute(plan);
                return ArithmeticType.FLOAT;
            }
        }
        error(expr, "Illegal operand type: " + plan.type);
        expr.setAttribute(new TypedPlan(new ConstantPlan(0), Primitive.INT));
        return ArithmeticType.INT;
    }

    private ArithmeticType getAritmeticTypeForPair(Expr<TypedPlan> firstExpr, Expr<TypedPlan> secondExpr) {
        ArithmeticType firstType = getArithmeticType(firstExpr);
        ArithmeticType secondType = getArithmeticType(secondExpr);
        ArithmeticType common = ArithmeticType.values()[Math.max(firstType.ordinal(), secondType.ordinal())];
        if (firstType != common) {
            firstExpr.setAttribute(new TypedPlan(new ArithmeticCastPlan(firstType, common,
                    firstExpr.getAttribute().plan), getType(common)));
        }
        if (secondType != common) {
            secondExpr.setAttribute(new TypedPlan(new ArithmeticCastPlan(secondType, common,
                    secondExpr.getAttribute().plan), getType(common)));
        }
        return common;
    }

    private ValueType getType(ArithmeticType type) {
        switch (type) {
            case DOUBLE:
                return Primitive.DOUBLE;
            case FLOAT:
                return Primitive.FLOAT;
            case INT:
                return Primitive.INT;
            case LONG:
                return Primitive.LONG;
        }
        throw new AssertionError("Unexpected arithmetic type: " + type);
    }

    private BinaryPlanType getPlanType(BinaryOperation op) {
        switch (op) {
            case SUBTRACT:
                return BinaryPlanType.SUBTRACT;
            case MULTIPLY:
                return BinaryPlanType.MULTIPLY;
            case DIVIDE:
                return BinaryPlanType.DIVIDE;
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

    private void error(Expr<TypedPlan> expr, String message) {
        diagnostics.add(new CompilerDiagnostic(expr.getStart(), expr.getEnd(), message));
    }
}
