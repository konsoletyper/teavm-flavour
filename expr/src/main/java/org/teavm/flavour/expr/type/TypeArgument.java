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

import java.util.Objects;
import java.util.function.Function;

public class TypeArgument {
    private Variance variance;
    private GenericType bound;

    public TypeArgument(Variance variance, GenericType bound) {
        this.variance = variance;
        this.bound = bound;
    }

    public Variance getVariance() {
        return variance;
    }

    public GenericType getBound() {
        return bound;
    }

    public static TypeArgument invariant(GenericType type) {
        Objects.requireNonNull(type);
        return new TypeArgument(Variance.INVARIANT, type);
    }

    public static TypeArgument covariant(GenericType bound) {
        Objects.requireNonNull(bound);
        return new TypeArgument(Variance.COVARIANT, bound);
    }

    public static TypeArgument contravariant(GenericType bound) {
        Objects.requireNonNull(bound);
        return new TypeArgument(Variance.CONTRAVARIANT, bound);
    }

    public TypeArgument mapBound(Function<GenericType, GenericType> f) {
        GenericType mappedBound = f.apply(bound);
        return mappedBound != bound ? new TypeArgument(variance, mappedBound) : this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeArgument that = (TypeArgument) o;
        return variance == that.variance && Objects.equals(bound, that.bound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variance, bound);
    }
}
