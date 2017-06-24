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

public class TernaryConditionExpr extends Expr {
    private Expr condition;
    private Expr consequent;
    private Expr alternative;

    public TernaryConditionExpr(Expr condition, Expr consequent, Expr alternative) {
        this.condition = condition;
        this.consequent = consequent;
        this.alternative = alternative;
    }

    public Expr getCondition() {
        return condition;
    }

    public void setCondition(Expr condition) {
        this.condition = condition;
    }

    public Expr getConsequent() {
        return consequent;
    }

    public void setConsequent(Expr consequent) {
        this.consequent = consequent;
    }

    public Expr getAlternative() {
        return alternative;
    }

    public void setAlternative(Expr alternative) {
        this.alternative = alternative;
    }

    @Override
    public <T> T acceptVisitor(ExprVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
