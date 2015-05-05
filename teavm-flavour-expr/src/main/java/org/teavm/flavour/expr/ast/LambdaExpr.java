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

/**
 *
 * @author Alexey Andreev
 */
public class LambdaExpr<T> extends Expr<T> {
    private List<BoundVariable> boundVariables = new ArrayList<>();
    private Expr<T> body;

    public LambdaExpr(Expr<T> body, List<BoundVariable> boundVariables) {
        this.body = body;
        this.boundVariables.addAll(boundVariables);
    }

    public Expr<T> getBody() {
        return body;
    }

    public void setBody(Expr<T> body) {
        this.body = body;
    }

    public List<BoundVariable> getBoundVariables() {
        return boundVariables;
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
