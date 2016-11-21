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
package org.teavm.flavour.rest.impl.model;

import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.reflect.ReflectField;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class PropertyModel implements Cloneable {
    BeanModel bean;
    String name;
    ReflectClass<?> type;
    ReflectField field;
    ReflectMethod getter;
    ReflectMethod setter;
    String targetName;
    Usage usage;

    public BeanModel getBean() {
        return bean;
    }

    public String getName() {
        return name;
    }

    public ReflectClass<?> getType() {
        return type;
    }

    public ReflectField getField() {
        return field;
    }

    public ReflectMethod getGetter() {
        return getter;
    }

    public String getTargetName() {
        return targetName;
    }

    public Usage getUsage() {
        return usage;
    }

    @Override
    public PropertyModel clone() {
        try {
            return (PropertyModel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
