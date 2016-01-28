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

/**
 *
 * @author Alexey Andreev
 */
class ClassPathMethodDescriber extends ClassPathAbstractMethodDescriber {
    private Method javaMethod;

    ClassPathMethodDescriber(ClassPathClassDescriber classDescriber, Method javaMethod) {
        super(classDescriber);
        this.javaMethod = javaMethod;
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
    public boolean isAbstract() {
        return Modifier.isAbstract(javaMethod.getModifiers());
    }

    @Override
    TypeVariable<?>[] getJavaTypeVariables() {
        return javaMethod.getTypeParameters();
    }

    @Override
    Type[] getJavaArgumentTypes() {
        return javaMethod.getGenericParameterTypes();
    }

    @Override
    Class<?>[] getJavaRawArgumentTypes() {
        return javaMethod.getParameterTypes();
    }

    @Override
    Type getJavaReturnType() {
        return javaMethod.getGenericReturnType();
    }

    @Override
    Type getJavaRawReturnType() {
        return javaMethod.getReturnType();
    }

    @Override
    AnnotatedElement getAnnotatedElement() {
        return javaMethod;
    }

    @Override
    public boolean isVariableArgument() {
        return javaMethod.isVarArgs();
    }
}
