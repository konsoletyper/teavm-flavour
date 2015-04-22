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
package org.teavm.flavour.templates.expr.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class GenericClass extends GenericType {
    String name;
    List<GenericType> arguments = new ArrayList<>();
    private List<GenericType> safeArguments = Collections.unmodifiableList(arguments);

    public GenericClass(String name, List<GenericType> arguments) {
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
}
