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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class ClassPathMethodDescriber extends ClassPathAnnotationsDescriber implements MethodDescriber {
    private ClassPathClassDescriber owner;
    private Method javaMethod;
    private TypeVar[] typeVariables;
    private ValueType[] argumentTypes;
    private ValueType[] rawArgumentTypes;
    private ValueType returnType;
    private ValueType rawReturnType;

    public ClassPathMethodDescriber(ClassPathClassDescriber classDescriber, Method javaMethod) {
        this.owner = classDescriber;
        this.javaMethod = javaMethod;
    }

    @Override
    public ClassDescriber getOwner() {
        return owner;
    }

    @Override
    public String getName() {
        return javaMethod.getName();
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(javaMethod.getModifiers());
    }

    @Override
    public TypeVar[] getTypeVariables() {
        if (typeVariables == null) {
            TypeVariable<?>[] javaVars = javaMethod.getTypeParameters();
            typeVariables = new TypeVar[javaVars.length];
            for (int i = 0; i < javaVars.length; ++i) {
                typeVariables[i] = owner.repository.getTypeVariable(javaVars[i]);
            }
        }
        return typeVariables.clone();
    }

    @Override
    public ValueType[] getArgumentTypes() {
        if (argumentTypes == null) {
            Type[] javaArgs = javaMethod.getGenericParameterTypes();
            argumentTypes = new ValueType[javaArgs.length];
            for (int i = 0; i < javaArgs.length; ++i) {
                argumentTypes[i] = owner.repository.convertGenericType(javaArgs[i]);
            }
        }
        return argumentTypes.clone();
    }

    @Override
    public ValueType[] getRawArgumentTypes() {
        if (rawArgumentTypes == null) {
            Class<?>[] javaArgs = javaMethod.getParameterTypes();
            rawArgumentTypes = new ValueType[javaArgs.length];
            for (int i = 0; i < javaArgs.length; ++i) {
                rawArgumentTypes[i] = owner.repository.convertGenericType(javaArgs[i]);
            }
        }
        return rawArgumentTypes.clone();
    }

    @Override
    public ValueType getReturnType() {
        if (returnType == null) {
            if (!javaMethod.getReturnType().equals(void.class)) {
                returnType = owner.repository.convertGenericType(javaMethod.getGenericReturnType());
            }
        }
        return returnType;
    }

    @Override
    public ValueType getRawReturnType() {
        if (rawReturnType != null) {
            if (!javaMethod.getReturnType().equals(void.class)) {
                rawReturnType = owner.repository.convertGenericType(javaMethod.getReturnType());
            }
        }
        return rawReturnType;
    }

    @Override
    AnnotatedElement getAnnotatedElement() {
        return javaMethod;
    }

    @Override
    ClassPathClassDescriberRepository getRepository() {
        return owner.repository;
    }
}
