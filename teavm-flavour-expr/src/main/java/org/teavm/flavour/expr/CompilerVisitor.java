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
import java.util.List;
import org.teavm.flavour.expr.ast.*;
import org.teavm.flavour.expr.plan.*;
import org.teavm.flavour.expr.type.*;

/**
 *
 * @author Alexey Andreev
 */
class CompilerVisitor implements ExprVisitorStrict<TypedPlan> {
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
    }

    @Override
    public void visit(CastExpr<TypedPlan> expr) {
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
                ArithmeticTypeWithPlan operand = getArithmeticType(expr.getOperand());
                NegatePlan plan = new NegatePlan(operand.typedPlan.plan, operand.type);
                expr.setAttribute(new TypedPlan(plan, operand.typedPlan.type));
                break;
            }
            case NOT: {
                TypedPlan operand = expr.getOperand().getAttribute();
                if (operand.type != Primitive.get(PrimitiveKind.BOOLEAN)) {
                    error(expr.getOperand(), "Value has value that is not suitable for not operation: " +
                            operand.type);
                    operand = new TypedPlan(new ConstantPlan(false), Primitive.get(PrimitiveKind.BOOLEAN));
                }
                NotPlan plan = new NotPlan(operand.plan);
                expr.setAttribute(new TypedPlan(plan, Primitive.get(PrimitiveKind.BOOLEAN)));
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
            type = Primitive.get(PrimitiveKind.BOOLEAN);
        } else if (expr.getValue() instanceof Character) {
            type = Primitive.get(PrimitiveKind.CHAR);
        } else if (expr.getValue() instanceof Byte) {
            type = Primitive.get(PrimitiveKind.BYTE);
        } else if (expr.getValue() instanceof Short) {
            type = Primitive.get(PrimitiveKind.SHORT);
        } else if (expr.getValue() instanceof Integer) {
            type = Primitive.get(PrimitiveKind.INT);
        } else if (expr.getValue() instanceof Long) {
            type = Primitive.get(PrimitiveKind.LONG);
        } else if (expr.getValue() instanceof Float) {
            type = Primitive.get(PrimitiveKind.FLOAT);
        } else if (expr.getValue() instanceof Double) {
            type = Primitive.get(PrimitiveKind.DOUBLE);
        } else if (expr.getValue() instanceof String) {
            type = new GenericClass("java.lang.String");
        } else {
            throw new IllegalArgumentException("Don't know how to compile constant: " + expr.getValue());
        }
        expr.setAttribute(new TypedPlan(new ConstantPlan(expr.getValue()), type));
    }

    private ArithmeticTypeWithPlan getArithmeticType(Expr<TypedPlan> expr) {
        TypedPlan plan = expr.getAttribute();
        if (plan.type instanceof Primitive) {
            PrimitiveKind kind = ((Primitive)plan.type).getKind();
            switch (kind) {
                case BYTE:
                case SHORT:
                case CHAR:
                case INT:
                    return new ArithmeticTypeWithPlan(ArithmeticType.INT, plan);
                case LONG:
                    return new ArithmeticTypeWithPlan(ArithmeticType.LONG, plan);
                case FLOAT:
                    return new ArithmeticTypeWithPlan(ArithmeticType.FLOAT, plan);
                case DOUBLE:
                    return new ArithmeticTypeWithPlan(ArithmeticType.DOUBLE, plan);
                default:
                    break;
            }
        }
        error(expr, "Illegal operand type: " + plan.type);
        return new ArithmeticTypeWithPlan(ArithmeticType.INT,
                new TypedPlan(new ConstantPlan(0), Primitive.get(PrimitiveKind.INT)));
    }

    private void error(Expr<TypedPlan> expr, String message) {
        diagnostics.add(new CompilerDiagnostic(expr.getStart(), expr.getEnd(), message));
    }

    static class ArithmeticTypeWithPlan {
        public final ArithmeticType type;
        public final TypedPlan typedPlan;

        public ArithmeticTypeWithPlan(ArithmeticType type, TypedPlan typedPlan) {
            this.type = type;
            this.typedPlan = typedPlan;
        }
    }
}
