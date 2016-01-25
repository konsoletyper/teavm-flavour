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
        if (!equalConstraintImpl(a, b)) {
            return false;
        }
        return recheck();
    }

    private boolean recheck() {
        while (inferenceVars.values().stream().anyMatch(v -> v.find().updated)) {
            for (InferenceVar var : inferenceVars.values()) {
                var.find().updated = false;
            }
            Set<InferenceVar> processed = new HashSet<>();
            for (InferenceVar var : inferenceVars.values()) {
                var = var.find();
                if (processed.add(var) && !recheckVar(var)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean recheckVar(InferenceVar var) {
        if (var.instantiation != null) {
            for (GenericType bound : var.upperBounds) {
                if (!subtypeConstraintImpl(bound, var.instantiation)) {
                    return false;
                }
            }
            for (GenericType bound : var.lowerBounds) {
                if (!subtypeConstraintImpl(var.instantiation, bound)) {
                    return false;
                }
            }
        }
        for (GenericType upperBound : var.upperBounds) {
            for (GenericType lowerBound : var.upperBounds) {
                if (!subtypeConstraintImpl(lowerBound, upperBound)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean equalConstraintImpl(GenericType a, GenericType b) {
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
                if (!equalConstraintImpl(s.getArguments().get(i), t.getArguments().get(i))) {
                    return false;
                }
            }
            return true;
        } else if (a instanceof GenericArray && b instanceof GenericArray) {
            GenericArray s = (GenericArray) a;
            GenericArray t = (GenericArray) b;
            if (s.getElementType() instanceof GenericType && t.getElementType() instanceof GenericType) {
                return equalConstraintImpl((GenericType) s.getElementType(), (GenericType) t.getElementType());
            } else {
                return s.getElementType().equals(t.getElementType());
            }
        }
        return false;
    }

    private boolean equalConstraintTwoVars(InferenceVar x, InferenceVar y) {
        InferenceVar common = x.union(y);
        InferenceVar remaining = common == x ? y : x;
        if (!equalConstraintImpl(common.instantiation, remaining.instantiation)) {
            return false;
        }

        GenericReference commonType = new GenericReference(common.anyTypeVar());

        for (GenericType bound : remaining.lowerBounds) {
            if (!subtypeConstraintImpl(bound, commonType)) {
                return false;
            }
        }
        for (GenericType bound : remaining.upperBounds) {
            if (!subtypeConstraintImpl(commonType, bound)) {
                return false;
            }
        }
        if (remaining.instantiation != null) {
            if (!equalConstraintImpl(commonType, remaining.instantiation)) {
                return false;
            }
        }
        return true;
    }

    private boolean equalConstraintVarType(InferenceVar x, GenericType t) {
        x = x.find();
        if (x.instantiation == null) {
            x.instantiation = t;
            x.updated = true;
            for (GenericType bound : x.upperBounds) {
                if (!subtypeConstraintImpl(x.instantiation, bound)) {
                    return false;
                }
            }
            for (GenericType bound : x.lowerBounds) {
                if (!subtypeConstraintImpl(bound, x.instantiation)) {
                    return false;
                }
            }
            return true;
        } else {
            return equalConstraintImpl(x.instantiation, t);
        }
    }

    private boolean subtypeConstraintImpl(GenericType a, GenericType b) {
        if (a instanceof GenericReference || b instanceof GenericReference) {
            if (a instanceof GenericReference) {
                InferenceVar x = var(((GenericReference) a).getVar());
                addUpperBound(x, b);
            }
            if (b instanceof GenericReference) {
                InferenceVar x = var(((GenericReference) b).getVar());
                addLowerBound(x, a);
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
            equalConstraintImpl(pattern.getArguments().get(i), t.getArguments().get(i));
        }
        return true;
    }

    private boolean addUpperBound(InferenceVar x, GenericType t) {
        return true;
    }

    private boolean addLowerBound(InferenceVar x, GenericType t) {
        x = x.find();
        if (x.instantiation != null) {
            if (!equalConstraintImpl(x.instantiation, t)) {
                return false;
            }
        }
        if (t instanceof GenericClass) {
            GenericClass cls = (GenericClass) t;
            List<GenericType> newLowerBounds = new ArrayList<>();
            Set<String> newErasure = new HashSet<>();
            Map<String, GenericType> erasureMap = new HashMap<>();
            for (GenericType lowerBound : x.lowerBounds) {
                if (lowerBound instanceof GenericReference) {
                    newLowerBounds.add(lowerBound);
                } else if (lowerBound instanceof GenericClass) {
                    GenericClass lowerBoundCls = (GenericClass) lowerBound;
                    newErasure.addAll(typeNavigator.commonSupertypes(Collections.singleton(cls.getName()),
                            Collections.singleton(lowerBoundCls.getName())));
                    erasureMap.put(cls.getName(), lowerBoundCls);
                }
            }
            if (newErasure.size() > 1) {
                newErasure.remove("java.lang.Object");
            }
        }
        return false;
    }

    private InferenceVar var(TypeVar typeVar) {
        return inferenceVars.computeIfAbsent(typeVar, InferenceVar::new).find();
    }

    class InferenceVar {
        InferenceVar parent;
        int rank;
        Set<TypeVar> variables = new HashSet<>();
        Set<GenericType> upperBounds = new HashSet<>();
        Set<GenericType> lowerBounds = new HashSet<>();
        GenericType instantiation;
        boolean updated;

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
            updated |= other.updated;
        }
    }
}
