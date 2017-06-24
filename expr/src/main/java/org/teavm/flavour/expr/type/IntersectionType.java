/*
 *  Copyright 2017 Alexey Andreev.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class IntersectionType extends GenericType {
    private Set<? extends GenericType> types;

    private IntersectionType(Set<? extends GenericType> types) {
        this.types = Collections.unmodifiableSet(types);
    }

    public static GenericType of(GenericType... types) {
        return of(Arrays.asList(types));
    }

    public static GenericType of(Collection<? extends GenericType> types) {
        Set<GenericType> flattenedTypes = new LinkedHashSet<>();
        for (GenericType type : types) {
            if (type instanceof IntersectionType) {
                flattenedTypes.addAll(((IntersectionType) type).getTypes());
            } else {
                flattenedTypes.add(type);
            }
        }
        return flattenedTypes.size() == 1 ? flattenedTypes.iterator().next() : new IntersectionType(flattenedTypes);
    }

    public Set<? extends GenericType> getTypes() {
        return types;
    }

    @Override
    GenericType substitute(Substitutions substitutions, Set<TypeVar> visited) {
        List<GenericType> mappedTypes = new ArrayList<>();
        boolean changed = false;
        for (GenericType type : types) {
            GenericType mappedType = type.substitute(substitutions, visited);
            if (mappedType != type) {
                changed = true;
            }
            mappedTypes.add(mappedType);
        }
        return changed ? IntersectionType.of(mappedTypes) : this;
    }

    @Override
    GenericType substituteArgs(Function<TypeVar, TypeArgument> substitutions, Set<TypeVar> visited) {
        List<GenericType> mappedTypes = new ArrayList<>();
        boolean changed = false;
        for (GenericType type : types) {
            GenericType mappedType = type.substituteArgs(substitutions, visited);
            if (mappedType != type) {
                changed = true;
            }
            mappedTypes.add(mappedType);
        }
        return changed ? IntersectionType.of(mappedTypes) : this;
    }

    @Override
    public GenericType erasure() {
        List<GenericType> erasedTypes = new ArrayList<>();
        boolean changed = false;
        for (GenericType type : types) {
            GenericType erasedType = type.erasure();
            if (erasedType != type) {
                changed = true;
            }
            erasedTypes.add(erasedType);
        }
        return changed ? IntersectionType.of(erasedTypes) : this;
    }

    @Override
    public int hashCode() {
        return types.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IntersectionType)) {
            return false;
        }
        IntersectionType that = (IntersectionType) obj;
        return types.equals(that.types);
    }
}
