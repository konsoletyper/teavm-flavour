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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class GenericTypeNavigator {
    private ClassDescriberRepository classRepository;

    public GenericTypeNavigator(ClassDescriberRepository classRepository) {
        this.classRepository = classRepository;
    }

    public GenericClass getParent(GenericClass cls) {
        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return null;
        }
        GenericClass superType = describer.getSupertype();
        if (superType == null) {
            return null;
        }

        ClassDescriber parentDescriber = classRepository.describe(superType.getName());
        if (parentDescriber == null) {
            return null;
        }

        TypeVar[] typeVars = parentDescriber.getTypeVariables();
        List<GenericType> typeValues = cls.getArguments();
        if (typeVars.length != typeValues.size()) {
            return null;
        }
        Map<TypeVar, GenericType> substitutions = new HashMap<>();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutions.put(typeVars[i], typeValues.get(i));
        }

        return superType.substitute(substitutions);
    }
}
