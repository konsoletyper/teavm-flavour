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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeUnifier {
    private ClassDescriberRepository classRepository;
    private Map<TypeVar, SubstitutionInfo> substitutions = new HashMap<>();
    private GenericTypeNavigator typeNavigator;

    public TypeUnifier(ClassDescriberRepository classRepository) {
        this.classRepository = classRepository;
        typeNavigator = new GenericTypeNavigator(classRepository);
    }

    public ClassDescriberRepository getClassRepository() {
        return classRepository;
    }

    public Substitutions getSubstitutions() {
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
            return substituteVariable((GenericReference) pattern, special);
        } else if (special instanceof GenericReference) {
            return substituteVariable((GenericReference) special, pattern);
        } else if (pattern instanceof GenericReference && special instanceof GenericReference) {
            return joinVariables(((GenericReference) pattern).getVar(), ((GenericReference) special).getVar());
        } else if (pattern instanceof GenericArray && special instanceof GenericArray) {
            return unifyArrays((GenericArray) pattern, (GenericArray) special);
        } else if (pattern instanceof GenericClass) {
            GenericClass patternClass = (GenericClass) pattern;
            if (patternClass.getName().equals("java.lang.Object") && covariant) {
                return special;
            } else if (special instanceof GenericClass) {
                GenericClass specialClass = (GenericClass) special;
                return covariant
                        ? unifyClassArgs(patternClass, specialClass)
                        : unifyClasses(patternClass, specialClass);
            } else {
                return null;
            }
        }
        return null;
    }

    private GenericType unifyArrays(GenericArray pattern, GenericArray special) {
        if (pattern.getElementType() instanceof GenericType && special.getElementType() instanceof GenericType) {
            GenericType patternElem = (GenericType) pattern.getElementType();
            GenericType specialElem = (GenericType) special.getElementType();
            GenericType resultElem = unifyImpl(patternElem, specialElem, true);
            return resultElem != null ? new GenericArray(resultElem) : null;
        } else {
            return pattern.getElementType().equals(special.getElementType()) ? special : null;
        }
    }

    private GenericType unifyClasses(GenericClass pattern, GenericClass special) {
        if (!pattern.getName().equals(special.getName())) {
            return null;
        }

        if (pattern.getArguments().size() != special.getArguments().size()) {
            return null;
        }

        GenericType[] args = new GenericType[pattern.getArguments().size()];
        for (int i = 0; i < pattern.getArguments().size(); ++i) {
            args[i] = unifyImpl(pattern.getArguments().get(i), special.getArguments().get(i), false);
            if (args[i] == null) {
                return null;
            }
        }

        return new GenericClass(pattern.getName(), args);
    }

    private GenericType substituteVariable(GenericReference ref, GenericType special) {
        SubstitutionInfo substitution = substitution(ref.getVar());
        if (substitution.value == null) {
            substitution.value = special;
            return substitution.value;
        } else {
            if (substitution.value.equals(special)) {
                return substitution.value;
            } else {
                GenericType common = unifyImpl(substitution.value, special, !substitution.named);
                if (common == null) {
                    return null;
                }
                substitution.value = common;
                substitution.strict = false;
                return common;
            }
        }
    }

    private GenericType joinVariables(TypeVar s, TypeVar t) {
        SubstitutionInfo u = substitution(s);
        SubstitutionInfo v = substitution(t);
        if (u == v) {
            return u.value != null ? u.value : new GenericReference(s);
        }
        SubstitutionInfo common = u.union(v);
        SubstitutionInfo other = u == common ? v : u;
        GenericType result = unifyImpl(common.value, other.value, !common.named);
        if (result != null) {
            common.value = result;
        }
        return common.value;
    }

    private GenericType unifyClassArgs(GenericClass s, GenericClass t) {
        String common = commonSuperclass(s, t);
        if (common == null) {
            return null;
        }

        List<GenericClass> path = typeNavigator.sublassPath(t, common);
        if (path == null) {
            return null;
        }
        t = path.get(path.size() - 1);
        path = typeNavigator.sublassPath(s, common);
        if (path == null) {
            return null;
        }
        s = path.get(path.size() - 1);

        if (t.getArguments().size() != s.getArguments().size()) {
            return null;
        }

        GenericType[] args = new GenericType[t.getArguments().size()];
        for (int i = 0; i < s.getArguments().size(); ++i) {
            args[i] = unifyImpl(s.getArguments().get(i), t.getArguments().get(i), true);
            if (args[i] == null) {
                return null;
            }
        }
        return new GenericClass(common, args);
    }

    private String commonSuperclass(GenericClass s, GenericClass t) {
        if (s.getName().equals(t.getName())) {
            return s.getName();
        }
        Set<GenericClass> superclasses = typeNavigator.commonSupertypes(Collections.singleton(s),
                Collections.singleton(t));
        if (superclasses.isEmpty()) {
            return "java.lang.Object";
        }
        Optional<GenericClass> concreteSuperclass = superclasses.stream().filter(cls -> {
            ClassDescriber desc = typeNavigator.getClassRepository().describe(cls.getName());
            return !desc.isInterface();
        }).findAny();
        if (!concreteSuperclass.isPresent()) {
            return "java.lang.Object";
        }
        return concreteSuperclass.get().getName();
    }

    private SubstitutionInfo substitution(TypeVar var) {
        return substitutions.computeIfAbsent(var, SubstitutionInfo::new).find();
    }

    private Substitutions safeSubstitutions = new Substitutions() {
        @Override public GenericType get(TypeVar var) {
            SubstitutionInfo info = substitutions.get(var);
            return info != null ? info.find().value : null;
        }
    };

    static class SubstitutionInfo {
        SubstitutionInfo parent;
        int rank;
        GenericType value;
        boolean strict = true;
        Set<TypeVar> variables = new HashSet<>();
        boolean named;

        SubstitutionInfo(TypeVar var) {
            variables.add(var);
            named = var.getName() != null;
        }

        public SubstitutionInfo find() {
            if (parent == null) {
                return this;
            }
            if (parent.parent == null) {
                return parent;
            }
            List<SubstitutionInfo> path = new ArrayList<>();
            SubstitutionInfo result = this;
            while (result.parent != null) {
                path.add(result);
                result = result.parent;
            }
            for (SubstitutionInfo elem : path) {
                elem.parent = result;
            }
            return result;
        }

        public SubstitutionInfo union(SubstitutionInfo other) {
            SubstitutionInfo a = find();
            SubstitutionInfo b = other.find();

            if (a.rank > b.rank) {
                b.parent = a;
                a.variables.addAll(b.variables);
                a.named |= b.named;
                return a;
            } else if (a.rank < b.rank) {
                a.parent = b;
                b.variables.addAll(a.variables);
                b.named |= a.named;
                return b;
            } else {
                b.parent = a;
                a.rank++;
                a.variables.addAll(b.variables);
                a.named |= b.named;
                return a;
            }
        }
    }
}
