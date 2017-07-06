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

import java.lang.reflect.*;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;

abstract class ClassPathAbstractMethodDescriber extends ClassPathAnnotationsDescriber implements MethodDescriber {
    private ClassPathClassDescriber owner;
    private TypeVar[] typeVariables;
    private ValueType[] parameterTypes;
    private ValueType[] rawArgumentTypes;
    private ValueType returnType;
    private ValueType rawReturnType;

    ClassPathAbstractMethodDescriber(ClassPathClassDescriber classDescriber) {
        this.owner = classDescriber;
    }

    @Override
    public ClassDescriber getOwner() {
        return owner;
    }

    @Override
    public TypeVar[] getTypeVariables() {
        if (typeVariables == null) {
            TypeVariable<?>[] javaVars = getJavaTypeVariables();
            typeVariables = new TypeVar[javaVars.length];
            for (int i = 0; i < javaVars.length; ++i) {
                typeVariables[i] = owner.repository.getTypeVariable(javaVars[i]);
            }
        }
        return typeVariables.clone();
    }

    abstract TypeVariable<?>[] getJavaTypeVariables();

    @Override
    public ValueType[] getParameterTypes() {
        if (parameterTypes == null) {
            Type[] javaArgs = getJavaArgumentTypes();
            parameterTypes = new ValueType[javaArgs.length];
            for (int i = 0; i < javaArgs.length; ++i) {
                parameterTypes[i] = owner.repository.convertGenericType(javaArgs[i]);
            }
        }
        return parameterTypes.clone();
    }

    abstract Type[] getJavaArgumentTypes();

    @Override
    public ValueType[] getRawParameterTypes() {
        if (rawArgumentTypes == null) {
            Class<?>[] javaArgs = getJavaRawArgumentTypes();
            rawArgumentTypes = new ValueType[javaArgs.length];
            for (int i = 0; i < javaArgs.length; ++i) {
                rawArgumentTypes[i] = owner.repository.convertGenericType(javaArgs[i]);
            }
        }
        return rawArgumentTypes.clone();
    }

    abstract Class<?>[] getJavaRawArgumentTypes();

    @Override
    public ValueType getReturnType() {
        if (returnType == null) {
            if (!getJavaReturnType().equals(void.class)) {
                returnType = owner.repository.convertGenericType(getJavaReturnType());
            }
        }
        return returnType;
    }

    abstract Type getJavaReturnType();

    @Override
    public ValueType getRawReturnType() {
        if (rawReturnType == null) {
            if (!getJavaRawReturnType().equals(void.class)) {
                rawReturnType = owner.repository.convertGenericType(getJavaRawReturnType());
            }
        }
        return rawReturnType;
    }

    abstract Type getJavaRawReturnType();

    @Override
    ClassPathClassDescriberRepository getRepository() {
        return owner.repository;
    }
}
