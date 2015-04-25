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

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private Map<String, Field> fieldCache = new HashMap<>();
    private Map<String, Method> methodCache = new HashMap<>();
    private Map<String, Constructor<?>> constructorCache = new HashMap<>();

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
                if (a) {
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
        plan.getOperand().acceptVisitor(this);
        if (value == null) {
            return;
        }
        Class<?> type = decodeType(plan.getTargetType());
        if (!type.isInstance(value)) {
            throw new InterpretationException("Can't cast value to " + type);
        }
    }

    @Override
    public void visit(ArithmeticCastPlan plan) {
        plan.getOperand().acceptVisitor(this);
        switch (plan.getSourceType()) {
            case INT:
                switch (plan.getTargetType()) {
                    case INT:
                        break;
                    case LONG:
                        value = (long)(Integer)value;
                        break;
                    case FLOAT:
                        value = (float)(Integer)value;
                        break;
                    case DOUBLE:
                        value = (double)(Integer)value;
                        break;
                }
                break;
            case LONG:
                switch (plan.getTargetType()) {
                    case INT:
                        value = (int)((Long)value).longValue();
                        break;
                    case LONG:
                        break;
                    case FLOAT:
                        value = (float)(Long)value;
                        break;
                    case DOUBLE:
                        value = (double)(Long)value;
                        break;
                }
                break;
            case FLOAT:
                switch (plan.getTargetType()) {
                    case INT:
                        value = ((Float)value).intValue();
                        break;
                    case LONG:
                        value = ((Float)value).longValue();
                        break;
                    case FLOAT:
                        break;
                    case DOUBLE:
                        value = ((Float)value).doubleValue();
                        break;
                }
                break;
            case DOUBLE:
                switch (plan.getTargetType()) {
                    case INT:
                        value = ((Double)value).intValue();
                        break;
                    case LONG:
                        value = ((Double)value).longValue();
                        break;
                    case FLOAT:
                        value = ((Double)value).floatValue();
                        break;
                    case DOUBLE:
                        break;
                }
                break;
        }
    }

    @Override
    public void visit(CastFromIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        switch (plan.getType()) {
            case BYTE:
                value = ((Integer)value).byteValue();
                break;
            case CHAR:
                value = (char)((Integer)value).intValue();
                break;
            case SHORT:
                value = ((Integer)value).shortValue();
                break;
        }
    }

    @Override
    public void visit(CastToIntegerPlan plan) {
        plan.getOperand().acceptVisitor(this);
        switch (plan.getType()) {
            case BYTE:
                value = ((Byte)value).intValue();
                break;
            case CHAR:
                value = (int)((Character)value).charValue();
                break;
            case SHORT:
                value = ((Short)value).intValue();
                break;
        }
    }

    @Override
    public void visit(GetArrayElementPlan plan) {
        plan.getArray().acceptVisitor(this);
        Object array = value;
        plan.getIndex().acceptVisitor(this);
        int index = (Integer)value;
        value = Array.get(array, index);
    }

    @Override
    public void visit(ArrayLengthPlan plan) {
        plan.getArray().acceptVisitor(this);
        value = Array.getLength(value);
    }

    @Override
    public void visit(FieldPlan plan) {
        Object instance;
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
            instance = value;
        } else {
            instance = null;
        }
        try {
            value = getField(plan.getClassName(), plan.getFieldName()).get(instance);
        } catch (IllegalAccessException e) {
            throw new InterpretationException("Can't access field " + plan.getClassName() + "." +
                    plan.getFieldName(), e);
        }
    }

    @Override
    public void visit(InstanceOfPlan plan) {
        plan.getOperand().acceptVisitor(this);
        if (value == null) {
            value = false;
            return;
        }
        Class<?> type = decodeType(plan.getClassName());
        value = type.isInstance(value);
    }

    @Override
    public void visit(InvocationPlan plan) {
        Object instance;
        if (plan.getInstance() != null) {
            plan.getInstance().acceptVisitor(this);
            instance = value;
        } else {
            instance = null;
        }
        Object[] arguments = new Object[plan.getArguments().size()];
        for (int i = 0; i < arguments.length; ++i) {
            plan.getArguments().get(i).acceptVisitor(this);
            arguments[i] = value;
        }
        Method method = getMethod(plan.getClassName(), plan.getMethodName(), plan.getMethodDesc());
        try {
            value = method.invoke(instance, arguments);
        } catch (IllegalAccessException e) {
            throw new InterpretationException("Can't access method " + plan.getClassName() + "." +
                    plan.getMethodName() + plan.getMethodDesc(), e);
        } catch (InvocationTargetException e) {
            throw new InterpretationException("Can't access method " + plan.getClassName() + "." +
                    plan.getMethodName() + plan.getMethodDesc(), e);
        }
    }

    @Override
    public void visit(ConstructionPlan plan) {
        Object[] arguments = new Object[plan.getArguments().size()];
        for (int i = 0; i < arguments.length; ++i) {
            plan.getArguments().get(i).acceptVisitor(this);
            arguments[i] = value;
        }
        Constructor<?> ctor = getConstructor(plan.getClassName(), plan.getMethodDesc());
        try {
            value = ctor.newInstance(arguments);
        } catch (IllegalAccessException e) {
            throw new InterpretationException("Can't access constructor " + plan.getClassName() + ".<init>" +
                    plan.getMethodDesc(), e);
        } catch (InvocationTargetException e) {
            throw new InterpretationException("Can't access constructor " + plan.getClassName() + ".<init>" +
                    plan.getMethodDesc(), e);
        } catch (InstantiationException e) {
            throw new InterpretationException("Can't access constructor " + plan.getClassName() + ".<init>" +
                    plan.getMethodDesc(), e);
        }
    }

    private Class<?> decodeType(String type) {
        return new TypeDecoder(type).decode();
    }

    private Class<?> getClass(String name) {
        Class<?> cls = classCache.get(name);
        if (cls == null) {
            try {
                cls = Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
            classCache.put(name, cls);
        }
        return cls;
    }

    private Field getField(String className, String fieldName) {
        String key = className + "#" + fieldName;
        Field field = fieldCache.get(key);
        if (field == null) {
            Class<?> cls = getClass(className);
            if (cls == null) {
                return null;
            }
            try {
                field = cls.getField(fieldName);
            } catch (NoSuchFieldException e) {
                return null;
            }
            fieldCache.put(key, field);
        }
        return field;
    }

    private Method getMethod(String className, String methodName, String desc) {
        String key = className + "#" + methodName + desc;
        Method method = methodCache.get(key);
        if (method == null) {
            List<Class<?>> argumentTypes = new ArrayList<>();
            TypeDecoder decoder = new TypeDecoder(desc);
            if (decoder.text.charAt(decoder.position++) != '(') {
                throw new InterpretationException("Wrong method descriptor " + desc);
            }
            while (decoder.text.charAt(decoder.position) != ')') {
                argumentTypes.add(decoder.decode());
            }
            Class<?> cls = getClass(className);
            if (cls == null) {
                return null;
            }
            try {
                method = cls.getMethod(methodName, argumentTypes.toArray(new Class<?>[0]));
            } catch (NoSuchMethodException e) {
                return null;
            }
            methodCache.put(key, method);
        }
        return method;
    }

    private Constructor<?> getConstructor(String className, String desc) {
        String key = className + "#" + desc;
        Constructor<?> ctor = constructorCache.get(key);
        if (ctor == null) {
            List<Class<?>> argumentTypes = new ArrayList<>();
            TypeDecoder decoder = new TypeDecoder(desc);
            if (decoder.text.charAt(decoder.position++) != '(') {
                throw new InterpretationException("Wrong method descriptor " + desc);
            }
            while (decoder.text.charAt(decoder.position) != ')') {
                argumentTypes.add(decoder.decode());
            }
            Class<?> cls = getClass(className);
            if (cls == null) {
                return null;
            }
            try {
                ctor = cls.getConstructor(argumentTypes.toArray(new Class<?>[0]));
            } catch (NoSuchMethodException e) {
                return null;
            }
            constructorCache.put(key, ctor);
        }
        return ctor;
    }

    class TypeDecoder {
        int position;
        final String text;

        public TypeDecoder(String text) {
            this.text = text;
        }

        public Class<?> decode() {
            switch (text.charAt(position++)) {
                case 'Z':
                    return boolean.class;
                case 'C':
                    return char.class;
                case 'B':
                    return byte.class;
                case 'S':
                    return short.class;
                case 'I':
                    return int.class;
                case 'J':
                    return long.class;
                case 'F':
                    return float.class;
                case 'D':
                    return double.class;
                case 'V':
                    return void.class;
                case 'L': {
                    int index = text.indexOf(';', position);
                    Class<?> cls = InterpreterVisitor.this.getClass(text.substring(position, index).replace('/', '.'));
                    position = index + 1;
                    return cls;
                }
                case '[':
                    return Array.newInstance(decode(), 0).getClass();
                default:
                    throw new InterpretationException("Error parsing type descriptor");
            }
        }
    }
}
