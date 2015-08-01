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

/**
 *
 * @author Alexey Andreev
 */
public class CastFromIntegerPlan extends Plan {
    private IntegerSubtype type;
    private Plan operand;

    public CastFromIntegerPlan(IntegerSubtype type, Plan operand) {
        this.type = type;
        this.operand = operand;
    }

    public IntegerSubtype getType() {
        return type;
    }

    public void setType(IntegerSubtype type) {
        this.type = type;
    }

    public Plan getOperand() {
        return operand;
    }

    public void setOperand(Plan operand) {
        this.operand = operand;
    }

    @Override
    public void acceptVisitor(PlanVisitor visitor) {
        visitor.visit(this);
    }
}
