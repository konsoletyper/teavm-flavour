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

/**
 *
 * @author Alexey Andreev
 */
class ClassPathConstructorDescriber extends ClassPathAbstractMethodDescriber {
    private Constructor<?> javaConstructor;

    public ClassPathConstructorDescriber(ClassPathClassDescriber classDescriber, Constructor<?> javaConstructor) {
        super(classDescriber);
        this.javaConstructor = javaConstructor;
    }

    @Override
    public String getName() {
        return "<init>";
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(javaConstructor.getModifiers());
    }

    @Override
    TypeVariable<?>[] getJavaTypeVariables() {
        return javaConstructor.getTypeParameters();
    }

    @Override
    Type[] getJavaArgumentTypes() {
        return javaConstructor.getGenericParameterTypes();
    }

    @Override
    Class<?>[] getJavaRawArgumentTypes() {
        return javaConstructor.getParameterTypes();
    }

    @Override
    Type getJavaReturnType() {
        return void.class;
    }

    @Override
    Type getJavaRawReturnType() {
        return void.class;
    }

    @Override
    AnnotatedElement getAnnotatedElement() {
        return javaConstructor;
    }
}
