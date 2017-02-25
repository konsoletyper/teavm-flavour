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

public class TernaryConditionExpr<T> extends Expr<T> {
    private Expr<T> condition;
    private Expr<T> consequent;
    private Expr<T> alternative;

    public TernaryConditionExpr(Expr<T> condition, Expr<T> consequent, Expr<T> alternative) {
        this.condition = condition;
        this.consequent = consequent;
        this.alternative = alternative;
    }

    public Expr<T> getCondition() {
        return condition;
    }

    public void setCondition(Expr<T> condition) {
        this.condition = condition;
    }

    public Expr<T> getConsequent() {
        return consequent;
    }

    public void setConsequent(Expr<T> consequent) {
        this.consequent = consequent;
    }

    public Expr<T> getAlternative() {
        return alternative;
    }

    public void setAlternative(Expr<T> alternative) {
        this.alternative = alternative;
    }

    @Override
    public void acceptVisitor(ExprVisitor<? super T> visitor) {
        visitor.visit(this);
    }

    @Override
    public void acceptVisitor(ExprVisitorStrict<T> visitor) {
        visitor.visit(this);
    }
}
