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
package org.teavm.flavour.expr.plan;

public interface PlanVisitor {
    void visit(ConstantPlan plan);

    void visit(VariablePlan plan);

    void visit(BinaryPlan plan);

    void visit(NegatePlan plan);

    void visit(ReferenceEqualityPlan plan);

    void visit(LogicalBinaryPlan plan);

    void visit(NotPlan plan);

    void visit(CastPlan plan);

    void visit(ArithmeticCastPlan plan);

    void visit(CastFromIntegerPlan plan);

    void visit(CastToIntegerPlan plan);

    void visit(GetArrayElementPlan plan);

    void visit(ArrayLengthPlan plan);

    void visit(FieldPlan plan);

    void visit(FieldAssignmentPlan plan);

    void visit(InstanceOfPlan plan);

    void visit(InvocationPlan plan);

    void visit(ConstructionPlan plan);

    void visit(ArrayConstructionPlan plan);

    void visit(ConditionalPlan plan);

    void visit(ThisPlan plan);

    void visit(LambdaPlan plan);

    void visit(ObjectPlan plan);
}
