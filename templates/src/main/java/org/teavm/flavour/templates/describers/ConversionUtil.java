/*
 *  Copyright 2019 konsoletyper.
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
package org.teavm.flavour.templates.describers;

import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.Variance;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.reflect.ReflectArrayType;
import org.teavm.metaprogramming.reflect.ReflectMethod;
import org.teavm.metaprogramming.reflect.ReflectParameterizedMember;
import org.teavm.metaprogramming.reflect.ReflectParameterizedType;
import org.teavm.metaprogramming.reflect.ReflectType;
import org.teavm.metaprogramming.reflect.ReflectTypeArgument;
import org.teavm.metaprogramming.reflect.ReflectTypeVariable;

final class ConversionUtil {
    private ConversionUtil() {
    }

    static ValueType mapType(ReflectClassDescriberRepository repository, ReflectClass<?> type) {

    }

    static ValueType mapType(ReflectClassDescriberRepository repository, ReflectType type) {
        if (type instanceof ReflectParameterizedType) {
            ReflectParameterizedType paramType = (ReflectParameterizedType) type;
            ReflectClass<?> typeCons = paramType.getTypeConstructor();
            if (typeCons.isPrimitive()) {
                return mapPrimitive(typeCons.getName());
            } else if (typeCons.isArray()) {
                return mapArray(repository, typeCons);
            } else {
                ReflectTypeArgument[] origArguments = paramType.getTypeArguments();
                TypeArgument[] arguments = new TypeArgument[origArguments.length];
                for (int i = 0; i < arguments.length; ++i) {
                    GenericType constraint = (GenericType) mapType(repository, origArguments[i].getConstraint());
                    Variance variance;
                    switch (origArguments[i].getVariance()) {
                        case INVARIANT:
                            variance = Variance.INVARIANT;
                            break;
                        case COVARIANT:
                            variance = Variance.COVARIANT;
                            break;
                        case CONTRAVARIANT:
                            variance = Variance.CONTRAVARIANT;
                            break;
                        default:
                            throw new RuntimeException();
                    }
                    arguments[i] = new TypeArgument(variance, constraint);
                }
                return new GenericClass(typeCons.getName(), arguments);
            }
        } else if (type instanceof ReflectArrayType) {
            ReflectArrayType arrayType = (ReflectArrayType) type;
            GenericType componentType = (GenericType) mapType(repository, arrayType.getComponentType());
            return new GenericArray(componentType);
        } else if (type instanceof ReflectTypeVariable) {
            ReflectTypeVariable typeVariable = (ReflectTypeVariable) type;
            ReflectParameterizedMember owner = typeVariable.getOwner();
            ReflectTypeVariable[] siblings = owner.getTypeParameters();
            int index = 0;
            while (siblings[index] != typeVariable) {
                index++;
            }
            if (owner instanceof ReflectClass) {
                ClassDescriberImpl containingClass = repository.describe(owner.getName());
                return new GenericReference(containingClass.getTypeVariables()[index]);
            } else if (owner instanceof ReflectMethod) {
                ClassDescriberImpl containingClass = repository.describe(owner.getName());
            } else {
                throw new RuntimeException("Unsupported type parameter owner");
            }
        }
    }

    private static ValueType mapArray(ReflectClassDescriberRepository repository, ReflectClass<?> cls) {
        ReflectClass<?> componentType = cls.getComponentType();
        if (componentType.isPrimitive()) {
            return new PrimitiveArray(mapPrimitive(componentType.getName()));
        } else {
            return new GenericArray((GenericType) mapType(repository, cls.getComponentType()));
        }
    }

    private static Primitive mapPrimitive(String name) {
        switch (name) {
            case "boolean":
                return Primitive.BOOLEAN;
            case "byte":
                return Primitive.BYTE;
            case "short":
                return Primitive.SHORT;
            case "char":
                return Primitive.CHAR;
            case "int":
                return Primitive.INT;
            case "long":
                return Primitive.LONG;
            case "float":
                return Primitive.FLOAT;
            case "double":
                return Primitive.DOUBLE;
            default:
                throw new IllegalArgumentException("Unknown primitive type: " + name);
        }
    }
}
