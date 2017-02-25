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
package org.teavm.flavour.expr.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GenericWildcard extends GenericType {
    private List<GenericType> lowerBound;
    private List<GenericType> upperBound;

    private GenericWildcard(List<GenericType> lowerBound, List<GenericType> upperBound) {
        this.lowerBound = Collections.unmodifiableList(new ArrayList<>(lowerBound));
        this.upperBound = Collections.unmodifiableList(new ArrayList<>(upperBound));
    }

    public static GenericWildcard lowerBounded(List<GenericType> lowerBound) {
        return new GenericWildcard(lowerBound, Collections.emptyList());
    }

    public static GenericWildcard upperBounded(List<GenericType> upperBound) {
        return new GenericWildcard(upperBound, Collections.emptyList());
    }

    public static GenericWildcard unbounded() {
        return new GenericWildcard(Collections.emptyList(), Collections.emptyList());
    }

    public List<GenericType> getLowerBound() {
        return lowerBound;
    }

    public List<GenericType> getUpperBound() {
        return upperBound;
    }

    @Override
    public GenericType substitute(Substitutions substitutions) {
        return super.substitute(substitutions);
    }

    @Override
    GenericType substitute(Substitutions substitutions, Set<TypeVar> visited) {
        return new GenericWildcard(
                lowerBound.stream().map(bound -> bound.substitute(substitutions, visited))
                        .collect(Collectors.toList()),
                upperBound.stream().map(bound -> bound.substitute(substitutions, visited))
                        .collect(Collectors.toList()));
    }

    @Override
    public GenericType erasure() {
        if (lowerBound.size() == 1) {
            return lowerBound.get(0).erasure();
        } else {
            return new GenericClass("java.lang.Object");
        }
    }
}
