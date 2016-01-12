/*
 *  Copyright 2016 Alexey Andreev.
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

import java.util.Arrays;
import java.util.Objects;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.reflect.ReflectMethod;

/**
 *
 * @author Alexey Andreev
 */
public final class MethodKey {
    private String name;
    private ReflectClass<?>[] parameterTypes;

    public MethodKey(String name, ReflectClass<?>... parameterTypes) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(parameterTypes);
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    public MethodKey(ReflectMethod method) {
        this(method.getName(), method.getParameterTypes());
    }

    public String getName() {
        return name;
    }

    public ReflectClass<?>[] getParameterTypes() {
        return parameterTypes.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MethodKey)) {
            return false;
        }
        MethodKey other = (MethodKey) obj;
        return name.equals(other.name) && Arrays.equals(parameterTypes, other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ Arrays.hashCode(parameterTypes);
    }
}
