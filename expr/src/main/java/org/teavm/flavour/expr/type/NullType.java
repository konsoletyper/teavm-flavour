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

import java.util.Set;
import java.util.function.Function;

public class NullType extends GenericType {
    public static final NullType INSTANCE = new NullType();

    private NullType() {
    }

    @Override
    public NullType substitute(Substitutions substitutions) {
        return this;
    }

    @Override
    NullType substitute(Substitutions substitutions, Set<TypeVar> visited) {
        return this;
    }

    @Override
    public NullType substituteArgs(Function<TypeVar, TypeArgument> substitutions) {
        return this;
    }

    @Override
    NullType substituteArgs(Function<TypeVar, TypeArgument> substitutions, Set<TypeVar> visited) {
        return this;
    }

    @Override
    public GenericType erasure() {
        return this;
    }
}
