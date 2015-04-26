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

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
    private ClassPathMethodDescriber[] methods;
    private Map<Method, ClassPathMethodDescriber> methodMap = new HashMap<>();
    private ClassPathFieldDescriber[] fields;
    private Map<Field, ClassPathFieldDescriber> fieldMap = new HashMap<>();

    public ClassPathClassDescriber(ClassPathClassDescriberRepository repository, Class<?> cls) {
        this.repository = repository;
        this.cls = cls;
    }

    @Override
    public String getName() {
        return cls.getName();
    }

    @Override
    public boolean isInterface() {
        return Modifier.isInterface(cls.getModifiers());
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
        if (methods == null) {
            Method[] javaMethods = cls.getDeclaredMethods();
            methods = new ClassPathMethodDescriber[javaMethods.length];
            int j = 0;
            for (int i = 0; i < methods.length; ++i) {
                ClassPathMethodDescriber method = getMethod(javaMethods[i]);
                if (method != null) {
                    methods[j++] = method;
                }
            }
            methods = Arrays.copyOf(methods, j);
        }
        return methods.clone();
    }

    @Override
    public MethodDescriber getMethod(String name, GenericClass... argumentTypes) {
        Class<?>[] javaArgs = new Class<?>[argumentTypes.length];
        for (int i = 0; i < javaArgs.length; ++i) {
            javaArgs[i] = repository.convertToRawType(argumentTypes[i]);
        }
        try {
            Method javaMethod = cls.getDeclaredMethod(name, javaArgs);
            return getMethod(javaMethod);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private ClassPathMethodDescriber getMethod(Method javaMethod) {
        if (!Modifier.isPublic(javaMethod.getModifiers())) {
            return null;
        }
        ClassPathMethodDescriber method = methodMap.get(javaMethod);
        if (method == null) {
            method = new ClassPathMethodDescriber(this, javaMethod);
            methodMap.put(javaMethod, method);
        }
        return method;
    }

    @Override
    public FieldDescriber[] getFields() {
        if (fields == null) {
            Field[] javaFields = cls.getDeclaredFields();
            fields = new ClassPathFieldDescriber[javaFields.length];
            int j = 0;
            for (int i = 0; i < fields.length; ++i) {
                ClassPathFieldDescriber field = getField(javaFields[i]);
                if (field != null) {
                    fields[j++] = field;
                }
            }
            fields = Arrays.copyOf(fields, j);
        }
        return fields.clone();
    }

    @Override
    public FieldDescriber getField(String name) {
        try {
            Field javaField = cls.getDeclaredField(name);
            return getField(javaField);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private ClassPathFieldDescriber getField(Field javaField) {
        if (!Modifier.isPublic(javaField.getModifiers())) {
            return null;
        }
        ClassPathFieldDescriber field = fieldMap.get(javaField);
        if (field == null) {
            field = new ClassPathFieldDescriber(this, javaField);
            fieldMap.put(javaField, field);
        }
        return field;
    }
}
