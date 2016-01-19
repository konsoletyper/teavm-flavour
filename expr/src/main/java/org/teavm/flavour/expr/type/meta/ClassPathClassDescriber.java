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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class ClassPathClassDescriber extends ClassPathAnnotationsDescriber implements ClassDescriber {
    ClassPathClassDescriberRepository repository;
    private Class<?> cls;
    private TypeVar[] typeVariables;
    private GenericClass supertype;
    private GenericClass[] interfaces;
    private ClassPathAbstractMethodDescriber[] methods;
    private Map<Method, ClassPathMethodDescriber> methodMap = new HashMap<>();
    private Map<Constructor<?>, ClassPathConstructorDescriber> constructorMap = new HashMap<>();
    private ClassPathFieldDescriber[] fields;
    private Map<Field, ClassPathFieldDescriber> fieldMap = new HashMap<>();

    ClassPathClassDescriber(ClassPathClassDescriberRepository repository, Class<?> cls) {
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
            supertype = cls.getGenericSuperclass() != null
                    ? (GenericClass) repository.convertGenericType(cls.getGenericSuperclass()) : null;
        }
        return supertype;
    }

    @Override
    public GenericClass[] getInterfaces() {
        if (interfaces == null) {
            Type[] javaInterfaces = cls.getGenericInterfaces();
            interfaces = new GenericClass[javaInterfaces.length];
            for (int i = 0; i < javaInterfaces.length; ++i) {
                interfaces[i] = (GenericClass) repository.convertGenericType(javaInterfaces[i]);
            }
        }
        return interfaces.clone();
    }

    @Override
    public MethodDescriber[] getMethods() {
        if (methods == null) {
            Method[] javaMethods = cls.getDeclaredMethods();
            Constructor<?>[] javaConstructors = cls.getDeclaredConstructors();
            methods = new ClassPathAbstractMethodDescriber[javaMethods.length + javaConstructors.length];
            int j = 0;
            for (int i = 0; i < javaMethods.length; ++i) {
                ClassPathMethodDescriber method = getMethod(javaMethods[i]);
                if (method != null) {
                    methods[j++] = method;
                }
            }
            for (int i = 0; i < javaConstructors.length; ++i) {
                ClassPathConstructorDescriber method = getMethod(javaConstructors[i]);
                if (method != null) {
                    methods[j++] = method;
                }
            }
            methods = Arrays.copyOf(methods, j);
        }
        return methods.clone();
    }

    @Override
    public MethodDescriber getMethod(String name, ValueType... argumentTypes) {
        Class<?>[] javaArgs = new Class<?>[argumentTypes.length];
        for (int i = 0; i < javaArgs.length; ++i) {
            javaArgs[i] = repository.convertToRawType(argumentTypes[i]);
        }
        try {
            if (name.equals("<init>")) {
                Constructor<?> javaConstructor = cls.getDeclaredConstructor(javaArgs);
                return getMethod(javaConstructor);
            } else {
                Method javaMethod = cls.getDeclaredMethod(name, javaArgs);
                return getMethod(javaMethod);
            }
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

    private ClassPathConstructorDescriber getMethod(Constructor<?> javaConstructor) {
        if (!Modifier.isPublic(javaConstructor.getModifiers())) {
            return null;
        }
        ClassPathConstructorDescriber ctor = constructorMap.get(javaConstructor);
        if (ctor == null) {
            ctor = new ClassPathConstructorDescriber(this, javaConstructor);
            constructorMap.put(javaConstructor, ctor);
        }
        return ctor;
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

    @Override
    AnnotatedElement getAnnotatedElement() {
        return cls;
    }

    @Override
    ClassPathClassDescriberRepository getRepository() {
        return repository;
    }
}
