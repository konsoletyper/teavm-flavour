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
package org.teavm.flavour.templates.expr.intermediate;

/**
 *
 * @author Alexey Andreev
 */
public class NegatePlan extends Plan {
    private Plan operand;
    private ArithmeticType valueType;

    public NegatePlan(Plan operand, ArithmeticType valueType) {
        this.operand = operand;
        this.valueType = valueType;
    }

    public Plan getOperand() {
        return operand;
    }

    public void setOperand(Plan operand) {
        this.operand = operand;
    }

    public ArithmeticType getValueType() {
        return valueType;
    }

    public void setValueType(ArithmeticType valueType) {
        this.valueType = valueType;
    }

    @Override
    public void acceptVisitor(PlanVisitor visitor) {
        visitor.visit(this);
    }
}
