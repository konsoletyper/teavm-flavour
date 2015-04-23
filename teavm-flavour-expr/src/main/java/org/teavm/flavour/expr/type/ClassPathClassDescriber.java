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
package org.teavm.flavour.expr.type;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 *
 * @author Alexey Andreev
 */
class ClassPathClassDescriber implements ClassDescriber {
    ClassPathClassDescriberRepository repository;
    private Class<?> cls;
    private TypeVar[] typeVariables;
    private GenericClass supertype;
    private GenericClass[] interfaces;

    public ClassPathClassDescriber(ClassPathClassDescriberRepository repository, Class<?> cls) {
        this.repository = repository;
        this.cls = cls;
    }

    @Override
    public String getName() {
        return cls.getName();
    }

    @Override
    public TypeVar[] getTypeVariables() {
        if (typeVariables == null) {
            TypeVariable<?>[] javaTypeVariables = cls.getTypeParameters();
            typeVariables = new TypeVar[javaTypeVariables.length];
            for (int i = 0; i < typeVariables.length; ++i) {
                typeVariables[i] = repository.getTypeVariable(javaTypeVariables[i]);
            }
        }
        return typeVariables;
    }

    @Override
    public GenericClass getSupertype() {
        if (supertype == null) {
            supertype = cls.getGenericSuperclass() != null ?
                    (GenericClass)repository.convertGenericType(cls.getGenericSuperclass()) : null;
        }
        return supertype;
    }

    @Override
    public GenericClass[] getInterfaces() {
        if (interfaces == null) {
            Type[] javaInterfaces = cls.getGenericInterfaces();
            interfaces = new GenericClass[javaInterfaces.length];
            for (int i = 0; i < javaInterfaces.length; ++i) {
                interfaces[i] = (GenericClass)repository.convertGenericType(javaInterfaces[i]);
            }
        }
        return interfaces.clone();
    }

    @Override
    public MethodDescriber[] getMethods() {
        return null;
    }
}
