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

import org.teavm.flavour.templates.expr.ast.*;
import org.teavm.flavour.templates.expr.plan.ConstantPlan;
import org.teavm.flavour.templates.expr.plan.Plan;
import org.teavm.flavour.templates.expr.plan.VariablePlan;

/**
 *
 * @author Alexey Andreev
 */
class CompilerVisitor implements ExprVisitorStrict<Plan> {
    @Override
    public void visit(BinaryExpr<Plan> expr) {
    }

    @Override
    public void visit(CastExpr<Plan> expr) {
    }

    @Override
    public void visit(InstanceOfExpr<Plan> expr) {
    }

    @Override
    public void visit(InvocationExpr<Plan> expr) {
    }

    @Override
    public void visit(StaticInvocationExpr<Plan> expr) {
    }

    @Override
    public void visit(PropertyExpr<Plan> expr) {
    }

    @Override
    public void visit(StaticPropertyExpr<Plan> expr) {
    }

    @Override
    public void visit(UnaryExpr<Plan> expr) {
    }

    @Override
    public void visit(VariableExpr<Plan> expr) {
        expr.setAttribute(new VariablePlan(expr.getName()));
    }

    @Override
    public void visit(ConstantExpr<Plan> expr) {
        expr.setAttribute(new ConstantPlan(expr.getValue()));
    }
}
