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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.plan.ArithmeticType;
import org.teavm.flavour.expr.plan.IntegerSubtype;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.GenericWildcard;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveKind;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

public final class CompilerCommons {
    static final GenericClass booleanClass = new GenericClass("java.lang.Boolean");
    static final GenericClass characterClass = new GenericClass("java.lang.Character");
    static final GenericClass byteClass = new GenericClass("java.lang.Byte");
    static final GenericClass shortClass = new GenericClass("java.lang.Short");
    static final GenericClass integerClass = new GenericClass("java.lang.Integer");
    static final GenericClass longClass = new GenericClass("java.lang.Long");
    static final GenericClass floatClass = new GenericClass("java.lang.Float");
    static final GenericClass doubleClass = new GenericClass("java.lang.Double");
    static final GenericClass stringClass = new GenericClass("java.lang.String");
    static final Set<ValueType> classesSuitableForComparison = new HashSet<>(Arrays.<ValueType>asList(
            characterClass, byteClass, shortClass, integerClass, longClass,
            floatClass, doubleClass, Primitive.BYTE, Primitive.CHAR, Primitive.SHORT,
            Primitive.INT, Primitive.LONG, Primitive.FLOAT, Primitive.DOUBLE));
    static final Map<Primitive, GenericClass> primitivesToWrappers = new HashMap<>();
    static final Map<GenericClass, Primitive> wrappersToPrimitives = new HashMap<>();
    private static ValueType[] orderedNumericTypes = { Primitive.BYTE, Primitive.SHORT, Primitive.INT, Primitive.LONG,
            Primitive.FLOAT, Primitive.DOUBLE };

    static {
        primitiveAndWrapper(Primitive.BOOLEAN, booleanClass);
        primitiveAndWrapper(Primitive.CHAR, characterClass);
        primitiveAndWrapper(Primitive.BYTE, byteClass);
        primitiveAndWrapper(Primitive.SHORT, shortClass);
        primitiveAndWrapper(Primitive.INT, integerClass);
        primitiveAndWrapper(Primitive.LONG, longClass);
        primitiveAndWrapper(Primitive.FLOAT, floatClass);
        primitiveAndWrapper(Primitive.DOUBLE, doubleClass);
    }

    static void primitiveAndWrapper(Primitive primitive, GenericClass wrapper) {
        primitivesToWrappers.put(primitive, wrapper);
        wrappersToPrimitives.put(wrapper, primitive);
    }

    private CompilerCommons() {
    }

    static ValueType commonSupertype(ValueType a, ValueType b, GenericTypeNavigator navigator) {
        if (a instanceof Primitive && b instanceof Primitive) {
            if (a == Primitive.BOOLEAN && b == Primitive.BOOLEAN) {
                return Primitive.BOOLEAN;
            } else if (a == Primitive.CHAR && b == Primitive.CHAR) {
                return Primitive.CHAR;
            }
            int p = numericTypeToOrder(((Primitive) a).getKind());
            int q = numericTypeToOrder(((Primitive) b).getKind());
            if (p < 0 || q < 0) {
                return null;
            }
            return orderedNumericTypes[Math.max(p, q)];
        } else if (a instanceof Primitive) {
            a = primitivesToWrappers.get(a);
        } else if (b instanceof Primitive) {
            b = primitivesToWrappers.get(b);
        }

        TypeInference inference = new TypeInference(navigator);
        GenericReference common = new GenericReference(new TypeVar());
        if (inference.subtypeConstraint((GenericType) a, common)
                && inference.subtypeConstraint((GenericType) b, common)) {
            return getType(common.substitute(inference.getSubstitutions()));
        }
        return null;
    }

    static int numericTypeToOrder(PrimitiveKind kind) {
        switch (kind) {
            case BYTE:
                return 0;
            case SHORT:
                return 1;
            case INT:
                return 2;
            case LONG:
                return 3;
            case FLOAT:
                return 4;
            case DOUBLE:
                return 5;
            default:
                break;
        }
        return -1;
    }


    static ValueType getType(ArithmeticType type) {
        switch (type) {
            case DOUBLE:
                return Primitive.DOUBLE;
            case FLOAT:
                return Primitive.FLOAT;
            case INT:
                return Primitive.INT;
            case LONG:
                return Primitive.LONG;
        }
        throw new AssertionError("Unexpected arithmetic type: " + type);
    }

    static ArithmeticType getArithmeticType(PrimitiveKind kind) {
        switch (kind) {
            case INT:
                return ArithmeticType.INT;
            case LONG:
                return ArithmeticType.LONG;
            case FLOAT:
                return ArithmeticType.FLOAT;
            case DOUBLE:
                return ArithmeticType.DOUBLE;
            default:
                return null;
        }
    }

    static IntegerSubtype getIntegerSubtype(PrimitiveKind kind) {
        switch (kind) {
            case BYTE:
                return IntegerSubtype.BYTE;
            case SHORT:
                return IntegerSubtype.SHORT;
            case CHAR:
                return IntegerSubtype.CHAR;
            default:
                return null;
        }
    }

    static int arithmeticSize(PrimitiveKind kind) {
        switch (kind) {
            case BYTE:
                return 0;
            case SHORT:
                return 1;
            case INT:
                return 2;
            case LONG:
                return 3;
            case FLOAT:
                return 4;
            case DOUBLE:
                return 5;
            default:
                return -1;
        }
    }

    static GenericType getType(GenericType type) {
        if (type instanceof GenericReference) {
            TypeVar var = ((GenericReference) type).getVar();
            if (var.getLowerBound().size() == 1) {
                type = var.getLowerBound().get(0);
            }
        } else if (type instanceof GenericWildcard) {
            GenericWildcard wildcard = (GenericWildcard) type;
            if (wildcard.getLowerBound().size() == 1) {
                type = wildcard.getLowerBound().get(0);
            }
        }
        return type;
    }

