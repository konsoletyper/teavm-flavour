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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 *
 * @author Alexey Andreev
 */
class ClassPathAnnotationDescriber implements AnnotationDescriber {
    private Annotation javaAnnotation;

    ClassPathAnnotationDescriber(Annotation javaAnnotation) {
        this.javaAnnotation = javaAnnotation;
    }

    @Override
    public AnnotationValue getValue(String name) {
        Method method;
        try {
            method = javaAnnotation.getClass().getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }

        Object javaValue;
        try {
            javaValue = method.invoke(javaAnnotation);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException();
        }

        return javaValue != null ? convertValue(javaValue) : null;
    }

    AnnotationValue convertValue(Object javaValue) {
        if (javaValue instanceof Boolean) {
            return new AnnotationBoolean((Boolean) javaValue);
        } else if (javaValue instanceof Byte) {
            return new AnnotationByte((Byte) javaValue);
        } else if (javaValue instanceof Short) {
            return new AnnotationShort((Short) javaValue);
        } else if (javaValue instanceof Integer) {
            return new AnnotationInt((Integer) javaValue);
        } else if (javaValue instanceof Long) {
            return new AnnotationLong((Long) javaValue);
        } else if (javaValue instanceof Float) {
            return new AnnotationFloat((Float) javaValue);
        } else if (javaValue instanceof Double) {
            return new AnnotationDouble((Double) javaValue);
        } else if (javaValue instanceof String) {
            return new AnnotationString((String) javaValue);
        } else if (javaValue instanceof Class<?>) {
            return new AnnotationClass(((Class<?>) javaValue).getName());
        } else if (javaValue instanceof Enum<?>) {
            return new AnnotationEnum(javaValue.getClass().getName(), ((Enum<?>) javaValue).name());
        } else if (javaValue.getClass().isArray()) {
            AnnotationValue[] list = new AnnotationValue[Array.getLength(javaValue)];
            for (int i = 0; i < list.length; ++i) {
                list[i] = convertValue(Array.get(javaValue, i));
            }
            return new AnnotationList(Arrays.asList(list));
        } else if (javaValue instanceof Annotation) {
            return new AnnotationReference(new ClassPathAnnotationDescriber((Annotation) javaValue));
        } else {
            throw new AssertionError("Don't know how to convert " + javaValue + " into annotation value");
        }
    }
}
