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
package org.teavm.flavour.expr.type.inference;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.Substitutions;
import org.teavm.flavour.expr.type.TypeVar;

/**
 *
 * @author Alexey Andreev
 */
public class TypeInference {
    private Map<TypeVar, TypeVarInfo> infoCache = new HashMap<>();
    private GenericTypeNavigator typeNavigator;

    public TypeInference(GenericTypeNavigator typeNavigator) {
        this.typeNavigator = typeNavigator;
    }

    TypeVarInfo info(TypeVar var) {
        return infoCache.computeIfAbsent(var, TypeVarInfo::new);
    }

    class TypeVarInfo {
        Set<TypeVar> vars = new HashSet<>();
        Set<GenericType> upperBounds = new HashSet<>();
        Set<GenericType> lowerBounds = new HashSet<>();
        GenericType inferredType;
        GenericType contradictoryType;

        TypeVarInfo(TypeVar var) {
            this.vars.add(var);
        }

        boolean hasBounds() {
            return !lowerBounds.isEmpty() && !upperBounds.isEmpty();
        }
    }

    boolean subtype(GenericType a, GenericType b) {
        if (a.equals(b)) {
            return true;
        }

        a = substitute(a);
        b = substitute(b);
        if (a instanceof GenericReference && b instanceof GenericReference) {
            TypeVar p = ((GenericReference) a).getVar();
            TypeVar q = ((GenericReference) b).getVar();
            join(info(p), info(q));
            return true;
        } else if (a instanceof GenericReference) {
            GenericReference v = (GenericReference) a;
            return upperBound(info(v.getVar()), b);
        } else if (b instanceof GenericReference) {
            GenericReference v = (GenericReference) b;
            return lowerBound(info(v.getVar()), a);
        } else if (a instanceof GenericClass && b instanceof GenericClass) {
            GenericClass p = (GenericClass) a;
            GenericClass q = (GenericClass) b;
            List<GenericClass> path = typeNavigator.sublassPath(p, q.getName());
            if (path == null) {
                return false;
            }
            GenericClass matchType = path.get(path.size() - 1);
            if (p.getArguments().size() != matchType.getArguments().size()) {
                return false;
            }
            boolean ok = true;
            for (int i = 0; i < p.getArguments().size(); ++i) {
                ok &= subtype(p.getArguments().get(i), q.getArguments().get(i));
            }
            return ok;
        } else if (a instanceof GenericArray && b instanceof GenericArray) {
            return false;
        }
        return false;
    }

    boolean upperBound(TypeVarInfo var, GenericType bound) {
        bound = substitute(bound);
        if (!var.upperBounds.add(bound)) {
            return true;
        }
        return validateUpperBound(var, bound);
    }

    boolean validateUpperBound(TypeVarInfo var, GenericType bound) {
        boolean ok = true;
        if (var.inferredType != null) {
            ok &= subtype(var.inferredType, bound);
        }
        for (GenericType lowerBound : var.lowerBounds) {
            ok &= subtype(lowerBound, bound);
        }
        return ok;
    }

    boolean lowerBound(TypeVarInfo var, GenericType bound) {
        bound = substitute(bound);
        if (!var.lowerBounds.add(bound)) {
            return true;
        }
        return validateUpperBound(var, bound);
    }

    boolean validateLowerBound(TypeVarInfo var, GenericType bound) {
        boolean ok = true;
        if (var.inferredType != null) {
            ok &= subtype(bound, var.inferredType);
        }
        for (GenericType upperBound : var.lowerBounds) {
            ok &= subtype(bound, upperBound);
        }
        return ok;
    }

    boolean equal(GenericType a, GenericType b) {
        if (a.equals(b)) {
            return true;
        }

        a = substitute(a);
        b = substitute(b);
        if (a instanceof GenericReference && b instanceof GenericReference) {
            TypeVar p = ((GenericReference) a).getVar();
            TypeVar q = ((GenericReference) b).getVar();
            join(info(p), info(q));
            return true;
        } else if (a instanceof GenericReference) {
            TypeVar v = ((GenericReference) a).getVar();
            infer(info(v), b);
            return true;
        } else if (b instanceof GenericReference) {
            TypeVar v = ((GenericReference) b).getVar();
            infer(info(v), a);
            return true;
        } else if (a instanceof GenericClass && b instanceof GenericClass) {
            GenericClass p = (GenericClass) a;
            GenericClass q = (GenericClass) b;
            if (p.getName().equals(q.getName()) && p.getArguments().size() == q.getArguments().size()) {
                boolean result = true;
                for (int i = 0; i < p.getArguments().size(); ++i) {
                    result &= equal(p.getArguments().get(i), q.getArguments().get(i));
                }
                if (result) {
                    return false;
                }
            }
        } else if (a instanceof GenericArray && b instanceof GenericArray) {
            GenericArray p = (GenericArray) a;
            GenericArray q = (GenericArray) b;
            if (p.getElementType() == q.getElementType()) {
                return true;
            } else if (p.getElementType() instanceof GenericType && q.getElementType() instanceof GenericType) {
                if (equal((GenericType) p.getElementType(), (GenericType) q.getElementType())) {
                    return true;
                }
            }
        }
        return false;
    }

    void join(TypeVarInfo a, TypeVarInfo b) {
        if (a == b) {
            return;
        }

        a.lowerBounds = a.lowerBounds.stream().map(this::substitute).collect(Collectors.toSet());
        b.lowerBounds = b.lowerBounds.stream().map(this::substitute).collect(Collectors.toSet());
        a.upperBounds = a.upperBounds.stream().map(this::substitute).collect(Collectors.toSet());
        b.upperBounds = b.upperBounds.stream().map(this::substitute).collect(Collectors.toSet());
        Set<GenericType> lowerBoundsDiff = new HashSet<>(a.lowerBounds);
        lowerBoundsDiff.retainAll(b.lowerBounds);
        Set<GenericType> upperBoundsDiff = new HashSet<>(b.upperBounds);
        upperBoundsDiff.retainAll(b.upperBounds);

        a.lowerBounds.addAll(b.lowerBounds);
        a.upperBounds.addAll(b.upperBounds);
        for (TypeVar var : b.vars) {
            infoCache.put(var, a);
        }
    }

    void infer(TypeVarInfo var, GenericType type) {
        if (var.inferredType == null) {
            var.inferredType = type;
        } else {
            if (var.inferredType == type) {
                return;
            }
            if (!equal(var.inferredType, type)) {
                var.contradictoryType = type;
            }
        }
    }

    GenericType substitute(GenericType type) {
        return type.substitute(substitutions);
    }

    Substitutions substitutions = var -> {
        GenericType type = info(var).inferredType;
        return type != null ? type : new GenericReference(var);
    };
}