    static boolean hasImplicitConversion(PrimitiveKind from, PrimitiveKind to) {
        if (from == to) {
            return true;
        }
        if (from == PrimitiveKind.BOOLEAN || to == PrimitiveKind.BOOLEAN) {
            return false;
        }
        if (from == PrimitiveKind.CHAR) {
            switch (to) {
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                    return true;
                default:
                    return false;
            }
        } else if (to == PrimitiveKind.CHAR) {
            return from == PrimitiveKind.BYTE;
        } else {
            int a = arithmeticSize(from);
            int b = arithmeticSize(to);
            if (a < 0 || b < 0) {
                return false;
            }
            return a < b;
        }
    }

    static ValueType unbox(ValueType type) {
        if (type instanceof GenericReference) {
            TypeVar v = ((GenericReference) type).getVar();
            return v.getLowerBound().stream()
                    .map(CompilerCommons.wrappersToPrimitives::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } else if (type instanceof GenericWildcard) {
            GenericWildcard wildcard = (GenericWildcard) type;
            return wildcard.getLowerBound().stream()
                    .map(CompilerCommons.wrappersToPrimitives::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return CompilerCommons.wrappersToPrimitives.get(type);
    }

    static GenericType box(ValueType type) {
        GenericClass wrapper = CompilerCommons.primitivesToWrappers.get(type);
        if (wrapper == null) {
            return (GenericType) type;
        }
        return wrapper;
    }

    static boolean tryCastPrimitive(Primitive source, Primitive target) {
        if (source.getKind() == PrimitiveKind.BOOLEAN) {
            return target == Primitive.BOOLEAN;
        } else {
            IntegerSubtype subtype = CompilerCommons.getIntegerSubtype(source.getKind());
            if (subtype != null) {
                source = Primitive.INT;
            }
            ArithmeticType sourceArithmetic = CompilerCommons.getArithmeticType(source.getKind());
            if (sourceArithmetic == null) {
                return false;
            }
            subtype = CompilerCommons.getIntegerSubtype(target.getKind());
            ArithmeticType targetArithmetic = CompilerCommons.getArithmeticType(target.getKind());
            if (targetArithmetic == null) {
                if (subtype == null) {
                    return false;
                }
            }
            return true;
        }
    }

    static Collection<GenericClass> extractClasses(ValueType type) {
        List<GenericClass> classes = new ArrayList<>();
        if (type instanceof Primitive) {
            type = CompilerCommons.box(type);
            if (type instanceof GenericClass) {
                classes.add((GenericClass) type);
            }
        } else if (type instanceof GenericReference) {
            TypeVar var = ((GenericReference) type).getVar();
            classes.addAll(var.getLowerBound().stream()
                    .filter(bound -> bound instanceof GenericClass)
                    .map(bound -> (GenericClass) bound)
                    .collect(Collectors.toList()));
        } else if (type instanceof GenericWildcard) {
            GenericWildcard wildcard = (GenericWildcard) type;
            classes.addAll(wildcard.getLowerBound().stream()
                    .filter(bound -> bound instanceof GenericClass)
                    .map(bound -> (GenericClass) bound)
                    .collect(Collectors.toList()));
        } else if (type instanceof GenericClass) {
            classes.add((GenericClass) type);
        }

        if (classes.isEmpty()) {
            classes.add(new GenericClass("java.lang.Object"));
        }
        return classes;
    }


    public static String typeToString(ValueType type) {
        StringBuilder sb = new StringBuilder();
        typeToString(type, sb);
        return sb.toString();
    }

    public static void typeToString(ValueType type, StringBuilder sb) {
        if (type instanceof Primitive) {
            switch (((Primitive) type).getKind()) {
                case BOOLEAN:
                    sb.append('Z');
                    break;
                case CHAR:
                    sb.append('C');
                    break;
                case BYTE:
                    sb.append('B');
                    break;
                case SHORT:
                    sb.append('S');
                    break;
                case INT:
                    sb.append('I');
                    break;
                case LONG:
                    sb.append('J');
                    break;
                case FLOAT:
                    sb.append('F');
                    break;
                case DOUBLE:
                    sb.append('D');
                    break;
            }
        } else if (type instanceof GenericArray) {
            sb.append('[');
            typeToString(((GenericArray) type).getElementType(), sb);
        } else if (type instanceof GenericClass) {
            sb.append('L').append(((GenericClass) type).getName().replace('.', '/')).append(';');
        } else if (type instanceof GenericReference) {
            TypeVar var = ((GenericReference) type).getVar();
            if (var.getLowerBound().size() == 1) {
                typeToString(var.getLowerBound().get(0), sb);
            } else {
                sb.append("Ljava/lang/Object;");
            }
        } else if (type instanceof GenericWildcard) {
            GenericWildcard wildcard = (GenericWildcard) type;
            if (wildcard.getLowerBound().size() == 1) {
                typeToString(wildcard.getLowerBound().get(0), sb);
            } else {
                sb.append("Ljava/lang/Object;");
            }
        }
    }

    public static String methodToDesc(MethodDescriber method) {
        StringBuilder desc = new StringBuilder().append('(');
        for (ValueType argType : method.getRawArgumentTypes()) {
            desc.append(typeToString(argType));
        }
        desc.append(')');
        if (method.getRawReturnType() != null) {
            desc.append(typeToString(method.getRawReturnType()));
        } else {
            desc.append('V');
        }
        return desc.toString();
    }
}
