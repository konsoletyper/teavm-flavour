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
package org.teavm.flavour.expr;

import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.expr.plan.*;

/**
 *
 * @author Alexey Andreev
 */
class InterpreterVisitor implements PlanVisitor {
    private Map<String, Object> variables;
    Object value;
    private Map<String, Class<?>> classCache = new HashMap<>();

    public InterpreterVisitor(Map<String, Object> variables) {
        this.variables = variables;
    }

    @Override
    public void visit(ConstantPlan plan) {
        value = plan.getValue();
    }

    @Override
    public void visit(VariablePlan plan) {
        value = variables.get(plan.getName());
    }

    @Override
    public void visit(BinaryPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        Object a = value;
        plan.getSecondOperand().acceptVisitor(this);
        Object b = value;
        switch (plan.getType()) {
            case ADD:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a + (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a + (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a + (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a + (Double)b;
                        break;
                }
                break;
            case SUBTRACT:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a - (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a - (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a - (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a - (Double)b;
                        break;
                }
                break;
            case MULTIPLY:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a * (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a * (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a * (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a * (Double)b;
                        break;
                }
                break;
            case DIVIDE:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a / (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a / (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a / (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a / (Double)b;
                        break;
                }
                break;
            case REMAINDER:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a % (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a % (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a % (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a % (Double)b;
                        break;
                }
                break;
            case EQUAL:
                switch (plan.getValueType()) {
                    case INT:
                        value = ((Integer)a).intValue() == ((Integer)b).intValue();
                        break;
                    case LONG:
                        value = ((Long)a).intValue() == ((Long)b).intValue();
                        break;
                    case FLOAT:
                        value = ((Float)a).floatValue() == ((Float)b).floatValue();
                        break;
                    case DOUBLE:
                        value = ((Double)a).doubleValue() == ((Double)b).doubleValue();
                        break;
                }
                break;
            case NOT_EQUAL:
                switch (plan.getValueType()) {
                    case INT:
                        value = ((Integer)a).intValue() != ((Integer)b).intValue();
                        break;
                    case LONG:
                        value = ((Long)a).intValue() != ((Long)b).intValue();
                        break;
                    case FLOAT:
                        value = ((Float)a).floatValue() != ((Float)b).floatValue();
                        break;
                    case DOUBLE:
                        value = ((Double)a).doubleValue() != ((Double)b).doubleValue();
                        break;
                }
                break;
            case GREATER:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a > (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a > (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a > (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a > (Double)b;
                        break;
                }
                break;
            case GREATER_OR_EQUAL:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a >= (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a >= (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a >= (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a >= (Double)b;
                        break;
                }
                break;
            case LESS:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a < (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a < (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a < (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a < (Double)b;
                        break;
                }
                break;
            case LESS_OR_EQUAL:
                switch (plan.getValueType()) {
                    case INT:
                        value = (Integer)a <= (Integer)b;
                        break;
                    case LONG:
                        value = (Long)a <= (Long)b;
                        break;
                    case FLOAT:
                        value = (Float)a <= (Float)b;
                        break;
                    case DOUBLE:
                        value = (Double)a <= (Double)b;
                        break;
                }
                break;
        }
    }

    @Override
    public void visit(NegatePlan plan) {
        plan.getOperand().acceptVisitor(this);
        switch (plan.getValueType()) {
            case INT:
                value = -(Integer)value;
                break;
            case LONG:
                value = -(Long)value;
                break;
            case FLOAT:
                value = -(Float)value;
                break;
            case DOUBLE:
                value = -(Double)value;
                break;
        }
    }

    @Override
    public void visit(ReferenceEqualityPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        Object a = value;
        plan.getSecondOperand().acceptVisitor(this);
        Object b = value;
        switch (plan.getType()) {
            case EQUAL:
                value = a == b;
                break;
            case NOT_EQUAL:
                value = a != b;
                break;
        }
    }

    @Override
    public void visit(LogicalBinaryPlan plan) {
        plan.getFirstOperand().acceptVisitor(this);
        Boolean a = (Boolean)value;
        switch (plan.getType()) {
            case AND:
                if (!a) {
                    value = false;
                } else {
                    plan.getSecondOperand().acceptVisitor(this);
                }
                break;
            case OR:
                if (!a) {
                    value = true;
                } else {
                    plan.getSecondOperand().acceptVisitor(this);
                }
                break;
        }
    }

    @Override
    public void visit(NotPlan plan) {
        plan.getOperand().acceptVisitor(this);
        value = !(Boolean)value;
    }

    @Override
    public void visit(CastPlan plan) {

    }

    @Override
    public void visit(ArithmeticCastPlan plan) {
    }

    @Override
    public void visit(CastFromIntegerPlan plan) {
    }

    @Override
    public void visit(CastToIntegerPlan plan) {
    }

    @Override
    public void visit(GetArrayElementPlan plan) {
    }

    @Override
    public void visit(ArrayLengthPlan plan) {
    }

    @Override
    public void visit(FieldPlan plan) {
    }

    @Override
    public void visit(InstanceOfPlan plan) {
    }

    @Override
    public void visit(InvocationPlan plan) {
    }

    @Override
    public void visit(ConstructionPlan plan) {
    }
}
