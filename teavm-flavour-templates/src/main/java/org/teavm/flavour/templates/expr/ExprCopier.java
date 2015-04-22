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
package org.teavm.flavour.templates.expr;

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.templates.expr.ast.*;

/**
 *
 * @author Alexey Andreev
 */
public class ExprCopier<T> implements ExprVisitor<Object> {
    private Expr<T> result;

    public Expr<T> getResult() {
        return result;
    }

    @Override
    public void visit(BinaryExpr<? extends Object> expr) {
        expr.getFirstOperand().acceptVisitor(this);
        Expr<T> firstOperand = result;
        expr.getSecondOperand().acceptVisitor(this);
        Expr<T> secondOperand = result;
        result = new BinaryExpr<>(firstOperand, secondOperand, expr.getOperation());
    }

    @Override
    public void visit(CastExpr<? extends Object> expr) {
        expr.getValue().acceptVisitor(this);
        result = new CastExpr<>(result, expr.getTargetType());
    }

    @Override
    public void visit(InstanceOfExpr<? extends Object> expr) {
        expr.getValue().acceptVisitor(this);
        result = new InstanceOfExpr<>(result, expr.getCheckedType());
    }

    @Override
    public void visit(InvocationExpr<? extends Object> expr) {
        expr.getInstance().acceptVisitor(this);
        Expr<T> instance = result;
        List<Expr<T>> arguments = new ArrayList<>();
        for (Expr<?> arg : expr.getArguments()) {
            arg.acceptVisitor(this);
            arguments.add(result);
        }
        result = new InvocationExpr<>(instance, expr.getMethodName(), arguments);
    }

    @Override
    public void visit(StaticInvocationExpr<? extends Object> expr) {
        List<Expr<T>> arguments = new ArrayList<>();
        for (Expr<?> arg : expr.getArguments()) {
            arg.acceptVisitor(this);
            arguments.add(result);
        }
        result = new StaticInvocationExpr<>(expr.getClassName(), expr.getMethodName(), arguments);
    }

    @Override
    public void visit(PropertyExpr<? extends Object> expr) {
        expr.getInstance().acceptVisitor(this);
        Expr<T> instance = result;
        result = new PropertyExpr<>(instance, expr.getPropertyName());
    }

    @Override
    public void visit(StaticPropertyExpr<? extends Object> expr) {
        result = new StaticPropertyExpr<>(expr.getClassName(), expr.getPropertyName());
    }

    @Override
    public void visit(UnaryExpr<? extends Object> expr) {
        expr.getOperand().acceptVisitor(this);
        result = new UnaryExpr<>(result, expr.getOperation());
    }

    @Override
    public void visit(VariableExpr<? extends Object> expr) {
        result = new VariableExpr<>(expr.getName());
    }

    @Override
    public void visit(ConstantExpr<? extends Object> expr) {
        result = new ConstantExpr<>(expr.getValue());
    }
}
