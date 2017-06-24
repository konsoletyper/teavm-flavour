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
package org.teavm.flavour.expr.type.meta;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.IntersectionType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;

public class ClassPathClassDescriberRepository implements ClassDescriberRepository {
    private static final Map<String, Primitive> primitiveMap = new HashMap<>();
    ClassLoader classLoader;
    private Map<String, Holder> cache = new HashMap<>();
    private Map<TypeVariable<?>, TypeVar> typeVarCache = new HashMap<>();

    static {
        primitiveMap.put("boolean", Primitive.BOOLEAN);
        primitiveMap.put("char", Primitive.CHAR);
        primitiveMap.put("byte", Primitive.BYTE);
        primitiveMap.put("short", Primitive.SHORT);
        primitiveMap.put("int", Primitive.INT);
        primitiveMap.put("long", Primitive.LONG);
        primitiveMap.put("float", Primitive.FLOAT);
        primitiveMap.put("double", Primitive.DOUBLE);
    }

    public ClassPathClassDescriberRepository() {
        this(ClassLoader.getSystemClassLoader());
    }

    public ClassPathClassDescriberRepository(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public ClassDescriber describe(String className) {
        Holder holder = cache.get(className);
        if (holder == null) {
            holder = new Holder();
            try {
                holder.classDescriber = new ClassPathClassDescriber(this,
                        Class.forName(className, false, classLoader));
            } catch (ClassNotFoundException e) {
                // Leave holder.classDescriber null
            }
            cache.put(className, holder);
        }
        return holder.classDescriber;
    }

    static class Holder {
        ClassPathClassDescriber classDescriber;
    }

    TypeVar getTypeVariable(TypeVariable<?> javaVar) {
        TypeVar var = typeVarCache.get(javaVar);
        if (var == null) {
            var = new TypeVar(javaVar.getName());
            typeVarCache.put(javaVar, var);
            Type[] javaBounds = javaVar.getBounds();
            if (javaBounds.length > 0) {
                var.withUpperBound((GenericClass) convertGenericType(javaBounds[0]));
            }
        }
        return var;
    }

    public ValueType convertGenericType(Type javaType) {
        if (javaType instanceof Class<?>) {
            Class<?> javaClass = (Class<?>) javaType;
            if (javaClass.isPrimitive()) {
                return primitiveMap.get(javaClass.getName());
            } else if (javaClass.isArray()) {
                if (javaClass.getComponentType().isPrimitive()) {
                    return new PrimitiveArray((Primitive) convertGenericType(javaClass.getComponentType()));
                } else {
                    return new GenericArray((GenericType) convertGenericType(javaClass.getComponentType()));
                }
            }
            return new GenericClass(javaClass.getName(), Collections.emptyList());
        } else if (javaType instanceof ParameterizedType) {
            ParameterizedType javaGenericType = (ParameterizedType) javaType;
            Class<?> javaRawClass = (Class<?>) javaGenericType.getRawType();
            Type[] javaArgs = javaGenericType.getActualTypeArguments();
            TypeArgument[] args = new TypeArgument[javaArgs.length];
            for (int i = 0; i < args.length; ++i) {
                args[i] = convertTypeArgument(javaArgs[i]);
            }
            return new GenericClass(javaRawClass.getName(), Arrays.asList(args));
        } else if (javaType instanceof GenericArrayType) {
            GenericArrayType javaArray = (GenericArrayType) javaType;
            return new GenericArray((GenericType) convertGenericType(javaArray.getGenericComponentType()));
        } else if (javaType instanceof TypeVariable<?>) {
            TypeVariable<?> javaVar = (TypeVariable<?>) javaType;
            return new GenericReference(getTypeVariable(javaVar));
        } else {
            throw new AssertionError("Unsupported type: " + javaType);
        }
    }

    private TypeArgument convertTypeArgument(Type javaType) {
         if (javaType instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) javaType;
            Type[] upperBounds = wildcard.getUpperBounds();
            Type[] lowerBounds = wildcard.getLowerBounds();
            if (lowerBounds.length > 0) {
                return TypeArgument.contravariant(IntersectionType.of(Arrays.stream(lowerBounds)
                        .map(bound -> (GenericType) convertGenericType(bound))
                        .collect(Collectors.toList())));
            } else if (upperBounds.length > 0) {
                return TypeArgument.covariant(IntersectionType.of(Arrays.stream(upperBounds)
                        .map(bound -> (GenericType) convertGenericType(bound))
                        .collect(Collectors.toList())));
            } else {
                return TypeArgument.covariant(GenericType.OBJECT);
            }
        } else {
            return TypeArgument.invariant((GenericType) convertGenericType(javaType));
        }
    }

    Class<?> convertToRawType(ValueType type) {
        if (type instanceof Primitive) {
            switch (((Primitive) type).getKind()) {
                case BOOLEAN:
                    return boolean.class;
                case CHAR:
                    return char.class;
                case BYTE:
                    return byte.class;
                case SHORT:
                    return short.class;
                case INT:
                    return int.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                case DOUBLE:
                    return double.class;
                default:
                    throw new AssertionError();
            }
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray) type;
            return Array.newInstance(convertToRawType(array.getElementType()), 0).getClass();
        } else if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass) type;
            try {
                return Class.forName(cls.getName(), false, classLoader);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Class not found: " + cls.getName());
            }
        } else if (type instanceof GenericReference) {
            return Object.class;
        } else {
            throw new AssertionError("Can't convert type: " + type);
        }
    }
}
