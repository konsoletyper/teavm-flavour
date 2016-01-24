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

    private GenericType unifyImpl(GenericType pattern, GenericType special, boolean covariant) {
        if (pattern.equals(special)) {
            return special;
        }
        if (pattern instanceof GenericReference) {
            return substituteVariable((GenericReference) pattern, special, covariant);
        } else if (special instanceof GenericReference) {
            return substituteVariable((GenericReference) special, pattern, covariant);
        } else if (pattern instanceof GenericReference && special instanceof GenericReference) {
            return joinVariables(((GenericReference) pattern).getVar(), ((GenericReference) special).getVar(),
                    covariant);
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

    private GenericType substituteVariable(GenericReference ref, GenericType special, boolean covariant) {
        SubstitutionInfo substitution = substitution(ref.getVar());
        if (!covariant) {
            if (substitution.value == null) {
                substitution.value = special;
                return substitution.value;
            } else {
                if (substitution.value.equals(special)) {
                    return substitution.value;
                } else {
                    GenericType common = unifyImpl(substitution.value, special, false);
                    if (common == null) {
                        return null;
                    }
                    substitution.value = common;
                    if (!recheckSubstitution(substitution)) {
                        return null;
                    }
                    return common;
                }
            }
        } else {
            if (substitution.upperBound == null) {
                substitution.upperBound = special;
            } else {
                GenericType common = unifyImpl(substitution.upperBound, special, true);
                if (common == null) {
                    return null;
                }
                substitution.upperBound = common;
                substitution.strict = false;
            }
            if (!recheckSubstitution(substitution)) {
                return null;
            }
            return substitution.value != null ? substitution.value : ref;
        }
    }

    private boolean recheckSubstitution(SubstitutionInfo info) {
        if (info.upperBound != null && info.value != null) {
            return checkSubtype(info.upperBound, info.value);
        }
        return true;
    }

    private boolean checkSubtype(GenericType subtype, GenericType supertype) {
        subtype = subtype.substitute(safeSubstitutions);
        supertype = supertype.substitute(safeSubstitutions);
        if (subtype instanceof GenericReference || supertype instanceof GenericReference) {
            return true;
        }

        if (subtype instanceof GenericClass && supertype instanceof GenericClass) {
            GenericClass subclass = (GenericClass) subtype;
            GenericClass superclass = (GenericClass) supertype;
            return isSuperclass(superclass.getName(), subclass.getName());
        } else {
            return false;
        }
    }

    private boolean isSuperclass(String superclassName, String subclassName) {
        if (subclassName.equals(superclassName)) {
            return true;
        }
        ClassDescriber subclass = classRepository.describe(subclassName);
        if (subclass == null || subclass == null) {
            return false;
        }

        if (subclass.getSupertype() != null && isSuperclass(superclassName, subclass.getSupertype().getName())) {
            return true;
        }
        for (GenericClass iface : subclass.getInterfaces()) {
            if (isSuperclass(superclassName, iface.getName())) {
                return true;
            }
        }
        return false;
    }

    private GenericType joinVariables(TypeVar s, TypeVar t, boolean covariant) {
        SubstitutionInfo common;
        if (!covariant) {
            SubstitutionInfo u = substitution(s);
            SubstitutionInfo v = substitution(t);
            if (u == v) {
                return u.value != null ? u.value : new GenericReference(s);
            }
            common = u.union(v);
            SubstitutionInfo other = u == common ? v : u;
            GenericType result;
            if (common.value == null) {
                result = other.value;
            } else if (other.value == null) {
                result = common.value;
            } else {
                result = unifyImpl(common.value, other.value, false);
            }
            if (result != null) {
                common.value = result;
            }
        } else {
            SubstitutionInfo u = substitution(s);
            SubstitutionInfo v = substitution(t);
            if (u != v) {
                common = u.union(v);
                SubstitutionInfo other = u == common ? v : u;
                GenericType result = unifyImpl(common.upperBound, other.upperBound, true);
                if (result != null) {
                    common.value = result;
                }
            } else {
                common = u;
            }
        }
        return common.value != null ? common.value : new GenericReference(s);
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
            GenericType u = s.getArguments().get(i);
            GenericType v = t.getArguments().get(i);
            if (u instanceof GenericReference && ((GenericReference) u).getVar().getName() != null
                    || v instanceof GenericReference && ((GenericReference) v).getVar().getName() != null) {
                args[i] = unifyImpl(u, v, false);
            } else {
                args[i] = unifyImpl(u, v, true);
            }
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
        Set<String> superclasses = typeNavigator.commonSupertypes(Collections.singleton(s.getName()),
                Collections.singleton(t.getName()));
        if (superclasses.isEmpty()) {
            return "java.lang.Object";
        }
        Optional<String> common = superclasses.stream().filter(cls -> {
            ClassDescriber desc = typeNavigator.getClassRepository().describe(cls);
            return !desc.isInterface();
        }).findAny();
        if (!common.isPresent()) {
            common = superclasses.stream()
                    .filter(cls -> cls.equals(s.getName()) || cls.equals(t.getName()))
                    .findFirst();
        }
        return common.orElse(null);
    }

    private SubstitutionInfo substitution(TypeVar var) {
        return substitutions.computeIfAbsent(var, SubstitutionInfo::new).find();
    }

    private Substitutions safeSubstitutions = new Substitutions() {
        @Override public GenericType get(TypeVar var) {
            SubstitutionInfo info = substitutions.get(var);
            if (info == null) {
                return null;
            }
            info = info.find();
            if (info.value != null) {
                return info.value.substitute(this);
            }
            if (info.upperBound != null) {
                return info.upperBound.substitute(this);
            }
            TypeVar wildcardVar = new TypeVar();
            return new GenericReference(wildcardVar);
        }
    };

    static class SubstitutionInfo {
        SubstitutionInfo parent;
        int rank;
        GenericType value;
        GenericType upperBound;
        boolean strict = true;
        Set<TypeVar> variables = new HashSet<>();

        SubstitutionInfo(TypeVar var) {
            variables.add(var);
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
                return a;
            } else if (a.rank < b.rank) {
                a.parent = b;
                b.variables.addAll(a.variables);
                return b;
            } else {
                b.parent = a;
                a.rank++;
                a.variables.addAll(b.variables);
                return a;
            }
        }

        public boolean isNamed() {
            return variables.stream().allMatch(var -> var.getName() != null);
        }
    }
}
