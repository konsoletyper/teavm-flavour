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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeInference {
    private GenericTypeNavigator typeNavigator;
    private Map<TypeVar, InferenceVar> inferenceVars = new LinkedHashMap<>();
    private Set<InferenceVar> unresolvedVars = new HashSet<>();
    private LeastUpperBoundFinder lubFinder;
    List<TypeInferenceStatePoint> statePoints = new ArrayList<>();
    TypeInferenceStatePoint currentStatePoint;

    public TypeInference(GenericTypeNavigator typeNavigator) {
        this.typeNavigator = typeNavigator;
        lubFinder = new LeastUpperBoundFinder(typeNavigator);
        statePoints.add(new TypeInferenceStatePoint(this, 0));
        currentStatePoint = statePoints.get(0);
    }

    public TypeInferenceStatePoint createStatePoint() {
        TypeInferenceStatePoint statePoint = new TypeInferenceStatePoint(this, statePoints.size());
        TypeInferenceStatePoint previousStatePoint = currentStatePoint;
        statePoints.add(statePoint);
        currentStatePoint = statePoint;
        return previousStatePoint;
    }

    void rollBack(TypeInferenceStatePoint statePoint) {
        if (statePoint.addedTypeVars != null) {
            inferenceVars.keySet().removeAll(statePoint.addedTypeVars);
        }

        if (statePoint.inferenceVarsBackup != null) {
            for (Map.Entry<InferenceVar, InferenceVar> entry : statePoint.inferenceVarsBackup.entrySet()) {
                InferenceVar inferenceVar = entry.getKey();
                inferenceVar.restore(entry.getValue());
            }
        }

        if (statePoint.inferenceVarMapBackup != null) {
            for (Map.Entry<TypeVar, InferenceVar> entry : statePoint.inferenceVarMapBackup.entrySet()) {
                if (inferenceVars.containsKey(entry.getKey())) {
                    inferenceVars.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public boolean addVariables(Collection<? extends TypeVar> vars) {
        for (TypeVar var : vars) {
            if (inferenceVars.containsKey(var)) {
                continue;
            }

            InferenceVar inferenceVar = new InferenceVar(var);
            inferenceVars.put(var, inferenceVar);
            currentStatePoint.addTypeVar(var);
        }

        for (InferenceVar inferenceVar : inferenceVars.values()) {
            for (TypeVar typeVar : inferenceVar.variables) {
                for (GenericType lowerBound : typeVar.getLowerBound()) {
                    if (!inferenceVar.addLowerBound(lowerBound)) {
                        inferenceVar.fail();
                        return false;
                    }
                }
                for (GenericType upperBound : typeVar.getUpperBound()) {
                    if (!inferenceVar.addUpperBound(upperBound)) {
                        inferenceVar.fail();
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public boolean resolve() {
        unresolvedVars = new HashSet<>(inferenceVars.values());

        while (!unresolvedVars.isEmpty()) {
            unresolvedVars = new LinkedHashSet<>(inferenceVars.values().stream()
                    .map(InferenceVar::find)
                    .filter(v -> v.instantiation == null)
                    .collect(Collectors.toList()));

            Collection<InferenceVar> variablesToResolve = findVariablesToResolve();
            if (variablesToResolve.stream().noneMatch(v -> v.captureConversionBound != null)) {
                TypeInferenceStatePoint statePoint = createStatePoint();
                if (resolveVariablesSimple(variablesToResolve)) {
                    continue;
                }
                statePoint.restoreTo();
            }

            if (!resolveVariablesCaptured(variablesToResolve)) {
                return false;
            }
        }

        return true;
    }

    private boolean resolveVariablesSimple(Collection<? extends InferenceVar> variablesToResolve) {
        Map<InferenceVar, List<GenericType>> improperLowerBounds = new HashMap<>();
        Map<InferenceVar, List<GenericType>> improperUpperBounds = new HashMap<>();
        Map<InferenceVar, GenericType> improperExactBounds = new HashMap<>();
        for (InferenceVar inferenceVar : variablesToResolve) {
            improperLowerBounds.put(inferenceVar, inferenceVar.lowerBounds.stream()
                    .filter(bound -> !isProperType(bound)).collect(Collectors.toList()));
            improperUpperBounds.put(inferenceVar, inferenceVar.upperBounds.stream()
                    .filter(bound -> !isProperType(bound)).collect(Collectors.toList()));
            if (inferenceVar.exactBound != null && !isProperType(inferenceVar.exactBound)) {
                inferenceVar.takeSnapshot();
                improperExactBounds.put(inferenceVar, inferenceVar.exactBound);
                inferenceVar.exactBound = null;
            }

            if (!resolveSimple(inferenceVar)) {
                return false;
            }
        }

        for (InferenceVar inferenceVar : variablesToResolve) {
            inferenceVar.takeSnapshot();
            inferenceVar.instantiation = inferenceVar.pendingInstantiation;
            inferenceVar.exactBound = inferenceVar.pendingInstantiation;
        }

        for (InferenceVar inferenceVar : variablesToResolve) {
            for (GenericType bound : improperLowerBounds.get(inferenceVar)) {
                if (!inferenceVar.addLowerBound(bound.substitute(substitutions))) {
                    return false;
                }
            }
            for (GenericType bound : improperUpperBounds.get(inferenceVar)) {
                if (!inferenceVar.addUpperBound(bound.substitute(substitutions))) {
                    return false;
                }
            }

            GenericType exactBound = improperExactBounds.get(inferenceVar);
            if (exactBound != null) {
                if (!inferenceVar.addExactBound(exactBound.substitute(substitutions))) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean resolveVariablesCaptured(Collection<? extends InferenceVar> variablesToResolve) {
        for (InferenceVar var : variablesToResolve) {
            var.takeSnapshot();
        }

        List<? extends InferenceVar> originalVariables = new ArrayList<>(variablesToResolve);
        List<TypeVar> freshTypeVariables = new ArrayList<>();
        Map<TypeVar, GenericType> typeMap = new HashMap<>();
        for (int i = 0; i < originalVariables.size(); ++i) {
            TypeVar freshTypeVar = new TypeVar();
            freshTypeVariables.add(freshTypeVar);
            for (TypeVar originalTypeVar : originalVariables.get(i).variables) {
                typeMap.put(originalTypeVar, new GenericReference(freshTypeVar));
            }
        }
        Substitutions freshSubstitutions = new MapSubstitutions(typeMap);

        for (int i = 0; i < originalVariables.size(); ++i) {
            InferenceVar originalVar = originalVariables.get(i);
            List<GenericType> lowerBounds = originalVar.lowerBounds.stream()
                    .filter(this::isProperType)
                    .map(b -> b.substitute(substitutions))
                    .collect(Collectors.toList());
            if (!lowerBounds.isEmpty()) {
                freshTypeVariables.get(i).withLowerBound(lubFinder.find(lowerBounds));
            } else {
                List<GenericType> upperBounds = originalVar.upperBounds.stream()
                        .map(b -> b.substitute(substitutions))
                        .map(b -> b.substitute(freshSubstitutions))
                        .collect(Collectors.toList());
                freshTypeVariables.get(i).withUpperBound(intersect(upperBounds));
            }
        }

        // TODO: check bound consistency

        for (int i = 0; i < originalVariables.size(); ++i) {
            InferenceVar originalVar = originalVariables.get(i);
            originalVar.instantiation = new GenericReference(freshTypeVariables.get(i));
            originalVar.captureConversionBound = null;
        }

        return true;
    }

    private Set<InferenceVar> findVariablesToResolve() {
        calculateUnresolvedDependantVars();
        Set<InferenceVar> result = new HashSet<>();
        Set<InferenceVar> visited = new HashSet<>();
        Set<InferenceVar> visiting = new HashSet<>();
        List<InferenceVar> path = new ArrayList<>();
        for (InferenceVar var : unresolvedVars) {
            findVariablesToResolve(var, result, visited, visiting, path);
        }
        return result;
    }

    private void findVariablesToResolve(InferenceVar currentVar, Set<InferenceVar> result,
            Set<InferenceVar> visited, Set<InferenceVar> visiting, List<InferenceVar> path) {
        if (visited.contains(currentVar)) {
            return;
        }

        visiting.add(currentVar);
        path.add(currentVar);

        int bestMatchingIndex = path.size() - 1;
        boolean matched = true;
        for (InferenceVar dependantVar : currentVar.unresolvedDependantVars) {
            if (visiting.contains(dependantVar)) {
                bestMatchingIndex = Math.min(bestMatchingIndex, path.lastIndexOf(dependantVar));
            } else {
                matched = false;
                findVariablesToResolve(dependantVar, result, visited, visiting, path);
            }
        }

        if (matched) {
            result.addAll(path.subList(bestMatchingIndex, path.size()));
        }

        path.remove(path.size() - 1);
        visited.add(currentVar);
        visiting.remove(currentVar);
    }

    private void calculateUnresolvedDependantVars() {
        for (InferenceVar v : inferenceVars.values()) {
            v.unresolvedDependantVars.clear();
        }
        for (InferenceVar v : inferenceVars.values()) {
            Stream<Stream<GenericType>> stream = Stream.of(
                    v.lowerBounds.stream(),
                    v.upperBounds.stream(),
                    v.exactBound != null ? Stream.of(v.exactBound) : Stream.empty());
            List<InferenceVar> varsInConstraints = stream.flatMap(types -> types)
                    .flatMap(TypeInference.this::collectInferenceVars)
                    .filter(var -> var.instantiation == null)
                    .collect(Collectors.toList());

            if (v.captureConversionBound == null) {
                v.unresolvedDependantVars.addAll(varsInConstraints);
            } else {
                for (InferenceVar u : varsInConstraints) {
                    u.unresolvedDependantVars.add(v);
                }
                for (InferenceVar u : v.captureConversionBound.captureConversion.captureVars) {
                    if (u != v && u != null) {
                        v.unresolvedDependantVars.add(u);
                    }
                }
            }
        }
    }

    private boolean resolveSimple(InferenceVar inferenceVar) {
        if (inferenceVar.exactBound != null && isProperType(inferenceVar.exactBound)) {
            inferenceVar.pendingInstantiation = inferenceVar.exactBound;
            return true;
        }

        List<GenericType> lowerBounds = inferenceVar.lowerBounds.stream()
                .filter(this::isProperType)
                .map(b -> b.substitute(substitutions))
                .collect(Collectors.toList());
        if (!lowerBounds.isEmpty()) {
            inferenceVar.pendingInstantiation = lubFinder.find(lowerBounds);
            return true;
        }

        List<GenericType> upperBounds = inferenceVar.upperBounds.stream()
                .filter(this::isProperType)
                .map(b -> b.substitute(substitutions))
                .collect(Collectors.toList());
        if (!upperBounds.isEmpty()) {
            inferenceVar.pendingInstantiation = intersect(upperBounds);
            return true;
        }

        inferenceVar.pendingInstantiation = GenericType.OBJECT;

        return true;
    }

    private GenericType intersect(Collection<? extends GenericType> types) {
        if (types.isEmpty()) {
            return NullType.INSTANCE;
        }
        if (types.size() == 1) {
            return types.iterator().next();
        }

        Set<GenericType> result = new HashSet<>();
        Set<GenericClass> classes = new HashSet<>();
        Set<GenericType> arrays = new HashSet<>();

        List<GenericType> flattenedTypes = new ArrayList<>();
        for (GenericType type : types) {
            if (type instanceof IntersectionType) {
                flattenedTypes.addAll(((IntersectionType) type).getTypes());
            } else {
                flattenedTypes.add(type);
            }
        }

        for (GenericType type : flattenedTypes) {
            if (type instanceof GenericClass) {
                classes.add((GenericClass) type);
            } else if (type instanceof GenericArray) {
                arrays.add(((GenericArray) type).getElementType());
            } else {
                result.add(type);
            }
        }

        if (!classes.isEmpty()) {
            result.addAll(intersectClasses(classes));
        }
        if (!arrays.isEmpty()) {
            result.add(new GenericArray(IntersectionType.of(intersect(arrays))));
        }

        return IntersectionType.of(result);
    }

    private Collection<? extends GenericClass> intersectClasses(Collection<? extends GenericClass> classes) {
        if (classes.size() == 1) {
            return classes;
        }

        Set<GenericClass> allSuperClasses = new HashSet<>();
        for (GenericClass cls : classes) {
            List<GenericClass> path = typeNavigator.sublassPath(cls, "java.lang.Object");
            for (int i = 1; i < path.size(); ++i) {
                if (!allSuperClasses.add(path.get(i))) {
                    break;
                }
            }
        }

        return classes.stream().filter(cls -> !allSuperClasses.contains(cls)).collect(Collectors.toList());
    }

    public boolean equalConstraint(ValueType a, ValueType b) {
        if (a == b) {
            return true;
        }

        if (a instanceof GenericType) {
            a = ((GenericType) a).substitute(substitutions);
        }
        if (b instanceof GenericType) {
            b = ((GenericType) b).substitute(substitutions);
        }
        if (isProperType(a) && isProperType(b)) {
            return a.equals(b);
        }

        if (a instanceof NullType || b instanceof NullType) {
            return false;
        } else if (isInferenceVar(a) && isInferenceVar(b)) {
            InferenceVar var1 = inferenceVars.get(((GenericReference) a).getVar());
            InferenceVar var2 = inferenceVars.get(((GenericReference) b).getVar());
            return !var1.union(var2).inferenceFailed;
        } else if (isInferenceVar(a) && b instanceof GenericType) {
            return inferenceVars.get(((GenericReference) a).getVar()).addExactBound((GenericType) b);
        } else if (isInferenceVar(b) && a instanceof GenericType) {
            return inferenceVars.get(((GenericReference) b).getVar()).addExactBound((GenericType) a);
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
            return equalConstraint(s.getElementType(), t.getElementType());
        }
        return false;
    }

    private boolean equalConstraint(TypeArgument a, TypeArgument b) {
        return a.getVariance() == b.getVariance() && equalConstraint(a.getBound(), b.getBound());
    }

    public boolean subtypeConstraint(ValueType subtype, ValueType supertype) {
        if (subtype instanceof GenericType) {
            subtype = ((GenericType) subtype).substitute(substitutions);
        }
        if (supertype instanceof GenericType) {
            supertype = ((GenericType) supertype).substitute(substitutions);
        }

        if (subtype instanceof Primitive && supertype instanceof Primitive) {
            return TypeUtils.isPrimitiveSubType((Primitive) subtype, (Primitive) supertype);
        } else if (subtype instanceof Primitive && supertype instanceof GenericClass) {
            supertype = TypeUtils.tryUnbox((GenericType) supertype);
            return supertype instanceof Primitive
                    && TypeUtils.isPrimitiveSubType((Primitive) subtype, (Primitive) supertype);
        } else if (supertype instanceof Primitive && subtype instanceof GenericClass) {
            subtype = TypeUtils.tryUnbox((GenericType) subtype);
            return subtype instanceof Primitive
                    && TypeUtils.isPrimitiveSubType((Primitive) subtype, (Primitive) supertype);
        } else if (subtype instanceof NullType) {
            return !(supertype instanceof Primitive);
        } else if (isInferenceVar(subtype)) {
            supertype = TypeUtils.tryBox(supertype);
            if (!(supertype instanceof GenericType)) {
                return false;
            }
            InferenceVar inferenceVar = inferenceVars.get(((GenericReference) subtype).getVar());
            return inferenceVar.addUpperBound((GenericType) supertype);
        } else if (isInferenceVar(supertype)) {
            subtype = TypeUtils.tryBox(subtype);
            if (!(subtype instanceof GenericType)) {
                return false;
            }
            InferenceVar inferenceVar = inferenceVars.get(((GenericReference) supertype).getVar());
            return inferenceVar.addLowerBound((GenericType) subtype);
        } else if (subtype instanceof GenericClass && supertype instanceof GenericClass) {
            GenericClass subclass = (GenericClass) subtype;
            GenericClass superclass = (GenericClass) supertype;
            List<GenericClass> path = typeNavigator.sublassPath(subclass, superclass.getName());
            if (path == null) {
                return false;
            }

            List<? extends TypeArgument> subclassArgs = path.get(path.size() - 1).getArguments();
            List<? extends TypeArgument> superclassArgs = superclass.getArguments();
            if (subclassArgs.size() != subclassArgs.size()) {
                return false;
            }

            for (int i = 0; i < subclassArgs.size(); ++i) {
                if (!isContainedBy(subclassArgs.get(i), superclassArgs.get(i))) {
                    return false;
                }
            }

            return true;
        } else if (supertype instanceof GenericClass && ((GenericClass) supertype).getName().equals("java.lang.Object")
                && subtype instanceof GenericType) {
            return true;
        } else if (subtype instanceof GenericArray && supertype instanceof GenericArray) {
            return subtypeConstraint(((GenericArray) subtype).getElementType(),
                    ((GenericArray) supertype).getElementType());
        } else if (subtype instanceof GenericReference) {
            TypeVar var = ((GenericReference) subtype).getVar();
            if (supertype instanceof GenericReference && var == ((GenericReference) supertype).getVar()) {
                return true;
            }
            ValueType supertypeCopy = supertype;
            return var.getUpperBound().stream().anyMatch(ub -> subtypeConstraint(ub, supertypeCopy));
        } else {
            return false;
        }
    }

    private boolean isContainedBy(TypeArgument a, TypeArgument b) {
        if (a.getVariance() == Variance.COVARIANT && b.getVariance() == Variance.COVARIANT) {
            return subtypeConstraint(a.getBound(), b.getBound());
        } else if ((a.getVariance() == Variance.CONTRAVARIANT || a.getVariance() == Variance.INVARIANT)
                && b.getVariance() == Variance.CONTRAVARIANT) {
            return subtypeConstraint(b.getBound(), a.getBound());
        } else if (a.getVariance() == Variance.INVARIANT) {
            return equalConstraint(a.getBound(), b.getBound());
        } else {
            return false;
        }
    }

    public List<? extends TypeArgument> captureConversionConstraint(List<? extends TypeVar> typeParameters,
            List<? extends TypeArgument> typeArguments) {
        if (typeArguments.size() != typeArguments.size()) {
            throw new IllegalArgumentException("Number of type parameters (" + typeArguments.size()
                    + ") is not equal to number of type arguments (" + typeArguments.size() + ")");
        }

        List<TypeVar> captureTypeVars = new ArrayList<>();
        List<TypeArgument> resultList = new ArrayList<>();
        List<InferenceVar> captureVars = new ArrayList<>();
        for (int i = 0; i < typeParameters.size(); ++i) {
            TypeVar freshTypeVar = typeArguments.get(i).getVariance() != Variance.INVARIANT ? new TypeVar() : null;
            captureTypeVars.add(freshTypeVar);
            resultList.add(freshTypeVar != null
                    ? TypeArgument.invariant(new GenericReference(freshTypeVar))
                    : typeArguments.get(i));
        }
        if (!addVariables(captureTypeVars.stream().filter(Objects::nonNull).collect(Collectors.toList()))) {
            return null;
        }

        for (int i = 0; i < typeParameters.size(); ++i) {
            TypeVar freshTypeVar = captureTypeVars.get(i);
            captureVars.add(freshTypeVar != null ? inferenceVars.get(freshTypeVar) : null);
        }

        CaptureConversion capture = new CaptureConversion(captureVars, typeParameters, typeArguments);
        for (int i = 0; i < typeParameters.size(); ++i) {
            InferenceVar captureVar = captureVars.get(i);
            if (captureVar != null) {
                captureVar.captureConversionBound = new CaptureConversionBound(capture, i);
            }
        }

        for (int i = 0; i < typeParameters.size(); ++i) {
            InferenceVar captureVar = captureVars.get(i);
            if (captureVar == null) {
                continue;
            }

            for (GenericType lowerBound : typeParameters.get(i).getLowerBound()) {
                if (!captureVar.addLowerBound(lowerBound)) {
                    return null;
                }
            }
            for (GenericType upperBound : typeParameters.get(i).getUpperBound()) {
                if (!captureVar.addUpperBound(upperBound)) {
                    return null;
                }
            }

            TypeArgument typeArgument = typeArguments.get(i);
            switch (typeArgument.getVariance()) {
                case INVARIANT:
                    if (!captureVar.addExactBound(typeArgument.getBound())) {
                        return null;
                    }
                    break;
                case COVARIANT:
                    if (!captureVar.addUpperBound(typeArgument.getBound())) {
                        return null;
                    }
                    break;
                case CONTRAVARIANT:
                    if (!captureVar.addLowerBound(typeArgument.getBound())) {
                        return null;
                    }
                    break;
            }
        }

        return resultList;
    }

    public Substitutions getSubstitutions() {
        return substitutions;
    }

    private Substitutions substitutions = var -> {
        InferenceVar inferenceVar = inferenceVars.get(var);
        return inferenceVar != null ? inferenceVar.find().instantiation : null;
    };

    private boolean isInferenceVar(ValueType type) {
        return type instanceof GenericReference && isInferenceVar(((GenericReference) type).getVar());
    }

    private boolean isInferenceVar(TypeVar typeVar) {
        InferenceVar inferenceVar = inferenceVars.get(typeVar);
        return inferenceVar != null && inferenceVar.find().instantiation == null;
    }

    private boolean isProperType(ValueType type) {
        return !collectInferenceVars(type).findAny().isPresent();
    }

    private Stream<InferenceVar> collectInferenceVars(ValueType type) {
        if (type instanceof GenericReference) {
            InferenceVar inferenceVar = inferenceVars.get(((GenericReference) type).getVar());
            if (inferenceVar == null) {
                return Stream.empty();
            }
            inferenceVar = inferenceVar.find();
            return inferenceVar.instantiation == null ? Stream.of(inferenceVar) : Stream.empty();
        } else if (type instanceof GenericClass) {
            return ((GenericClass) type).getArguments().stream()
                    .map(arg -> arg.getBound())
                    .flatMap(this::collectInferenceVars);
        } else if (type instanceof GenericArray) {
            return collectInferenceVars(((GenericArray) type).getElementType());
        } else {
            return Stream.empty();
        }
    }

    class InferenceVar {
        InferenceVar parent;
        int rank;
        Set<TypeVar> variables = new LinkedHashSet<>();
        Set<GenericType> lowerBounds = new LinkedHashSet<>();
        Set<GenericType> upperBounds = new LinkedHashSet<>();
        CaptureConversionBound captureConversionBound;
        GenericType exactBound;
        GenericType instantiation;
        GenericType pendingInstantiation;
        boolean inferenceFailed;
        Set<InferenceVar> unresolvedDependantVars = new LinkedHashSet<>();

        InferenceVar(TypeVar var) {
            variables.add(var);
            lowerBounds.addAll(var.getLowerBound());
            upperBounds.addAll(var.getUpperBound());
        }

        private InferenceVar() {
        }

        InferenceVar backup() {
            InferenceVar copy = new InferenceVar();
            copy.parent = parent;
            copy.rank = rank;
            copy.variables.addAll(variables);
            copy.lowerBounds.addAll(lowerBounds);
            copy.upperBounds.addAll(upperBounds);
            copy.captureConversionBound = captureConversionBound;
            copy.exactBound = exactBound;
            copy.instantiation = instantiation;
            copy.inferenceFailed = inferenceFailed;
            return copy;
        }

        void restore(InferenceVar backup) {
            parent = backup.parent;
            rank = backup.rank;
            variables.retainAll(backup.variables);
            lowerBounds.retainAll(backup.lowerBounds);
            upperBounds.retainAll(backup.upperBounds);
            captureConversionBound = backup.captureConversionBound;
            exactBound = backup.exactBound;
            instantiation = backup.instantiation;
            inferenceFailed = backup.inferenceFailed;
        }

        void takeSnapshot() {
            currentStatePoint.backup(this);
        }

        InferenceVar find() {
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
                u.takeSnapshot();
                u.parent = v;
            }
            return v;
        }

        private TypeArgument getCaptureTypeArgument() {
            if (captureConversionBound == null) {
                return null;
            }
            return captureConversionBound.captureConversion.arguments.get(captureConversionBound.index);
        }

        private TypeVar getCaptureTypeParameter() {
            if (captureConversionBound == null) {
                return null;
            }
            return captureConversionBound.captureConversion.parameters.get(captureConversionBound.index);
        }

        private boolean shouldIncorporateWithCaptureConversion(GenericType type) {
            return captureConversionBound != null && type instanceof GenericReference && !isProperType(type);
        }

        boolean addExactBound(GenericType type) {
            if (shouldIncorporateWithCaptureConversion(type)) {
                return false;
            }

            type = type.substitute(substitutions);

            if (exactBound == null) {
                takeSnapshot();
                exactBound = type;
                for (GenericType lowerBound : lowerBounds) {
                    if (!subtypeConstraint(lowerBound, exactBound)) {
                        return false;
                    }
                }
                for (GenericType upperBound : upperBounds) {
                    if (!subtypeConstraint(exactBound, upperBound)) {
                        return false;
                    }
                }
            } else {
                if (!equalConstraint(exactBound, type)) {
                    return false;
                }
            }

            return true;
        }

        private GenericType substituteCaptureConversion(GenericType type) {
            return type.substitute(captureConversionBound.captureConversion.substitutions);
        }

        boolean addUpperBound(GenericType type) {
            if (!addUpperBoundNoCaptureConversion(type)) {
                return false;
            }

            if (shouldIncorporateWithCaptureConversion(type)) {
                TypeArgument argument = getCaptureTypeArgument();
                TypeVar parameter = getCaptureTypeParameter();
                GenericType parameterBound = !parameter.getUpperBound().isEmpty()
                        ? IntersectionType.of(parameter.getUpperBound())
                        : GenericType.OBJECT;
                switch (argument.getVariance()) {
                    case COVARIANT:
                        if (argument.getBound().equals(GenericType.OBJECT)) {
                            GenericType bound = substituteCaptureConversion(parameterBound);
                            if (!subtypeConstraint(bound, type)) {
                                return false;
                            }
                        }
                        if (parameterBound.equals(GenericType.OBJECT)) {
                            if (!subtypeConstraint(argument.getBound(), type)) {
                                return false;
                            }
                        }
                        break;

                    case CONTRAVARIANT:
                        GenericType bound = substituteCaptureConversion(parameterBound);
                        if (!subtypeConstraint(bound, type)) {
                            return false;
                        }
                        break;

                    case INVARIANT:
                        break;
                }
            }

            return true;
        }

        private boolean addUpperBoundNoCaptureConversion(GenericType type) {
            if (type instanceof GenericReference && variables.contains(((GenericReference) type).getVar())) {
                return true;
            }

            if (type instanceof IntersectionType) {
                return ((IntersectionType) type).getTypes().stream().allMatch(this::addUpperBound);
            }

            type = type.substitute(substitutions);

            takeSnapshot();
            if (!upperBounds.add(type)) {
                return true;
            }
            if (exactBound != null && !subtypeConstraint(exactBound, type)) {
                return false;
            }
            for (GenericType lowerBound : lowerBounds) {
                if (!subtypeConstraint(lowerBound, type)) {
                    return false;
                }
            }

            if (type instanceof GenericReference) {
                InferenceVar that = inferenceVars.get(((GenericReference) type).getVar());
                if (that != null) {
                    for (TypeVar var : variables) {
                        that.addLowerBound(new GenericReference(var));
                    }
                }
            }

            return true;
        }

        boolean addLowerBound(GenericType type) {
            if (!addLowerBoundNoCaptureConversion(type)) {
                return false;
            }

            if (shouldIncorporateWithCaptureConversion(type)) {
                TypeArgument argument = getCaptureTypeArgument();

                switch (argument.getVariance()) {
                    case COVARIANT:
                        return false;

                    case CONTRAVARIANT:
                        if (!subtypeConstraint(type, argument.getBound())) {
                            return false;
                        }
                        break;

                    case INVARIANT:
                        break;
                }
            }

            return true;
        }

        private boolean addLowerBoundNoCaptureConversion(GenericType type) {
            if (type instanceof GenericReference && variables.contains(((GenericReference) type).getVar())) {
                return true;
            }

            type = type.substitute(substitutions);

            takeSnapshot();
            if (!lowerBounds.add(type)) {
                return true;
            }
            if (exactBound != null && !subtypeConstraint(type, exactBound)) {
                return false;
            }
            for (GenericType upperBound : upperBounds) {
                if (!subtypeConstraint(type, upperBound)) {
                    return false;
                }
            }

            if (type instanceof GenericReference) {
                InferenceVar that = inferenceVars.get(((GenericReference) type).getVar());
                if (that != null) {
                    for (TypeVar var : variables) {
                        that.addUpperBound(new GenericReference(var));
                    }
                }
            }

            return true;
        }

        InferenceVar union(InferenceVar other) {
            return this.find().unionImpl(other.find());
        }

        private InferenceVar unionImpl(InferenceVar other) {
            if (this == other) {
                return this;
            }
            takeSnapshot();
            other.takeSnapshot();
            if (rank > other.rank) {
                other.parent = this;
                if (!mergeData(other)) {
                    fail();
                }
                return this;
            } else if (rank < other.rank) {
                parent = other;
                if (!other.mergeData(this)) {
                    fail();
                }
                return other;
            } else {
                other.parent = this;
                ++rank;
                if (!mergeData(other)) {
                    fail();
                }
                return this;
            }
        }

        private boolean mergeData(InferenceVar other) {
            if (instantiation != null && other.instantiation != null) {
                return instantiation.equals(other.instantiation);
            } else if (instantiation != null) {
                return other.addExactBound(instantiation);
            } else if (other.instantiation != null) {
                return addExactBound(other.instantiation);
            }

            variables.addAll(other.variables);

            List<GenericType> existingLowerBounds = new ArrayList<>(lowerBounds);
            List<GenericType> existingUpperBounds = new ArrayList<>(upperBounds);
            List<GenericType> newLowerBounds = new ArrayList<>(other.lowerBounds);
            List<GenericType> newUpperBounds = new ArrayList<>(other.upperBounds);
            GenericType existingExactBound = exactBound;
            GenericType newExactBound = other.exactBound;

            if (existingExactBound != null) {
                if (newExactBound != null && !equalConstraint(existingExactBound, newExactBound)) {
                    return false;
                }
            } else {
                exactBound = newExactBound;
            }

            lowerBounds.addAll(newLowerBounds);
            upperBounds.addAll(newUpperBounds);

            if (!incorporateLowerAndUpperBoundsWithExactBound(newExactBound, existingLowerBounds,
                    existingUpperBounds)) {
                return false;
            }
            if (!incorporateLowerAndUpperBoundsWithExactBound(existingExactBound, newLowerBounds, newUpperBounds)) {
                return false;
            }

            if (!incorporateLowerAndUpperBounds(existingLowerBounds, newUpperBounds)) {
                return false;
            }
            if (!incorporateLowerAndUpperBounds(newLowerBounds, existingUpperBounds)) {
                return false;
            }

            for (TypeVar var : other.variables) {
                if (currentStatePoint.inferenceVarMapBackup == null) {
                    currentStatePoint.inferenceVarMapBackup = new HashMap<>();
                }
                InferenceVar old = inferenceVars.put(var, this);
                currentStatePoint.inferenceVarMapBackup.put(var, old);
            }

            return true;
        }

        private boolean incorporateLowerAndUpperBoundsWithExactBound(GenericType exactBound,
                List<GenericType> lowerBounds, List<GenericType> upperBounds) {
            if (exactBound != null) {
                for (GenericType lowerBound : lowerBounds) {
                    if (!subtypeConstraint(lowerBound, exactBound)) {
                        return false;
                    }
                }
                for (GenericType upperBound : upperBounds) {
                    if (!subtypeConstraint(exactBound, upperBound)) {
                        return false;
                    }
                }
            }

            return true;
        }

        private boolean incorporateLowerAndUpperBounds(List<GenericType> lowerBounds, List<GenericType> upperBounds) {
            for (GenericType lowerBound : lowerBounds) {
                for (GenericType upperBound : upperBounds) {
                    if (!subtypeConstraint(lowerBound, upperBound)) {
                        return false;
                    }
                }
            }

            return true;
        }

        void fail() {
            takeSnapshot();
            if (!inferenceFailed) {
                inferenceFailed = true;
            }
        }
    }

    class CaptureConversion {
        List<? extends InferenceVar> captureVars;
        List<? extends TypeVar> parameters;
        List<? extends TypeArgument> arguments;
        Substitutions substitutions;

        CaptureConversion(List<? extends InferenceVar> captureVars, List<? extends TypeVar> parameters,
                List<? extends TypeArgument> arguments) {
            this.captureVars = captureVars;
            this.parameters = parameters;
            this.arguments = arguments;

            MapSubstitutions substitutions = new MapSubstitutions(new HashMap<>());
            for (int i = 0; i < captureVars.size(); ++i) {
                InferenceVar capturingInferenceVar = captureVars.get(i);
                if (capturingInferenceVar == null) {
                    continue;
                }
                TypeVar capturingVar = capturingInferenceVar.variables.iterator().next();
                substitutions.getMap().put(parameters.get(i), new GenericReference(capturingVar));
                inferenceVars.put(parameters.get(i), captureVars.get(i));
            }
            this.substitutions = substitutions;
        }
    }

    class CaptureConversionBound {
        CaptureConversion captureConversion;
        int index;

        CaptureConversionBound(CaptureConversion captureConversion, int index) {
            this.captureConversion = captureConversion;
            this.index = index;
        }
    }
}
