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

import org.teavm.flavour.expr.type.GenericType;

/**
 *
 * @author Alexey Andreev
 */
public class InstanceOfExpr<T> extends Expr<T> {
    private Expr<T> value;
    private GenericType checkedType;

    public InstanceOfExpr(Expr<T> value, GenericType checkedType) {
        this.value = value;
        this.checkedType = checkedType;
    }

    public Expr<T> getValue() {
        return value;
    }

    public void setValue(Expr<T> value) {
        this.value = value;
    }

    public GenericType getCheckedType() {
        return checkedType;
    }

    public void setCheckedType(GenericType checkedType) {
        this.checkedType = checkedType;
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
