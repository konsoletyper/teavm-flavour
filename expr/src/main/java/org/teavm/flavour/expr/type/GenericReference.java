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

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class GenericReference extends GenericType {
    private TypeVar var;

    public GenericReference(TypeVar var) {
        Objects.requireNonNull(var);
        this.var = var;
    }

    public TypeVar getVar() {
        return var;
    }

    @Override
    GenericType substitute(Substitutions substitutions, Set<TypeVar> visited) {
        try {
            if (!visited.add(var)) {
                return this;
            }
            GenericType substitution = substitutions.get(var);
            if (substitution == null) {
                return this;
            }
            return substitution != this ? substitution.substitute(substitutions, visited) : substitution;
        } finally {
            visited.remove(var);
        }
    }

    @Override
    public GenericType substituteArgs(Function<TypeVar, TypeArgument> substitutions) {
        return this;
    }

    @Override
    GenericType substituteArgs(Function<TypeVar, TypeArgument> substitutions, Set<TypeVar> visited) {
        return this;
    }

    @Override
    public int hashCode() {
        return 31 * var.hashCode() + 13;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GenericReference)) {
            return false;
        }
        GenericReference other = (GenericReference) obj;
        return var == other.var;
    }

    @Override
    public GenericType erasure() {
        if (var.getUpperBound().size() != 1) {
            return GenericType.OBJECT;
        } else {
            return var.getUpperBound().iterator().next();
        }
    }
}
