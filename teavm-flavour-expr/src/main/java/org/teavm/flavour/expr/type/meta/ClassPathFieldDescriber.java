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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class ClassPathFieldDescriber extends ClassPathAnnotationsDescriber implements FieldDescriber {
    private ClassPathClassDescriber owner;
    private Field javaField;
    private ValueType type;
    private ValueType rawType;

    public ClassPathFieldDescriber(ClassPathClassDescriber owner, Field javaField) {
        this.owner = owner;
        this.javaField = javaField;
    }

    @Override
    public ClassDescriber getOwner() {
        return owner;
    }

    @Override
    public String getName() {
        return javaField.getName();
    }

    @Override
    public ValueType getType() {
        if (type == null) {
            type = owner.repository.convertGenericType(javaField.getGenericType());
        }
        return type;
    }

    @Override
    public ValueType getRawType() {
        if (rawType == null) {
            rawType = owner.repository.convertGenericType(javaField.getType());
        }
        return rawType;
    }

    @Override
    public boolean isStatic() {
        return Modifier.isStatic(javaField.getModifiers());
    }

    @Override
    AnnotatedElement getAnnotatedElement() {
        return javaField;
    }

    @Override
    ClassPathClassDescriberRepository getRepository() {
        return owner.repository;
    }
}
