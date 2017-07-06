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
package org.teavm.flavour.expr.ast;

import java.util.ArrayList;
import java.util.List;

public class ExprCopier implements ExprVisitor<Expr> {
    @Override
    public Expr visit(BinaryExpr expr) {
        Expr firstOperand = expr.getFirstOperand().acceptVisitor(this);
        Expr secondOperand = expr.getSecondOperand().acceptVisitor(this);
        return copyLocation(new BinaryExpr(firstOperand, secondOperand, expr.getOperation()), expr);
    }

    @Override
    public Expr visit(CastExpr expr) {
        Expr operand = expr.getValue().acceptVisitor(this);
        return copyLocation(new CastExpr(operand, expr.getTargetType()), expr);
    }

    @Override
    public Expr visit(InstanceOfExpr expr) {
        Expr operand = expr.getValue().acceptVisitor(this);
        return copyLocation(new InstanceOfExpr(operand, expr.getCheckedType()), expr);
    }

    @Override
    public Expr visit(InvocationExpr expr) {
        Expr instance = expr.getInstance() != null ? expr.getInstance().acceptVisitor(this) : null;
        List<Expr> arguments = new ArrayList<>();
        for (Expr arg : expr.getArguments()) {
            arguments.add(arg.acceptVisitor(this));
        }
        return copyLocation(new InvocationExpr(instance, expr.getMethodName(), arguments), expr);
    }

    @Override
    public Expr visit(StaticInvocationExpr expr) {
        List<Expr> arguments = new ArrayList<>();
        for (Expr arg : expr.getArguments()) {
            arguments.add(arg.acceptVisitor(this));
        }
        return copyLocation(new StaticInvocationExpr(expr.getClassName(), expr.getMethodName(), arguments), expr);
    }

    @Override
    public Expr visit(PropertyExpr expr) {
        Expr instance = expr.getInstance().acceptVisitor(this);
        return copyLocation(new PropertyExpr(instance, expr.getPropertyName()), expr);
    }

    @Override
    public Expr visit(StaticPropertyExpr expr) {
        return copyLocation(new StaticPropertyExpr(expr.getClassName(), expr.getPropertyName()), expr);
    }

    @Override
    public Expr visit(UnaryExpr expr) {
        Expr operand = expr.getOperand().acceptVisitor(this);
        return copyLocation(new UnaryExpr(operand, expr.getOperation()), expr);
    }

    @Override
    public Expr visit(VariableExpr expr) {
        return copyLocation(new VariableExpr(expr.getName()), expr);
    }

    @Override
    public Expr visit(ConstantExpr expr) {
        return copyLocation(new ConstantExpr(expr.getValue()), expr);
    }

    @Override
    public Expr visit(TernaryConditionExpr expr) {
        Expr condition = expr.getCondition().acceptVisitor(this);
        Expr consequent = expr.getConsequent().acceptVisitor(this);
        Expr alternative = expr.getAlternative().acceptVisitor(this);
        return copyLocation(new TernaryConditionExpr(condition, consequent, alternative), expr);
    }

    @Override
    public Expr visit(ThisExpr expr) {
        return copyLocation(new ThisExpr(), expr);
    }

    @Override
    public Expr visit(LambdaExpr expr) {
        Expr body = expr.getBody().acceptVisitor(this);
        return copyLocation(new LambdaExpr(body, expr.getBoundVariables()), expr);
    }

    @Override
    public Expr visit(AssignmentExpr expr) {
        Expr target = expr.getTarget().acceptVisitor(this);
        Expr value = expr.getValue().acceptVisitor(this);
        return copyLocation(new AssignmentExpr(target, value), expr);
    }

    private Expr copyLocation(Expr expr, Expr from) {
        expr.setStart(from.getStart());
        expr.setEnd(from.getEnd());
        return expr;
    }
}
