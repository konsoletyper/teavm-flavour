/*
 *  Copyright 2016 Alexey Andreev.
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

public class AssignmentExpr<T> extends Expr<T> {
    private Expr<T> target;
    private Expr<T> value;

    public AssignmentExpr(Expr<T> target, Expr<T> value) {
        this.target = target;
        this.value = value;
    }

    public Expr<T> getTarget() {
        return target;
    }

    public Expr<T> getValue() {
        return value;
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
