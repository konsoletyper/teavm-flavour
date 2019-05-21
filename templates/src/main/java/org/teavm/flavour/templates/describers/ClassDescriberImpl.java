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

import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.AnnotationDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.FieldDescriber;
import org.teavm.flavour.expr.type.meta.MethodDescriber;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.reflect.ReflectType;
import org.teavm.metaprogramming.reflect.ReflectTypeVariable;

public class ClassDescriberImpl implements ClassDescriber {
    private ReflectClassDescriberRepository repository;
    private ReflectClass<?> underlyingClass;
    private TypeVar[] typeVariables;

    ClassDescriberImpl(ReflectClassDescriberRepository repository, ReflectClass<?> underlyingClass) {
        this.repository = repository;
        this.underlyingClass = underlyingClass;
    }

    @Override
    public String getName() {
        return underlyingClass.getName();
    }

    @Override
    public boolean isInterface() {
        return underlyingClass.isInterface();
    }

    @Override
    public TypeVar[] getTypeVariables() {
        if (typeVariables == null) {
            ReflectTypeVariable[] underlyingParameters = underlyingClass.getTypeParameters();
            typeVariables = new TypeVar[underlyingParameters.length];
            for (int i = 0; i < typeVariables.length; ++i) {
                ReflectTypeVariable underlyingParameter = underlyingParameters[i];
                typeVariables[i] = new TypeVar(underlyingParameter.getName());
            }
            for (int i = 0; i < typeVariables.length; ++i) {
                ReflectTypeVariable underlyingParameter = underlyingParameters[i];
                ReflectType[] underlyingBounds = underlyingParameter.getBounds();
                GenericType[] bounds = new GenericType[underlyingBounds.length];
                for (int j = 0; j < bounds.length; ++j) {
                    bounds[j] = (GenericType) ConversionUtil.mapType(repository, underlyingBounds[j]);
                }
                typeVariables[i].withUpperBound(bounds);
            }
        }
        return typeVariables;
    }

    @Override
    public GenericClass getSupertype() {
        return null;
    }

    @Override
    public GenericClass[] getInterfaces() {
        return new GenericClass[0];
    }

    @Override
    public MethodDescriber[] getMethods() {
        return new MethodDescriber[0];
    }

    @Override
    public MethodDescriber getMethod(String name, ValueType... parameterTypes) {
        return null;
    }

    @Override
    public FieldDescriber[] getFields() {
        return new FieldDescriber[0];
    }

    @Override
    public FieldDescriber getField(String name) {
        return null;
    }

    @Override
    public AnnotationDescriber getAnnotation(String className) {
        return null;
    }

    @Override
    public AnnotationDescriber[] getAnnotations() {
        return new AnnotationDescriber[0];
    }
}
