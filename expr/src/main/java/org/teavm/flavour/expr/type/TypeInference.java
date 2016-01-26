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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 *
 * @author Alexey Andreev
 */
public class TypeInference {
    private GenericTypeNavigator typeNavigator;
    private Map<TypeVar, InferenceVar> inferenceVars = new WeakHashMap<>();

    public TypeInference(GenericTypeNavigator typeNavigator) {
        this.typeNavigator = typeNavigator;
    }

    public boolean equalConstraint(GenericType a, GenericType b) {
        if (a instanceof GenericReference && b instanceof GenericReference) {
            InferenceVar x = var(((GenericReference) a).getVar());
            InferenceVar y = var(((GenericReference) b).getVar());
            return equalConstraintTwoVars(x, y);
        } else if (a instanceof GenericReference) {
            InferenceVar x = var(((GenericReference) a).getVar());
            return equalConstraintVarType(x, b);
        } else if (b instanceof GenericReference) {
            InferenceVar x = var(((GenericReference) b).getVar());
            return equalConstraintVarType(x, a);
        } else if (a instanceof GenericClass && b instanceof GenericClass) {
            GenericClass s = (GenericClass) a;
            GenericClass t = (GenericClass) b;
            if (!s.getName().equals(t.getName()) || s.getArguments().size() != t.getArguments().size()) {
                return false;
            }
            for (int i = 0; i < s.getArguments().size(); ++i) {
                if (!equalConstraint(s.getArguments().get(i), t.getArguments().get(i))) {
                    return false;
                }
            }
            return true;
        } else if (a instanceof GenericArray && b instanceof GenericArray) {
            GenericArray s = (GenericArray) a;
            GenericArray t = (GenericArray) b;
            if (s.getElementType() instanceof GenericType && t.getElementType() instanceof GenericType) {
                return equalConstraint((GenericType) s.getElementType(), (GenericType) t.getElementType());
            } else {
                return s.getElementType().equals(t.getElementType());
            }
        }
        return false;
    }

    private boolean equalConstraintTwoVars(InferenceVar x, InferenceVar y) {
        InferenceVar common = x.union(y);
        InferenceVar remaining = common == x ? y : x;
        if (common.boundType == null) {
            common.boundType = remaining.boundType;
            return true;
        } else if (remaining.boundType == null) {
            return true;
        } else if (common.boundType == BoundType.EXACT && remaining.boundType == BoundType.EXACT) {
            return equalConstraint(common.bounds.iterator().next(), remaining.bounds.iterator().next());
        } else {
            return false;
        }
    }

    private boolean equalConstraintVarType(InferenceVar x, GenericType t) {
        x = x.find();
        if (x.boundType == null) {
            x.boundType = BoundType.EXACT;
            x.bounds.add(t);
            return true;
        } else if (x.boundType == BoundType.EXACT) {
            return equalConstraint(x.bounds.iterator().next(), t);
        } else if (x.boundType == BoundType.LOWER) {
            if (x.complexBound) {
                return false;
            }
            x.boundType = BoundType.EXACT;
            GenericType oldBound = x.bounds.iterator().next();
            x.bounds.clear();
            x.bounds.add(t);
            return subtypeConstraint(oldBound, t);
        } else {
            return false;
        }
    }

    public boolean subtypeConstraint(GenericType a, GenericType b) {
        if (a instanceof GenericReference || b instanceof GenericReference) {
            if (a instanceof GenericReference) {
                InferenceVar x = var(((GenericReference) a).getVar());
                if (!addUpperBound(x, b)) {
                    return false;
                }
            }
            if (b instanceof GenericReference) {
                InferenceVar x = var(((GenericReference) b).getVar());
                if (!addLowerBound(x, a)) {
                    return false;
                }
            }
            return true;
        } else if (a instanceof GenericClass && b instanceof GenericClass) {
            return subtypeConstraintClasses((GenericClass) a, (GenericClass) b);
        } else if (a instanceof GenericArray) {
            if (b instanceof GenericArray) {
                return true;
            } else if (b instanceof GenericClass && ((GenericClass) b).getName().equals("java.lang.Object")) {
                return true;
            }
        }
        return false;
    }

    private boolean subtypeConstraintClasses(GenericClass s, GenericClass t) {
        List<GenericClass> path = typeNavigator.sublassPath(s, t.getName());
        if (path == null) {
            return false;
        }
        GenericClass pattern = path.get(path.size() - 1);
        for (int i = 0; i < pattern.getArguments().size(); ++i) {
            equalConstraint(pattern.getArguments().get(i), t.getArguments().get(i));
        }
        return true;
    }

    private boolean addUpperBound(InferenceVar x, GenericType t) {
        return true;
    }

    private boolean addLowerBound(InferenceVar x, GenericType t) {
        x = x.find();
        if (x.visited) {
            x.recursive = true;
            return true;
        }
        x.visited = true;
        try {
            if (x.boundType == null) {
                x.boundType = BoundType.LOWER;
                if (t instanceof GenericClass) {
                    GenericClass cls = (GenericClass) t;
                    GenericType[] args = new GenericType[cls.getArguments().size()];
                    for (int i = 0; i < args.length; ++i) {
                        args[i] = new GenericReference(new TypeVar());
                    }
                    GenericClass bound = new GenericClass(cls.getName(), args);
                    for (int i = 0; i < args.length; ++i) {
                        if (!subtypeConstraint(cls.getArguments().get(i), args[i])) {
                            return false;
                        }
                    }
                    x.bounds.add(bound);
                } else {
                    x.bounds.add(t);
                }
                return true;
            } else if (x.boundType == BoundType.LOWER) {
                return extendLowerBound(x, t);
            } else if (x.boundType == BoundType.EXACT) {
                return subtypeConstraint(t, x.bounds.iterator().next());
            }
            return false;
        } finally {
            x.visited = false;
        }
    }

