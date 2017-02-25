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

public class ExprCopier<T> implements ExprVisitor<Object> {
    private Expr<T> result;

    public Expr<T> getResult() {
        return result;
    }

    @Override
    public void visit(BinaryExpr<?> expr) {
        expr.getFirstOperand().acceptVisitor(this);
        Expr<T> firstOperand = result;
        expr.getSecondOperand().acceptVisitor(this);
        Expr<T> secondOperand = result;
        result = new BinaryExpr<>(firstOperand, secondOperand, expr.getOperation());
        copyLocation(expr);
    }

    @Override
    public void visit(CastExpr<?> expr) {
        expr.getValue().acceptVisitor(this);
        result = new CastExpr<>(result, expr.getTargetType());
        copyLocation(expr);
    }

    @Override
    public void visit(InstanceOfExpr<?> expr) {
        expr.getValue().acceptVisitor(this);
        result = new InstanceOfExpr<>(result, expr.getCheckedType());
        copyLocation(expr);
    }

    @Override
    public void visit(InvocationExpr<?> expr) {
        Expr<T> instance;
        if (expr.getInstance() != null) {
            expr.getInstance().acceptVisitor(this);
            instance = result;
        } else {
            instance = null;
        }
        List<Expr<T>> arguments = new ArrayList<>();
        for (Expr<?> arg : expr.getArguments()) {
            arg.acceptVisitor(this);
            arguments.add(result);
        }
        result = new InvocationExpr<>(instance, expr.getMethodName(), arguments);
        copyLocation(expr);
    }

    @Override
    public void visit(StaticInvocationExpr<?> expr) {
        List<Expr<T>> arguments = new ArrayList<>();
        for (Expr<?> arg : expr.getArguments()) {
            arg.acceptVisitor(this);
            arguments.add(result);
        }
        result = new StaticInvocationExpr<>(expr.getClassName(), expr.getMethodName(), arguments);
        copyLocation(expr);
    }

    @Override
    public void visit(PropertyExpr<?> expr) {
        expr.getInstance().acceptVisitor(this);
        Expr<T> instance = result;
        result = new PropertyExpr<>(instance, expr.getPropertyName());
        copyLocation(expr);
    }

    @Override
    public void visit(StaticPropertyExpr<?> expr) {
        result = new StaticPropertyExpr<>(expr.getClassName(), expr.getPropertyName());
        copyLocation(expr);
    }

    @Override
    public void visit(UnaryExpr<?> expr) {
        expr.getOperand().acceptVisitor(this);
        result = new UnaryExpr<>(result, expr.getOperation());
        copyLocation(expr);
    }

    @Override
    public void visit(VariableExpr<?> expr) {
        result = new VariableExpr<>(expr.getName());
        copyLocation(expr);
    }

    @Override
    public void visit(ConstantExpr<?> expr) {
        result = new ConstantExpr<>(expr.getValue());
        copyLocation(expr);
    }

    @Override
    public void visit(TernaryConditionExpr<?> expr) {
        expr.getCondition().acceptVisitor(this);
        Expr<T> condition = result;
        expr.getConsequent().acceptVisitor(this);
        Expr<T> consequent = result;
        expr.getAlternative().acceptVisitor(this);
        Expr<T> alternative = result;
        result = new TernaryConditionExpr<>(condition, consequent, alternative);
        copyLocation(expr);
    }

    @Override
    public void visit(ThisExpr<?> expr) {
        result = new ThisExpr<>();
        copyLocation(expr);
    }

    @Override
    public void visit(LambdaExpr<?> expr) {
        expr.getBody().acceptVisitor(this);
        result = new LambdaExpr<>(result, expr.getBoundVariables());
        copyLocation(expr);
    }

    @Override
    public void visit(AssignmentExpr<?> expr) {
        expr.getTarget().acceptVisitor(this);
        Expr<T> target = result;
        expr.getValue().acceptVisitor(this);
        Expr<T> value = result;
        result = new AssignmentExpr<>(target, value);
        copyLocation(expr);
    }

    private void copyLocation(Expr<?> expr) {
        result.setStart(expr.getStart());
        result.setEnd(expr.getEnd());
    }
}
