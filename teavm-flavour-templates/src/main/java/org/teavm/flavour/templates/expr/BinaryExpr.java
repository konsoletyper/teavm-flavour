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

/**
 *
 * @author Alexey Andreev
 */
public class BinaryExpr<T> extends Expr<T> {
    private Expr<T> firstOperand;
    private Expr<T> secondOperand;
    private BinaryOperation operation;

    public Expr<T> getFirstOperand() {
        return firstOperand;
    }

    public void setFirstOperand(Expr<T> firstOperand) {
        this.firstOperand = firstOperand;
    }

    public Expr<T> getSecondOperand() {
        return secondOperand;
    }

    public void setSecondOperand(Expr<T> secondOperand) {
        this.secondOperand = secondOperand;
    }

    public BinaryOperation getOperation() {
        return operation;
    }

    public void setOperation(BinaryOperation operation) {
        this.operation = operation;
    }

    @Override
    public void acceptVisitor(ExprVisitor<? super T> visitor) {
        visitor.visit(this);
    }
}