    private boolean extendLowerBound(InferenceVar x, GenericType t) {
        if (!(t instanceof GenericClass)) {
            x.bounds.add(t);
            return true;
        }
        GenericClass cls = (GenericClass) t;
        List<GenericType> newLowerBounds = new ArrayList<>();
        List<GenericClass> newClasses = new ArrayList<>();
        Set<String> newErasure = new HashSet<>();
        Map<String, GenericClass> erasureMap = new HashMap<>();
        for (GenericType lowerBound : x.bounds) {
            if (lowerBound instanceof GenericReference) {
                newLowerBounds.add(lowerBound);
            } else if (lowerBound instanceof GenericClass) {
                GenericClass lowerBoundCls = (GenericClass) lowerBound;
                newErasure.addAll(typeNavigator.commonSupertypes(Collections.singleton(cls.getName()),
                        Collections.singleton(lowerBoundCls.getName())));
                erasureMap.put(lowerBoundCls.getName(), lowerBoundCls);
            }
        }
        if (newErasure.size() > 1) {
            newErasure.remove("java.lang.Object");
        }
        if (newErasure.size() != 1 || erasureMap.size() != 1
                || !erasureMap.keySet().containsAll(newErasure)) {
            x.complexBound = true;
        }

        for (String erasure : newErasure) {
            GenericClass existing = erasureMap.get(erasure);
            if (existing == null) {
                existing = typeNavigator.getGenericClass(erasure);
                newClasses.add(existing);
                erasureMap.put(erasure, existing);
            }
            newLowerBounds.add(existing);
        }

        x.bounds.clear();
        x.bounds.addAll(newLowerBounds);

        for (String erasure : newErasure) {
            GenericClass existing = erasureMap.get(erasure);
            List<GenericClass> path = typeNavigator.sublassPath(cls, erasure);
            GenericClass newType = path.get(path.size() - 1);
            for (int i = 0; i < existing.getArguments().size(); ++i) {
                if (!subtypeConstraint(newType.getArguments().get(i), existing.getArguments().get(i))) {
                    return false;
                }
            }
        }

        for (GenericClass existing : erasureMap.values()) {
            if (newErasure.contains(existing)) {
                continue;
            }
            for (GenericClass newClass : newClasses) {
                List<GenericClass> path = typeNavigator.sublassPath(existing, newClass.getName());
                if (path == null) {
                    continue;
                }
                GenericClass oldClass = path.get(path.size() - 1);
                for (int i = 0; i < oldClass.getArguments().size(); ++i) {
                    if (!subtypeConstraint(oldClass.getArguments().get(i), newClass.getArguments().get(i))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private InferenceVar var(TypeVar typeVar) {
        return inferenceVars.computeIfAbsent(typeVar, InferenceVar::new).find();
    }

    public Substitutions getSubstitutions() {
        return substitutions;
    }

    private Substitutions substitutions = new Substitutions() {
        @Override
        public GenericType get(TypeVar var) {
            InferenceVar inferenceVar = inferenceVars.get(var);
            if (inferenceVar == null) {
                return null;
            }
            if (inferenceVar.boundType == null) {
                return null;
            }
            switch (inferenceVar.boundType) {
                case EXACT:
                    return inferenceVar.filteredBounds().iterator().next();
                case UPPER: {
                    TypeVar v = new TypeVar();
                    List<GenericType> bounds = inferenceVar.filteredBounds();
                    v.withUpperBound(bounds.toArray(new GenericType[0]));
                    return new GenericReference(v);
                }
                case LOWER: {
                    List<GenericType> bounds = inferenceVar.filteredBounds();
                    if (!inferenceVar.complexBound) {
                        return bounds.iterator().next();
                    }
                    TypeVar v = new TypeVar();
                    v.withLowerBound(bounds.toArray(new GenericType[0]));
                    return new GenericReference(v);
                }
            }
            return null;
        }
    };

    class InferenceVar {
        InferenceVar parent;
        int rank;
        Set<TypeVar> variables = new HashSet<>();
        Set<GenericType> bounds = new HashSet<>();
        BoundType boundType;
        boolean complexBound;
        boolean visited;
        boolean recursive;

        InferenceVar(TypeVar var) {
            variables.add(var);
        }

        public TypeVar anyTypeVar() {
            return variables.iterator().next();
        }

        public InferenceVar find() {
            if (parent == null) {
                return this;
            }
            if (parent.parent == null) {
                return parent;
            }
            List<InferenceVar> path = new ArrayList<>();
            InferenceVar v = this;
            while (v.parent != null) {
                path.add(v);
                v = v.parent;
            }
            for (InferenceVar u : path) {
                u.parent = v;
            }
            return v;
        }

        public InferenceVar union(InferenceVar other) {
            return this.find().unionImpl(other.find());
        }

        private InferenceVar unionImpl(InferenceVar other) {
            if (rank > other.rank) {
                other.parent = this;
                unionData(other);
                return this;
            } else if (rank < other.rank) {
                parent = other;
                other.unionData(this);
                return other;
            } else {
                other.parent = this;
                ++rank;
                unionData(other);
                return this;
            }
        }

        private void unionData(InferenceVar other) {
            variables.addAll(other.variables);
            recursive |= other.recursive;
        }

        public List<GenericType> filteredBounds() {
            return bounds.stream().filter(bound -> {
                if (!(bound instanceof GenericReference)) {
                    return true;
                }
                InferenceVar var = var(((GenericReference) bound).getVar());
                return var != this;
            }).collect(Collectors.toList());
        }
    }

    enum BoundType {
        UPPER,
        LOWER,
        EXACT
    }
}
