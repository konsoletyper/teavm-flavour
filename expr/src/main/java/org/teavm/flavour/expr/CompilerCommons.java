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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.plan.ArithmeticType;
import org.teavm.flavour.expr.plan.IntegerSubtype;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.IntersectionType;
import org.teavm.flavour.expr.type.LeastUpperBoundFinder;
import org.teavm.flavour.expr.type.NullType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.PrimitiveKind;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.TypeUtils;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.Variance;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

public final class CompilerCommons {
    static final Set<ValueType> classesSuitableForComparison = new HashSet<>(Arrays.asList(
            TypeUtils.CHARACTER_CLASS, TypeUtils.BYTE_CLASS, TypeUtils.SHORT_CLASS, TypeUtils.INTEGER_CLASS,
            TypeUtils.LONG_CLASS, TypeUtils.FLOAT_CLASS, TypeUtils.DOUBLE_CLASS, Primitive.BYTE, Primitive.CHAR,
            Primitive.SHORT, Primitive.INT, Primitive.LONG, Primitive.FLOAT, Primitive.DOUBLE));

    private static ValueType[] orderedNumericTypes = { Primitive.BYTE, Primitive.SHORT, Primitive.INT, Primitive.LONG,
            Primitive.FLOAT, Primitive.DOUBLE };

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
            a = TypeUtils.tryBox(a);
        } else if (b instanceof Primitive) {
            b = TypeUtils.tryBox(b);
        }

        return new LeastUpperBoundFinder(navigator).find(Arrays.asList((GenericType) a, (GenericType) b));
    }

    private static int numericTypeToOrder(PrimitiveKind kind) {
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

    private static int arithmeticSize(PrimitiveKind kind) {
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

    static boolean tryCastPrimitive(Primitive source, Primitive target) {
        if (source.getKind() == PrimitiveKind.BOOLEAN) {
            return target == Primitive.BOOLEAN;
        } else {
            IntegerSubtype subtype = getIntegerSubtype(source.getKind());
            if (subtype != null) {
                source = Primitive.INT;
            }
            ArithmeticType sourceArithmetic = getArithmeticType(source.getKind());
            if (sourceArithmetic == null) {
                return false;
            }
            subtype = getIntegerSubtype(target.getKind());
            ArithmeticType targetArithmetic = getArithmeticType(target.getKind());
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
            type = TypeUtils.tryBox(type);
            if (type instanceof GenericClass) {
                classes.add((GenericClass) type);
            }
        } else if (type instanceof GenericReference) {
            TypeVar var = ((GenericReference) type).getVar();
            classes.addAll(var.getLowerBound().stream()
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
        } else if (type instanceof PrimitiveArray) {
            sb.append('[');
            typeToString(((PrimitiveArray) type).getElementType(), sb);
        } else if (type instanceof GenericClass) {
            sb.append('L').append(((GenericClass) type).getName().replace('.', '/')).append(';');
        } else if (type instanceof GenericReference) {
            TypeVar var = ((GenericReference) type).getVar();
            if (var.getLowerBound().size() == 1) {
                typeToString(var.getLowerBound().iterator().next(), sb);
            } else {
                sb.append("Ljava/lang/Object;");
            }
        }
    }

    public static String methodToDesc(MethodDescriber method) {
        StringBuilder desc = new StringBuilder().append('(');
        for (ValueType argType : method.getRawParameterTypes()) {
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

    public static boolean isSuperType(ValueType supertype, ValueType subtype, GenericTypeNavigator navigator) {
        if (supertype.equals(subtype)) {
            return true;
        } else if (supertype instanceof Primitive && subtype instanceof Primitive) {
            return TypeUtils.isPrimitiveSubType((Primitive) subtype, (Primitive) supertype);
        } else if (supertype instanceof GenericClass && subtype instanceof GenericClass) {
            GenericClass superclass = (GenericClass) supertype;
            GenericClass subclass = (GenericClass) subtype;
            List<GenericClass> path = navigator.sublassPath(subclass, superclass.getName());
            if (path == null) {
                return false;
            }
            GenericClass candidate = path.get(path.size() - 1);
            for (int i = 0; i < superclass.getArguments().size(); ++i) {
                if (!isContainedBy(candidate.getArguments().get(i), superclass.getArguments().get(i), navigator)) {
                    return false;
                }
            }

            return true;
        } else if (supertype instanceof GenericArray && subtype instanceof GenericArray) {
            return isSuperType(((GenericArray) supertype).getElementType(), ((GenericArray) subtype).getElementType(),
                    navigator);
        } else if (subtype instanceof IntersectionType) {
            Collection<? extends GenericType> types = ((IntersectionType) subtype).getTypes();
            return types.stream().anyMatch(t -> isSuperType(supertype, t, navigator));
        } else if (subtype instanceof GenericReference) {
            TypeVar typeVar = ((GenericReference) subtype).getVar();
            return typeVar.getUpperBound().stream().anyMatch(t -> isSuperType(supertype, t, navigator));
        } else if (supertype instanceof GenericReference) {
            TypeVar typeVar = ((GenericReference) supertype).getVar();
            return typeVar.getLowerBound().stream().anyMatch(t -> isSuperType(t, subtype, navigator));
        } else if (subtype instanceof NullType) {
            return supertype instanceof GenericType;
        }

        return supertype.equals(GenericType.OBJECT) && subtype instanceof GenericType;
    }

    public static boolean isContainedBy(TypeArgument a, TypeArgument b, GenericTypeNavigator navigator) {
        if (a.getVariance() == Variance.COVARIANT && b.getVariance() == Variance.COVARIANT) {
            return isSuperType(a.getBound(), b.getBound(), navigator);
        } else if (a.getVariance() == Variance.CONTRAVARIANT && b.getVariance() == Variance.CONTRAVARIANT) {
            return isSuperType(b.getBound(), a.getBound(), navigator);
        } else if (a.getVariance() == Variance.INVARIANT) {
            return isSuperType(a.getBound(), b.getBound(), navigator);
        } else {
            return false;
        }
    }

    public static boolean isLooselyCompatibleType(ValueType targetType, ValueType sourceType,
            GenericTypeNavigator navigator) {
        if (targetType.equals(sourceType)) {
            return true;
        }
        if (targetType instanceof Primitive) {
            if (sourceType instanceof Primitive) {
                ValueType unboxed = TypeUtils.tryUnbox((GenericType) sourceType);
                if (unboxed == null) {
                    ValueType boxed = TypeUtils.tryBox(targetType);
                    return isSuperType(boxed, sourceType, navigator);
                } else {
                    sourceType = unboxed;
                }
            }
            if (!hasImplicitConversion(((Primitive) sourceType).getKind(), ((Primitive) targetType).getKind())) {
                return false;
            }
            return tryCastPrimitive((Primitive) sourceType, (Primitive) targetType);
        }
        if (sourceType instanceof Primitive) {
            sourceType = TypeUtils.tryBox(sourceType);
            if (sourceType == null) {
                return false;
            }
        }

        return isSuperType(targetType, sourceType, navigator);
    }
}
