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

import java.util.Map;
import java.util.Objects;

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
    public GenericType substitute(Map<TypeVar, GenericType> substitutions) {
        GenericType substitution = substitutions.get(var);
        return substitution != null ? substitution : this;
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
        GenericReference other = (GenericReference)obj;
        return var == other.var;
    }

    @Override
    public GenericType erasure() {
        if (var.getUpperBound() == null) {
            return new GenericClass("java.lang.Object");
        } else {
            return var.getUpperBound().erasure();
        }
    }
}
