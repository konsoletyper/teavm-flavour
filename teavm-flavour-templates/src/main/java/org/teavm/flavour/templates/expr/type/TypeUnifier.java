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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class TypeUnifier {
    private ClassDescriberRepository classRepository;
    private Map<TypeVar, GenericType> substitutions = new HashMap<>();
    private Map<TypeVar, GenericType> safeSubstitutions = Collections.unmodifiableMap(substitutions);

    public TypeUnifier(ClassDescriberRepository classRepository) {
        this.classRepository = classRepository;
    }

    public ClassDescriberRepository getClassRepository() {
        return classRepository;
    }

    public Map<TypeVar, GenericType> getSubstitutions() {
        return safeSubstitutions;
    }

    public boolean unify(GenericType pattern, GenericType special) {
        substitutions.clear();
        return unifyImpl(pattern, special) != null;
    }

    private GenericType unifyImpl(GenericType pattern, GenericType special) {
        if (pattern instanceof GenericReference) {
            return substituteVariable((GenericReference)pattern, special);
        } else if (pattern instanceof Primitive && special instanceof Primitive) {
            return ((Primitive)pattern).getKind() == ((Primitive)special).getKind() ?
                    pattern : null;
        } else if (pattern instanceof GenericArray && special instanceof GenericArray) {
            GenericType unifiedElement = unifyImpl(((GenericArray)pattern).getElementType(),
                    ((GenericArray)special).getElementType());
            return unifiedElement != null ? new GenericArray(unifiedElement) : null;
        } else if (pattern instanceof GenericClass && special instanceof GenericClass) {
            return null;
        }
        return null;
    }

    private GenericType substituteVariable(GenericReference ref, GenericType special) {
        return null;
    }
}
