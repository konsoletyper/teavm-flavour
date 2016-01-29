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

/**
 *
 * @author Alexey Andreev
 */
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
                if (var.getName() == null) {
                    TypeVar tmpVar = new TypeVar();
                    if (!var.getLowerBound().isEmpty()) {
                        GenericType[] bounds = var.getLowerBound().stream()
                                .map(bound -> bound.substitute(substitutions, visited))
                                .toArray(sz -> new GenericType[sz]);
                        tmpVar.withLowerBound(bounds);
                    } else {
                        GenericType[] bounds = var.getUpperBound().stream()
                                .map(bound -> bound.substitute(substitutions, visited))
                                .toArray(sz -> new GenericType[sz]);
                        tmpVar.withUpperBound(bounds);
                    }
                    return new GenericReference(tmpVar);
                }
                return this;
            }
            return substitution != this ? substitution.substitute(substitutions, visited) : substitution;
        } finally {
            visited.remove(var);
        }
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
            return new GenericClass("java.lang.Object");
        } else {
            return var.getUpperBound().get(0);
        }
    }
}
