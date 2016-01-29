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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.ast.BinaryExpr;
import org.teavm.flavour.expr.ast.CastExpr;
import org.teavm.flavour.expr.ast.ConstantExpr;
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
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveKind;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class TypeEstimatorVisitor implements ExprVisitor<Object> {
    private ClassResolver classResolver;
    private GenericTypeNavigator navigator;
    ValueType result;
    private Scope scope;
    Map<String, ValueType> boundVars = new HashMap<>();

    TypeEstimatorVisitor(ClassResolver classResolver, GenericTypeNavigator navigator, Scope scope) {
        this.classResolver = classResolver;
        this.navigator = navigator;
        this.scope = scope;
    }

    @Override
    public void visit(BinaryExpr<? extends Object> expr) {
        expr.getFirstOperand().acceptVisitor(this);
        if (result == null) {
            return;
        }
        ValueType first = result;

        expr.getSecondOperand().acceptVisitor(this);
        if (result == null) {
            return;
        }
        ValueType second = result;

        switch (expr.getOperation()) {
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case REMAINDER:
                result = CompilerCommons.getType(getAritmeticTypeForPair(first, second));
                break;
            case AND:
            case OR:
            case EQUAL:
            case NOT_EQUAL:
            case LESS:
            case LESS_OR_EQUAL:
            case GREATER:
            case GREATER_OR_EQUAL:
                result = Primitive.BOOLEAN;
                break;
            case ADD:
                if (first.equals(CompilerCommons.stringClass) || second.equals(CompilerCommons.stringClass)) {
                    result = CompilerCommons.stringClass;
                } else {
                    result = CompilerCommons.getType(getAritmeticTypeForPair(first, second));
                }
                break;
            case GET_ELEMENT:
                result = estimateGetElement(first, second);
                break;
        }
    }

    private ValueType estimateGetElement(ValueType first, ValueType second) {
        if (first instanceof GenericArray) {
            return ((GenericArray) first).getElementType();
        } else if (first instanceof GenericClass) {
            TypeVar k = new TypeVar("K");
            TypeVar v = new TypeVar("V");
            GenericClass mapClass = new GenericClass("java.util.Map", new GenericReference(k),
                    new GenericReference(v));
            TypeInference inference = new TypeInference(navigator);
            if (inference.subtypeConstraint((GenericClass) first, mapClass)) {
                GenericType keyType = CompilerCommons.box(second);
                inference.subtypeConstraint(keyType, new GenericReference(k));
                return new GenericReference(v).substitute(inference.getSubstitutions());
            }

            GenericClass listClass = new GenericClass("java.util.List", new GenericReference(new TypeVar()),
                    new GenericReference(v));
            inference = new TypeInference(navigator);
            if (inference.subtypeConstraint((GenericClass) first, listClass)) {
                return new GenericReference(v).substitute(inference.getSubstitutions());
            }

            return null;
        } else {
            return null;
        }
    }

    @Override
    public void visit(CastExpr<? extends Object> expr) {
        result = resolveType(expr.getTargetType());
    }

    @Override
    public void visit(InstanceOfExpr<? extends Object> expr) {
        result = Primitive.BOOLEAN;
    }

    @Override
    public void visit(InvocationExpr<? extends Object> expr) {
        expr.getInstance().acceptVisitor(this);
        if (result == null) {
            return;
        }
        GenericType instance = CompilerCommons.box(result);
        List<GenericClass> classes = new ArrayList<>();
        if (instance instanceof GenericClass) {
            classes.add((GenericClass) instance);
        } else if (instance instanceof GenericReference) {
            TypeVar var = ((GenericReference) instance).getVar();
            if (!var.getUpperBound().isEmpty()) {
                classes.addAll(var.getUpperBound().stream()
                        .filter(bound -> bound instanceof GenericClass)
                        .map(bound -> (GenericClass) bound)
                        .collect(Collectors.toList()));
            }
        }
        if (classes.isEmpty()) {
            classes.add(new GenericClass("java.lang.Object"));
        }

        ValueType[] args = new ValueType[expr.getArguments().size()];
        for (int i = 0; i < args.length; ++i) {
            expr.getArguments().get(i).acceptVisitor(this);
            args[i] = result;
        }

        MethodLookup lookup = new MethodLookup(navigator);
        GenericMethod method = lookup.lookupVirtual(classes, expr.getMethodName(), args);
        result = method == null ? null : lookup.getReturnType();
    }

    @Override
    public void visit(StaticInvocationExpr<? extends Object> expr) {
        ValueType[] args = new ValueType[expr.getArguments().size()];
        for (int i = 0; i < args.length; ++i) {
            expr.getArguments().get(i).acceptVisitor(this);
            args[i] = result;
        }

        GenericClass cls = navigator.getGenericClass(expr.getClassName());

        MethodLookup lookup = new MethodLookup(navigator);
        GenericMethod method = lookup.lookupStatic(Collections.singleton(cls), expr.getMethodName(), args);
        result = method == null ? null : lookup.getReturnType();
    }

    @Override
    public void visit(PropertyExpr<? extends Object> expr) {
        expr.getInstance().acceptVisitor(this);
        ValueType instance = result;
        if (instance == null) {
            return;
        }

        if (instance instanceof GenericArray && expr.getPropertyName().equals("length")) {
            result = Primitive.INT;
            return;
        }

        if (!(instance instanceof GenericClass)) {
            result = null;
            return;
        }

        GenericClass cls = (GenericClass) instance;
        result = estimatePropertyAccess(instance, cls, expr.getPropertyName());
    }

    @Override
    public void visit(StaticPropertyExpr<? extends Object> expr) {
        result = estimatePropertyAccess(null, navigator.getGenericClass(expr.getClassName()), expr.getPropertyName());
    }

    private ValueType estimatePropertyAccess(ValueType instance, GenericClass cls, String propertyName) {
        GenericField field = navigator.getField(cls, propertyName);
        boolean isStatic = instance == null;

        if (field != null) {
            if (isStatic == field.getDescriber().isStatic()) {
                return field.getActualType();
            } else {
                return null;
            }
        } else {
            GenericMethod method = navigator.getMethod(cls, getGetterName(propertyName));
            if (method == null) {
                method = navigator.getMethod(cls, getBooleanGetterName(propertyName));
                if (method != null && method.getActualReturnType() != Primitive.BOOLEAN) {
                    return null;
                }
            }
            if (method != null) {
                if (isStatic == method.getDescriber().isStatic()) {
                    return method.getActualReturnType();
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
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
    public void visit(UnaryExpr<? extends Object> expr) {
        expr.getOperand().acceptVisitor(this);
        if (result == null) {
            return;
        }
        switch (expr.getOperation()) {
            case NEGATE: {
                ArithmeticType type = getArithmeticType(result);
                result = type != null ? CompilerCommons.getType(type) : null;
                break;
            }
            case NOT:
                result = Primitive.BOOLEAN;
                break;
        }
    }

    @Override
    public void visit(VariableExpr<? extends Object> expr) {
        if (boundVars.containsKey(expr.getName())) {
            result = boundVars.get(expr);
        } else {
            result = scope.variableType(expr.getName());
        }
    }

    @Override
    public void visit(ConstantExpr<? extends Object> expr) {
        if (expr.getValue() == null) {
            result = null;
        } else if (expr.getValue() instanceof Boolean) {
            result = Primitive.BOOLEAN;
        } else if (expr.getValue() instanceof Character) {
            result = Primitive.CHAR;
        } else if (expr.getValue() instanceof Byte) {
            result = Primitive.BYTE;
        } else if (expr.getValue() instanceof Short) {
            result = Primitive.SHORT;
        } else if (expr.getValue() instanceof Integer) {
            result = Primitive.INT;
        } else if (expr.getValue() instanceof Long) {
            result = Primitive.LONG;
        } else if (expr.getValue() instanceof Float) {
            result = Primitive.FLOAT;
        } else if (expr.getValue() instanceof Double) {
            result = Primitive.DOUBLE;
        } else if (expr.getValue() instanceof String) {
            result = CompilerCommons.stringClass;
        } else {
            result = null;
        }
    }

    @Override
    public void visit(TernaryConditionExpr<? extends Object> expr) {
        expr.getCondition().acceptVisitor(this);
        if (result == null) {
            return;
        }
        ValueType first = result;
        expr.getAlternative().acceptVisitor(this);
        if (result == null) {
            return;
        }
        ValueType second = result;

        result = CompilerCommons.commonSupertype(first, second, navigator);
    }

    @Override
    public void visit(ThisExpr<? extends Object> expr) {
        result = scope.variableType("this");
    }

    @Override
    public void visit(LambdaExpr<? extends Object> expr) {
        result = null;
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
            type = CompilerCommons.unbox(type);
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
            List<GenericType> arguments = new ArrayList<>();
            for (GenericType arg : cls.getArguments()) {
                GenericType resolvedArg = (GenericType) resolveType(arg);
                if (resolvedArg != arg) {
                    changed = true;
                }
            }
            return !changed ? type : new GenericClass(resolvedName, arguments);
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray) type;
            ValueType elementType = resolveType(array.getElementType());
            return elementType == array.getElementType() ? type : new GenericArray(elementType);
        } else {
            return type;
        }
    }
}
