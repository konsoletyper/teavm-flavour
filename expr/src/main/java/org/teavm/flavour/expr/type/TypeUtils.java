/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.expr.type;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TypeUtils {
    public static final GenericClass BOOLEAN_CLASS = new GenericClass("java.lang.Boolean");
    public static final GenericClass CHARACTER_CLASS = new GenericClass("java.lang.Character");
    public static final GenericClass BYTE_CLASS = new GenericClass("java.lang.Byte");
    public static final GenericClass SHORT_CLASS = new GenericClass("java.lang.Short");
    public static final GenericClass INTEGER_CLASS = new GenericClass("java.lang.Integer");
    public static final GenericClass LONG_CLASS = new GenericClass("java.lang.Long");
    public static final GenericClass FLOAT_CLASS = new GenericClass("java.lang.Float");
    public static final GenericClass DOUBLE_CLASS = new GenericClass("java.lang.Double");
    public static final GenericClass STRING_CLASS = new GenericClass("java.lang.String");

    static final Map<Primitive, GenericClass> primitivesToWrappers = new HashMap<>();
    static final Map<GenericClass, Primitive> wrappersToPrimitives = new HashMap<>();

    private TypeUtils() {
    }

    static {
        primitiveAndWrapper(Primitive.BOOLEAN, BOOLEAN_CLASS);
        primitiveAndWrapper(Primitive.CHAR, CHARACTER_CLASS);
        primitiveAndWrapper(Primitive.BYTE, BYTE_CLASS);
        primitiveAndWrapper(Primitive.SHORT, SHORT_CLASS);
        primitiveAndWrapper(Primitive.INT, INTEGER_CLASS);
        primitiveAndWrapper(Primitive.LONG, LONG_CLASS);
        primitiveAndWrapper(Primitive.FLOAT, FLOAT_CLASS);
        primitiveAndWrapper(Primitive.DOUBLE, DOUBLE_CLASS);
    }

    private static void primitiveAndWrapper(Primitive primitive, GenericClass wrapper) {
        primitivesToWrappers.put(primitive, wrapper);
        wrappersToPrimitives.put(wrapper, primitive);
    }

    public static ValueType tryUnbox(GenericType type) {
        if (type instanceof GenericReference) {
            TypeVar v = ((GenericReference) type).getVar();
            return v.getLowerBound().stream()
                    .map(wrappersToPrimitives::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return wrappersToPrimitives.get(type);
    }

    public static Primitive unbox(GenericType type) {
        ValueType result = tryUnbox(type);
        return result instanceof Primitive ? (Primitive) result : null;
    }

    public static ValueType tryBox(ValueType type) {
        GenericClass wrapper = primitivesToWrappers.get(type);
        if (wrapper == null) {
            return type;
        }
        return wrapper;
    }

    public static GenericClass box(ValueType type) {
        ValueType result = tryBox(type);
        return result != type ? (GenericClass) result : null;
    }

    public static boolean isPrimitiveSubType(Primitive subtype, Primitive supertype) {
        if (subtype == supertype) {
            return true;
        }
        switch (supertype.getKind()) {
            case DOUBLE:
                switch (subtype.getKind()) {
                    case FLOAT:
                    case LONG:
                    case INT:
                    case CHAR:
                    case SHORT:
                    case BYTE:
                        return true;
                    default:
                        return false;
                }
            case FLOAT:
                switch (subtype.getKind()) {
                    case LONG:
                    case INT:
                    case CHAR:
                    case SHORT:
                    case BYTE:
                        return true;
                    default:
                        return false;
                }
            case LONG:
                switch (subtype.getKind()) {
                    case INT:
                    case CHAR:
                    case SHORT:
                    case BYTE:
                        return true;
                    default:
                        return false;
                }
            case INT:
                switch (subtype.getKind()) {
                    case CHAR:
                    case SHORT:
                    case BYTE:
                        return true;
                    default:
                        return false;
                }
            case SHORT:
                return subtype.getKind() == PrimitiveKind.BYTE;
            default:
                return false;
        }
    }

    public static MethodWithFreshTypeVars withFreshTypeVars(GenericMethod method, TypeInference inference) {
        TypeVar[] params = method.getDescriber().getTypeVariables();
        if (params.length == 0) {
            return new MethodWithFreshTypeVars(method, params);
        }

        Map<TypeVar, GenericType> substitutionMap = new HashMap<>();
        TypeVar[] freshVars = new TypeVar[params.length];
        for (int i = 0; i < params.length; ++i) {
            TypeVar freshVar = new TypeVar(params[i].getName());
            substitutionMap.put(params[i], new GenericReference(freshVar));
            freshVars[i] = freshVar;
        }
        Substitutions substitution = new MapSubstitutions(substitutionMap);

        method = method.substitute(substitution);

        for (int i = 0; i < params.length; ++i) {
            TypeVar param = params[i];
            TypeVar freshVar = freshVars[i];
            if (!param.getLowerBound().isEmpty()) {
                freshVar.withLowerBound(param.getLowerBound().stream()
                        .map(bound -> bound.substitute(substitution))
                        .toArray(GenericType[]::new));
            } else {
                freshVar.withUpperBound(param.getUpperBound().stream()
                        .map(bound -> bound.substitute(substitution))
                        .toArray(GenericType[]::new));
            }
        }

        if (!inference.addVariables(Arrays.asList(freshVars))) {
            return null;
        }

        return new MethodWithFreshTypeVars(method, freshVars);
    }
}
