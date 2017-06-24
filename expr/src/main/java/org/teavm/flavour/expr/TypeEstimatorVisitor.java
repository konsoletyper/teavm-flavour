/*
 *  Copyright 2016 Alexey Andreev.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.teavm.flavour.expr.ast.AssignmentExpr;
import org.teavm.flavour.expr.ast.BinaryExpr;
import org.teavm.flavour.expr.ast.CastExpr;
import org.teavm.flavour.expr.ast.ConstantExpr;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.ExprVisitor;
import org.teavm.flavour.expr.ast.InstanceOfExpr;
import org.teavm.flavour.expr.ast.InvocationExpr;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.ast.PropertyExpr;
import org.teavm.flavour.expr.ast.StaticInvocationExpr;
import org.teavm.flavour.expr.ast.StaticPropertyExpr;
import org.teavm.flavour.expr.ast.TernaryConditionExpr;
import org.teavm.flavour.expr.ast.ThisExpr;
import org.teavm.flavour.expr.ast.UnaryExpr;
import org.teavm.flavour.expr.ast.VariableExpr;
import org.teavm.flavour.expr.plan.ArithmeticType;
import org.teavm.flavour.expr.plan.IntegerSubtype;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericField;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.NullType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.PrimitiveKind;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeUtils;
import org.teavm.flavour.expr.type.ValueType;

class TypeEstimatorVisitor implements ExprVisitor<ValueType> {
    private TypeInference inference;
    private ClassResolver classResolver;
    private GenericTypeNavigator navigator;
    private Scope scope;
    ValueType expectedType;

    TypeEstimatorVisitor(TypeInference inference, ClassResolver classResolver, GenericTypeNavigator navigator,
            Scope scope) {
        this.inference = inference;
        this.classResolver = classResolver;
        this.navigator = navigator;
        this.scope = scope;
    }

    @Override
    public ValueType visit(BinaryExpr expr) {
        ValueType first = expr.getFirstOperand().acceptVisitor(this);
        if (first == null) {
            return null;
        }

        ValueType second = expr.getSecondOperand().acceptVisitor(this);
        if (second == null) {
            return null;
        }

        switch (expr.getOperation()) {
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER:
                return CompilerCommons.getType(getAritmeticTypeForPair(first, second));
            case AND:
            case OR:
            case EQUAL:
            case NOT_EQUAL:
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL:
                return Primitive.BOOLEAN;
            case ADD:
                return first.equals(TypeUtils.STRING_CLASS) || second.equals(TypeUtils.STRING_CLASS)
                        ? TypeUtils.STRING_CLASS
                        : CompilerCommons.getType(getAritmeticTypeForPair(first, second));
            case GET_ELEMENT:
                return estimateGetElement(first, expr.getSecondOperand());
        }

        return null;
    }

    private ValueType estimateGetElement(ValueType first, Expr argument) {
        if (first instanceof GenericArray) {
            return ((GenericArray) first).getElementType();
        } else if (first instanceof PrimitiveArray) {
            return ((PrimitiveArray) first).getElementType();
        } else if (first instanceof GenericClass) {
            Collection<GenericClass> classes = CompilerCommons.extractClasses(first);

            MethodLookup lookup = new MethodLookup(inference, classResolver, navigator, scope);
            GenericMethod method = lookup.lookupVirtual(classes, "get", Arrays.asList(argument));
            return method == null ? null : lookup.getReturnType();
        } else {
            return null;
        }
    }

    @Override
    public ValueType visit(CastExpr expr) {
        return resolveType(expr.getTargetType());
    }

    @Override
    public ValueType visit(InstanceOfExpr expr) {
        return Primitive.BOOLEAN;
    }

    @Override
    public ValueType visit(InvocationExpr expr) {
        ValueType instance = expr.getInstance() != null
                ? expr.getInstance().acceptVisitor(this)
                : scope.variableType("this");
        if (instance == null) {
            return null;
        }

        instance = TypeUtils.tryBox(instance);
        if (!(instance instanceof GenericClass)) {
            return null;
        }
        Collection<GenericClass> classes = CompilerCommons.extractClasses(instance);

        ValueType[] args = new ValueType[expr.getArguments().size()];
        for (int i = 0; i < args.length; ++i) {
            args[i] = expr.getArguments().get(i).acceptVisitor(this);
        }

        MethodLookup lookup = new MethodLookup(inference, classResolver, navigator, scope);
        GenericMethod method = lookup.lookupVirtual(classes, expr.getMethodName(), expr.getArguments());
        return fixReturnType(method, lookup);
    }

    @Override
    public ValueType visit(StaticInvocationExpr expr) {
        ValueType[] args = new ValueType[expr.getArguments().size()];
        for (int i = 0; i < args.length; ++i) {
            args[i] = expr.getArguments().get(i).acceptVisitor(this);
        }

        GenericClass cls = navigator.getGenericClass(expr.getClassName());

        MethodLookup lookup = new MethodLookup(inference, classResolver, navigator, scope);
        GenericMethod method = lookup.lookupStatic(Collections.singleton(cls), expr.getMethodName(),
                expr.getArguments());
        return fixReturnType(method, lookup);
    }

    private ValueType fixReturnType(GenericMethod method, MethodLookup lookup) {
        if (method == null) {
            return null;
        }

        ValueType[] capturedReturnType = new ValueType[1];
        if (!addReturnTypeConstraint(lookup.getReturnType(), expectedType)) {
            return null;
        }
        if (capturedReturnType != null) {
            return capturedReturnType[0];
        }
        return lookup.getReturnType();
    }


    private boolean addReturnTypeConstraint(ValueType actualType, ValueType expectedType) {
        if (actualType == null || expectedType == null) {
            return true;
        }

        return inference.subtypeConstraint(actualType, expectedType);
    }

    @Override
    public ValueType visit(PropertyExpr expr) {
        ValueType instance = expr.getInstance().acceptVisitor(this);
        if (instance == null) {
            return null;
        }

        if (instance instanceof GenericArray && expr.getPropertyName().equals("length")) {
            return Primitive.INT;
        }

        return estimatePropertyAccess(instance, CompilerCommons.extractClasses(instance), expr.getPropertyName());
    }

    @Override
    public ValueType visit(StaticPropertyExpr expr) {
        GenericClass cls = navigator.getGenericClass(expr.getClassName());
        return estimatePropertyAccess(null, Collections.singleton(cls), expr.getPropertyName());
    }

    private ValueType estimatePropertyAccess(ValueType instance, Collection<GenericClass> classes,
            String propertyName) {
        for (GenericClass cls : classes) {
            GenericField field = navigator.getField(cls, propertyName);
            boolean isStatic = instance == null;

            if (field != null) {
                if (isStatic == field.getDescriber().isStatic()) {
                    return field.getActualType();
                }
            } else {
                GenericMethod method = navigator.getMethod(cls, getGetterName(propertyName));
                if (method == null) {
                    method = navigator.getMethod(cls, getBooleanGetterName(propertyName));
                    if (method != null && method.getActualReturnType() != Primitive.BOOLEAN) {
                        continue;
                    }
                }
                if (method != null) {
                    if (isStatic == method.getDescriber().isStatic()) {
                        return method.getActualReturnType();
                    }
                }
            }
        }
        return null;
    }

    private String getGetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "get";
        }
        return "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    private String getBooleanGetterName(String propertyName) {
        if (propertyName.isEmpty()) {
            return "is";
        }
        return "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    @Override
    public ValueType visit(UnaryExpr expr) {
        ValueType result = expr.getOperand().acceptVisitor(this);
        if (result == null) {
            return null;
        }
        switch (expr.getOperation()) {
            case NEGATE: {
                ArithmeticType type = getArithmeticType(result);
                return type != null ? CompilerCommons.getType(type) : null;
            }
            case NOT:
                return Primitive.BOOLEAN;
        }

        return null;
    }

    @Override
    public ValueType visit(VariableExpr expr) {
        ValueType result = scope.variableType(expr.getName());
        if (result == null) {
            ValueType thisType = scope.variableType("this");
            return estimatePropertyAccess(thisType, CompilerCommons.extractClasses(thisType), expr.getName());
        }

        return result;
    }

    @Override
    public ValueType visit(ConstantExpr expr) {
        if (expr.getValue() == null) {
            return NullType.INSTANCE;
        } else if (expr.getValue() instanceof Boolean) {
            return Primitive.BOOLEAN;
        } else if (expr.getValue() instanceof Character) {
            return Primitive.CHAR;
        } else if (expr.getValue() instanceof Byte) {
            return Primitive.BYTE;
        } else if (expr.getValue() instanceof Short) {
            return Primitive.SHORT;
        } else if (expr.getValue() instanceof Integer) {
            return Primitive.INT;
        } else if (expr.getValue() instanceof Long) {
            return Primitive.LONG;
        } else if (expr.getValue() instanceof Float) {
            return Primitive.FLOAT;
        } else if (expr.getValue() instanceof Double) {
            return Primitive.DOUBLE;
        } else if (expr.getValue() instanceof String) {
            return TypeUtils.STRING_CLASS;
        } else {
            return null;
        }
    }

    @Override
    public ValueType visit(TernaryConditionExpr expr) {
        ValueType first = expr.getConsequent().acceptVisitor(this);
        if (first == null) {
            return null;
        }
        ValueType second = expr.getAlternative().acceptVisitor(this);
        if (second == null) {
            return null;
        }

        return CompilerCommons.commonSupertype(first, second, navigator);
    }

    @Override
    public ValueType visit(ThisExpr expr) {
        return scope.variableType("this");
    }

    @Override
    public ValueType visit(LambdaExpr expr) {
        return null;
    }

    @Override
    public ValueType visit(AssignmentExpr expr) {
        return null;
    }

    private ArithmeticType getAritmeticTypeForPair(ValueType first, ValueType second) {
        ArithmeticType firstType = getArithmeticType(first);
        if (firstType == null) {
            return null;
        }
        ArithmeticType secondType = getArithmeticType(second);
        if (secondType == null) {
            return null;
        }
        return ArithmeticType.values()[Math.max(firstType.ordinal(), secondType.ordinal())];
    }

    private ArithmeticType getArithmeticType(ValueType type) {
        if (!(type instanceof Primitive)) {
            type = TypeUtils.tryUnbox((GenericType) type);
        }
        if (type != null) {
            PrimitiveKind kind = ((Primitive) type).getKind();
            IntegerSubtype subtype = CompilerCommons.getIntegerSubtype(kind);
            if (subtype != null) {
                kind = ((Primitive) type).getKind();
            }
            return CompilerCommons.getArithmeticType(kind);
        }
        return null;
    }

    private ValueType resolveType(ValueType type) {
        if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass) type;
            String resolvedName = classResolver.findClass(cls.getName());
            if (resolvedName == null) {
                return type;
            }
            boolean changed = !resolvedName.equals(cls.getName());
            List<TypeArgument> arguments = new ArrayList<>();
            for (TypeArgument arg : cls.getArguments()) {
                TypeArgument resolvedArg = arg.mapBound(bound -> (GenericType) resolveType(bound));
                arguments.add(resolvedArg);
                changed |= arg != resolvedArg;
            }
            return !changed ? type : new GenericClass(resolvedName, arguments);
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray) type;
            GenericType elementType = (GenericType) resolveType(array.getElementType());
            return elementType == array.getElementType() ? type : new GenericArray(elementType);
        } else {
            return type;
        }
    }
}
