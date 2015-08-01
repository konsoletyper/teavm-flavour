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

/**
 *
 * @author Alexey Andreev
 */
public interface ExprVisitor<T> {
    void visit(BinaryExpr<? extends T> expr);

    void visit(CastExpr<? extends T> expr);

    void visit(InstanceOfExpr<? extends T> expr);

    void visit(InvocationExpr<? extends T> expr);

    void visit(StaticInvocationExpr<? extends T> expr);

    void visit(PropertyExpr<? extends T> expr);

    void visit(StaticPropertyExpr<? extends T> expr);

    void visit(UnaryExpr<? extends T> expr);

    void visit(VariableExpr<? extends T> expr);

    void visit(ConstantExpr<? extends T> expr);

    void visit(TernaryConditionExpr<? extends T> expr);

    void visit(ThisExpr<? extends T> expr);

    void visit(LambdaExpr<? extends T> expr);
}
