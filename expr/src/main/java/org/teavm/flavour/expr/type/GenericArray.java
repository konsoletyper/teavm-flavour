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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class GenericArray extends GenericType {
    private GenericType elementType;

    public GenericArray(GenericType elementType) {
        Objects.requireNonNull(elementType);
        this.elementType = elementType;
    }

    public GenericType getElementType() {
        return elementType;
    }

    @Override
    public GenericArray substitute(Substitutions substitutions) {
        return substitute(substitutions, new HashSet<>());
    }

    @Override
    GenericArray substitute(Substitutions substitutions, Set<TypeVar> visited) {
        GenericType substElem = elementType.substitute(substitutions, visited);
        return substElem != elementType ? new GenericArray(substElem) : this;
    }

    @Override
    public GenericArray substituteArgs(Function<TypeVar, TypeArgument> substitutions) {
        return substituteArgs(substitutions, new HashSet<>());
    }

    @Override
    GenericArray substituteArgs(Function<TypeVar, TypeArgument> substitutions, Set<TypeVar> visited) {
        GenericType substElem = elementType.substituteArgs(substitutions, visited);
        return substElem != elementType ? new GenericArray(substElem) : this;
    }

    @Override
    public int hashCode() {
        return 31 * elementType.hashCode() + 13;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GenericArray)) {
            return false;
        }
        GenericArray other = (GenericArray) obj;
        return elementType.equals(other.elementType);
    }

    @Override
    public GenericArray erasure() {
        return new GenericArray(elementType.erasure());
    }
}
