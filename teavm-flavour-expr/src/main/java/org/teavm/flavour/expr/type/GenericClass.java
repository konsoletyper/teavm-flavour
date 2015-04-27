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

import java.util.*;

/**
 *
 * @author Alexey Andreev
 */
public final class GenericClass extends GenericType {
    private String name;
    private List<GenericType> arguments = new ArrayList<>();
    private List<GenericType> safeArguments = Collections.unmodifiableList(arguments);

    public GenericClass(String name) {
        this(name, Collections.<GenericType>emptyList());
    }

    public GenericClass(String name, List<GenericType> arguments) {
        if (name == null || arguments == null) {
            throw new IllegalArgumentException();
        }
        for (GenericType argument : arguments) {
            if (argument == null) {
                throw new IllegalArgumentException();
            }
        }
        this.name = name;
        this.arguments.addAll(arguments);
    }

    public String getName() {
        return name;
    }

    public List<GenericType> getArguments() {
        return safeArguments;
    }

    @Override
    public GenericClass substitute(Map<TypeVar, GenericType> substitutions) {
        List<GenericType> argumentSubstitutions = new ArrayList<>();
        boolean changed = false;
        for (GenericType arg : arguments) {
            GenericType argSubst = arg.substitute(substitutions);
            changed |= arg != argSubst;
            argumentSubstitutions.add(argSubst);
        }
        return changed ? new GenericClass(name, argumentSubstitutions) : this;
    }

    @Override
    public int hashCode() {
        int hash = 31 * name.hashCode() + 13;
        for (GenericType arg : arguments) {
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
        GenericClass other = (GenericClass)obj;
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
