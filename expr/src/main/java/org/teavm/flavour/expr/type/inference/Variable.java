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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * @author Alexey Andreev
 */
public class Variable {
    private TypeConstraints consumer;
    private List<Variable> upperDependencies = new ArrayList<>();
    private List<Variable> lowerDependencies = new ArrayList<>();
    private List<ReferenceType> upperBounds = new ArrayList<>();
    private List<ReferenceType> lowerBounds = new ArrayList<>();
    private ReferenceType implementation;
    int rank;
    private Variable parent;

    Variable(TypeConstraints consumer) {
        this.consumer = consumer;
    }

    private Variable union(Variable other) {
        if (rank < other.rank) {
            other.parent = this;
            return this;
        } else if (rank > other.rank) {
            parent = other;
            return other;
        } else {
            other.parent = this;
            ++rank;
            return this;
        }
    }

    public Variable find() {
        Variable var = this;
        if (var.parent != null) {
            if (var.parent.parent == null) {
                return var.parent;
            }
            List<Variable> path = new ArrayList<>();
            do {
                path.add(var);
                var = var.parent;
            } while (var.parent != null);
            for (Variable pathVar : path) {
                pathVar.parent = var;
            }
        }
        return var;
    }

    public boolean equalTo(Variable other, TypeConstraints constraints) {
        return equalToImpl(other, other, constraints);
    }

    private static boolean equalToImpl(Variable s, Variable t, TypeConstraints constraints) {
        if (s == t) {
            return true;
        }
        boolean ok = true;

        if (s != null && t != null) {
            ok &= constraints.equal(s.implementation, t.implementation);
            ok &= adoptImplementation(s, t, constraints);
        } else if (s.implementation != null) {
            ok &= adoptImplementation(t, s, constraints);
        } else if (t.implementation != null) {
            ok &= adoptImplementation(s, t, constraints);
        }

        return ok;
    }

    private static boolean adoptImplementation(Variable s, Variable t, TypeConstraints constraints) {
        boolean ok = true;
        s.implementation = t.implementation;

        Set<Variable> newDependencies = t.upperDependencies.stream().map(Variable::find).collect(Collectors.toSet());
        newDependencies.removeAll(s.upperDependencies.stream().map(Variable::find).collect(Collectors.toSet()));

        // When T <: S1 <: S2 <: ... <: Sn <: T then T = S1 = S2 = ... = Sn
        if (newDependencies.contains(s)) {
            for (Variable u : newDependencies) {
                ok &= s.equalTo(u, constraints);
            }
            t = t.find();
        } else {
            s.upperDependencies.addAll(newDependencies);
        }

        return ok;
    }

    public Reference createReference() {
        return new Reference(this);
    }

    public static class Reference {
        private Variable variable;

        Reference(Variable variable) {
            super();
            this.variable = variable;
        }

        public Variable getVariable() {
            variable = variable.find();
            return variable;
        }
    }
}
