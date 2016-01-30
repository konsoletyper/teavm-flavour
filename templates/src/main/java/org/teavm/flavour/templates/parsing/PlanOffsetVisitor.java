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
package org.teavm.flavour.templates.parsing;

import org.teavm.flavour.expr.Location;
import org.teavm.flavour.expr.plan.*;

/**
 *
 * @author Alexey Andreev
 */
class PlanOffsetVisitor implements PlanVisitor {
    private int offset;

    PlanOffsetVisitor(int offset) {
        this.offset = offset;
    }

    @Override
    public void visit(ConstantPlan plan) {
        apply(plan);
    }

    @Override
    public void visit(VariablePlan plan) {
        apply(plan);
    }

    @Override
    public void visit(BinaryPlan plan) {
        apply(plan);
        plan.getFirstOperand().acceptVisitor(this);
        plan.getSecondOperand().acceptVisitor(this);
    }

    @Override
    public void visit(NegatePlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(ReferenceEqualityPlan plan) {
        apply(plan);
        plan.getFirstOperand().acceptVisitor(this);
        plan.getSecondOperand().acceptVisitor(this);
    }

    @Override
    public void visit(LogicalBinaryPlan plan) {
        apply(plan);
        plan.getFirstOperand().acceptVisitor(this);
        plan.getSecondOperand().acceptVisitor(this);
    }

    @Override
    public void visit(NotPlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(CastPlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(ArithmeticCastPlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(CastFromIntegerPlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(CastToIntegerPlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(GetArrayElementPlan plan) {
        apply(plan);
        plan.getArray().acceptVisitor(this);
        plan.getIndex().acceptVisitor(this);
    }

    @Override
    public void visit(ArrayLengthPlan plan) {
        apply(plan);
        plan.getArray().acceptVisitor(this);
    }

    @Override
    public void visit(FieldPlan plan) {
        apply(plan);
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
        }
    }

    @Override
    public void visit(InstanceOfPlan plan) {
        apply(plan);
        plan.getOperand().acceptVisitor(this);
    }

    @Override
    public void visit(InvocationPlan plan) {
        apply(plan);
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
        }
        for (Plan arg : plan.getArguments()) {
            arg.acceptVisitor(this);
        }
    }

    @Override
    public void visit(ConstructionPlan plan) {
        apply(plan);
        for (Plan arg : plan.getArguments()) {
            arg.acceptVisitor(this);
        }
    }

    @Override
    public void visit(ArrayConstructionPlan plan) {
        apply(plan);
        for (Plan elem : plan.getElements()) {
            elem.acceptVisitor(this);
        }
    }

    @Override
    public void visit(ConditionalPlan plan) {
        apply(plan);
        plan.getCondition().acceptVisitor(this);
        plan.getConsequent().acceptVisitor(this);
        plan.getAlternative().acceptVisitor(this);
    }

    @Override
    public void visit(ThisPlan plan) {
        apply(plan);
    }

    @Override
    public void visit(LambdaPlan plan) {
        apply(plan);
        plan.getBody().acceptVisitor(this);
    }

    private void apply(Plan plan) {
        if (plan.getLocation() != null) {
            plan.setLocation(new Location(plan.getLocation().getStart() + offset,
                    plan.getLocation().getEnd() + offset));
        }
    }
}
