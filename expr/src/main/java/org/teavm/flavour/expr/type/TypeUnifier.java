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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeUnifier {
    private ClassDescriberRepository classRepository;
    private Map<TypeVar, GenericType> substitutions = new HashMap<>();
    private Map<TypeVar, GenericType> safeSubstitutions = Collections.unmodifiableMap(substitutions);
    private GenericTypeNavigator typeNavigator;

    public TypeUnifier(ClassDescriberRepository classRepository) {
        this.classRepository = classRepository;
        typeNavigator = new GenericTypeNavigator(classRepository);
    }

    public ClassDescriberRepository getClassRepository() {
        return classRepository;
    }

    public Map<TypeVar, GenericType> getSubstitutions() {
        return safeSubstitutions;
    }

    public boolean unify(GenericType pattern, GenericType special, boolean covariant) {
        return unifyImpl(pattern, special, covariant) != null;
    }

    public GenericType unifyAndGet(GenericType pattern, GenericType special, boolean covariant) {
        return unifyImpl(pattern, special, covariant);
    }

    private GenericType unifyImpl(GenericType pattern, GenericType special, boolean covariant) {
        if (pattern.equals(special)) {
            return special;
        }
        if (pattern instanceof GenericReference) {
            pattern = pattern.substitute(substitutions);
        }
        if (special instanceof GenericReference) {
            special = special.substitute(substitutions);
        }
        if (pattern instanceof GenericReference) {
            return substituteVariable((GenericReference)pattern, special);
        } else if (special instanceof GenericReference) {
            return substituteVariable((GenericReference)special, pattern);
        } else if (pattern instanceof GenericArray && special instanceof GenericArray) {
            return unifyArrays((GenericArray)pattern, (GenericArray)special);
        } else if (pattern instanceof GenericClass) {
            GenericClass patternClass = (GenericClass)pattern;
            if (patternClass.getName().equals("java.lang.Object") && covariant) {
                return special;
            } else if (special instanceof GenericClass) {
                GenericClass specialClass = (GenericClass)special;
                return unifyClasses(patternClass, specialClass, covariant);
            } else {
                return null;
            }
        }
        return null;
    }

    private GenericType unifyArrays(GenericArray pattern, GenericArray special) {
        if (pattern.getElementType() instanceof GenericType && special.getElementType() instanceof GenericType) {
            GenericType patternElem = (GenericType)pattern.getElementType();
            GenericType specialElem = (GenericType)special.getElementType();
            GenericType resultElem = unifyImpl(patternElem, specialElem, true);
            return resultElem != null ? new GenericArray(resultElem) : null;
        } else {
            return pattern.getElementType().equals(special.getElementType()) ? special : null;
        }
    }

    private GenericType unifyClasses(GenericClass pattern, GenericClass special, boolean covariant) {
        if (pattern.getArguments().size() != special.getArguments().size()) {
            return null;
        }
        GenericClass matchType;
        if (!covariant) {
            if (!pattern.getName().equals(special.getName())) {
                return null;
            }
            matchType = special;
        } else {
            List<GenericClass> path = typeNavigator.sublassPath(special, pattern.getName());
            if (path == null) {
                return null;
            }
            matchType = path.get(path.size() - 1);
        }
        if (pattern.getArguments().size() != matchType.getArguments().size()) {
            return null;
        }
        for (int i = 0; i < pattern.getArguments().size(); ++i) {
            if (unifyImpl(pattern.getArguments().get(i), matchType.getArguments().get(i), false) == null) {
                return null;
            }
        }
        return matchType;
    }

    private GenericType substituteVariable(GenericReference ref, GenericType special) {
        GenericType knownSubstitution = substitutions.get(ref.getVar());
        if (knownSubstitution == null) {
            substitutions.put(ref.getVar(), special);
            if (ref.getVar().getUpperBound() != null) {
                if (unifyImpl(ref.getVar().getUpperBound(), special, true) == null) {
                    return null;
                }
            }
        } else {
            if (!knownSubstitution.equals(special)) {
                return null;
            }
        }
        return special;
    }
}
