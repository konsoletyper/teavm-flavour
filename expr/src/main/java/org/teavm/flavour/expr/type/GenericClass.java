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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class GenericClass extends GenericType {
    private String name;
    private List<? extends TypeArgument> arguments;

    public GenericClass(String name) {
        this.name = name;
        this.arguments = Collections.emptyList();
    }

    public GenericClass(String name, TypeArgument... arguments) {
        this(name, Arrays.asList(arguments));
    }

    public GenericClass(String name, TypeVar... arguments) {
        this(name, Arrays.stream(arguments)
                .map(typeVar -> TypeArgument.invariant(new GenericReference(typeVar)))
                .collect(Collectors.toList()));
    }

    public GenericClass(String name, List<? extends TypeArgument> arguments) {
        if (name == null || arguments == null) {
            throw new IllegalArgumentException();
        }
        for (TypeArgument argument : arguments) {
            if (argument == null) {
                throw new IllegalArgumentException();
            }
        }
        this.name = name;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public String getName() {
        return name;
    }

    public List<? extends TypeArgument> getArguments() {
        return arguments;
    }

    @Override
    public GenericClass substitute(Substitutions substitutions) {
        return substitute(substitutions, new HashSet<>());
    }

    @Override
    GenericClass substitute(Substitutions substitutions, Set<TypeVar> visited) {
        List<TypeArgument> argumentSubstitutions = new ArrayList<>();
        boolean changed = false;
        for (TypeArgument arg : arguments) {
            TypeArgument argSubst = arg.mapBound(bound -> bound.substitute(substitutions, visited));
            argumentSubstitutions.add(argSubst);
            changed |= arg != argSubst;
        }
        return changed ? new GenericClass(name, argumentSubstitutions) : this;
    }

    @Override
    public GenericClass substituteArgs(Function<TypeVar, TypeArgument> substitutions) {
        return substituteArgs(substitutions, new HashSet<>());
    }

    @Override
    GenericClass substituteArgs(Function<TypeVar, TypeArgument> substitutions, Set<TypeVar> visited) {
        List<TypeArgument> argumentSubstitutions = new ArrayList<>();
        boolean changed = false;
        for (TypeArgument arg : arguments) {
            if (arg.getVariance() == Variance.INVARIANT && arg.getBound() instanceof GenericReference) {

                TypeArgument argSubst = substitutions.apply(
                        ((GenericReference) arg.getBound()).getVar());
                if (argSubst != null) {
                    changed |= true;
                    arg = argSubst;
                }
            }
            argumentSubstitutions.add(arg);
        }
        return changed ? new GenericClass(name, argumentSubstitutions) : this;
    }

    @Override
    public int hashCode() {
        int hash = 31 * name.hashCode() + 13;
        for (TypeArgument arg : arguments) {
            hash = 31 * hash + arg.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GenericClass)) {
            return false;
        }
        GenericClass other = (GenericClass) obj;
        if (!name.equals(other.name)) {
            return false;
        }
        if (arguments.size() != other.arguments.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (!arguments.get(i).equals(other.arguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public GenericType erasure() {
        return new GenericClass(name);
    }
}
