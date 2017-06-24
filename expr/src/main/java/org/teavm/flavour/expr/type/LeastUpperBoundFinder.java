/*
 *  Copyright 2017 Alexey Andreev.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.type.meta.ClassDescriber;

public class LeastUpperBoundFinder {
    private GenericTypeNavigator typeNavigator;
    private Set<Set<TypeArgument>> cache = new HashSet<>();

    public LeastUpperBoundFinder(GenericTypeNavigator typeNavigator) {
        this.typeNavigator = typeNavigator;
    }

    public GenericType find(List<GenericType> types) {
        Set<GenericType> mec = calculateMinimalErasedCandidateSet(types);
        Map<GenericType, List<GenericType>> relevant = findRelevant(types, mec);
        return intersectArrays(IntersectionType.of(relevant.values().stream()
                .map(this::leastContainingInvocation)
                .collect(Collectors.toList())));
    }

    private GenericType intersectArrays(GenericType type) {
        if (!(type instanceof IntersectionType)) {
            return type;
        }

        Collection<? extends GenericType> types = ((IntersectionType) type).getTypes();
        Set<GenericType> allTypes = new HashSet<>();
        Set<GenericType> arrayTypes = new LinkedHashSet<>();
        for (GenericType componentType : types) {
            if (componentType instanceof GenericArray) {
                GenericArray array = (GenericArray) componentType;
                arrayTypes.add(array.getElementType());
            } else {
                allTypes.add(componentType);
            }
        }

        if (!arrayTypes.isEmpty()) {
            allTypes.add(new GenericArray(intersectArrays(IntersectionType.of(arrayTypes))));
        }
        return IntersectionType.of(allTypes);
    }

    private Map<GenericType, List<GenericType>> findRelevant(List<GenericType> types, Set<GenericType> mec) {
        Set<GenericType> visited = new HashSet<>();
        Map<GenericType, List<GenericType>> relevant = new LinkedHashMap<>();
        for (GenericType type : types) {
            findRelevant(type, mec, visited, relevant);
        }
        return relevant;
    }

    private void findRelevant(GenericType type, Set<GenericType> mec, Set<GenericType> visited,
            Map<GenericType, List<GenericType>> relevant) {
        if (!visited.add(type)) {
            return;
        }

        if (mec.contains(type.erasure())) {
            relevant.computeIfAbsent(type.erasure(), k -> new ArrayList<>()).add(type);
        }

        for (GenericType supertype : getSupertypes(type)) {
            findRelevant(supertype, mec, visited, relevant);
        }
    }

    private GenericType leastContainingInvocation(List<GenericType> types) {
        if (types.get(0) instanceof GenericClass) {
            List<GenericClass> classes = types.stream().map(t -> (GenericClass) t).collect(Collectors.toList());
            assert allClassesHaveSameNameAndArgCount(classes);
            List<TypeArgument> newArguments = new ArrayList<>();
            int argCount = classes.get(0).getArguments().size();
            for (int i = 0; i < argCount; ++i) {
                int argIndex = i;
                List<TypeArgument> arguments = classes.stream()
                        .map(cls -> cls.getArguments().get(argIndex))
                        .collect(Collectors.toList());
                newArguments.add(leastContainingTypeArgument(arguments));
            }
            return new GenericClass(classes.get(0).getName(), newArguments);
        } else if (types.get(0) instanceof GenericArray) {
            List<GenericArray> arrays = types.stream().map(t -> (GenericArray) t).collect(Collectors.toList());
            List<GenericType> arguments = arrays.stream()
                    .map(array -> array.getElementType())
                    .collect(Collectors.toList());
            return new GenericArray(find(arguments));
        } else {
            assert types.stream().allMatch(t -> types.get(0).equals(t));
            return types.get(0);
        }
    }

    private boolean allClassesHaveSameNameAndArgCount(List<GenericClass> classes) {
        String name = classes.get(0).getName();
        int argCount = classes.get(0).getArguments().size();
        for (int i = 1; i < classes.size(); ++i) {
            if (!name.equals(classes.get(i).getName()) || argCount != classes.get(i).getArguments().size()) {
                return false;
            }
        }
        return true;
    }

    private TypeArgument leastContainingTypeArgument(List<TypeArgument> arguments) {
        Set<TypeArgument> argumentSet = new HashSet<>(arguments);
        if (cache.add(argumentSet)) {
            TypeArgument result = arguments.stream().reduce(this::leastContainingTypeArgument).get();
            cache.remove(argumentSet);
            return result;
        } else {
            return TypeArgument.covariant(GenericType.OBJECT);
        }
    }

    private TypeArgument leastContainingTypeArgument(TypeArgument a, TypeArgument b) {
        if (a.getVariance() == Variance.INVARIANT && b.getVariance() == Variance.INVARIANT) {
            if (a.getBound().equals(b.getBound())) {
                return a;
            } else {
                return TypeArgument.covariant(find(Arrays.asList(a.getBound(), b.getBound())));
            }
        } else if (a.getVariance() == Variance.COVARIANT && b.getVariance() == Variance.COVARIANT
                || a.getVariance() == Variance.COVARIANT && b.getVariance() == Variance.INVARIANT
                || a.getVariance() == Variance.INVARIANT && b.getVariance() == Variance.COVARIANT) {
            return TypeArgument.covariant(find(Arrays.asList(a.getBound(), b.getBound())));
        } else if (a.getVariance() == Variance.CONTRAVARIANT && b.getVariance() == Variance.CONTRAVARIANT
                || a.getVariance() == Variance.CONTRAVARIANT && b.getVariance() == Variance.INVARIANT
                || a.getVariance() == Variance.INVARIANT && b.getVariance() == Variance.CONTRAVARIANT) {
            return TypeArgument.contravariant(IntersectionType.of(a.getBound(), b.getBound()));
        } else if (a.getVariance() == Variance.COVARIANT && b.getVariance() == Variance.CONTRAVARIANT
                || a.getVariance() == Variance.CONTRAVARIANT && b.getVariance() == Variance.COVARIANT) {
            if (a.getBound().equals(b.getBound())) {
                return TypeArgument.invariant(a.getBound());
            } else {
                return TypeArgument.covariant(GenericType.OBJECT);
            }
        } else {
            return TypeArgument.covariant(new GenericClass("java.lang.Object"));
        }
    }

    private Collection<? extends GenericType> getSupertypes(GenericType type) {
        Set<GenericType> supertypes = new LinkedHashSet<>();
        if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass) type;
            GenericClass parent = typeNavigator.getParent(cls);
            if (parent != null) {
                supertypes.add(parent);
            }
            supertypes.addAll(Arrays.asList(typeNavigator.getInterfaces(cls)));
        } else if (type instanceof GenericArray) {
            GenericType elementType = ((GenericArray) type).getElementType();
            for (GenericType elementSupertype : getSupertypes(elementType)) {
                supertypes.add(new GenericArray(elementSupertype));
            }
            supertypes.add(GenericType.OBJECT);
        } else if (type instanceof PrimitiveArray) {
            supertypes.add(GenericType.OBJECT);
        } else if (type instanceof GenericReference) {
            TypeVar typeVar = ((GenericReference) type).getVar();
            if (!typeVar.getUpperBound().isEmpty()) {
                supertypes.add(typeVar.getUpperBound().iterator().next());
            } else {
                supertypes.add(GenericType.OBJECT);
            }
        } else if (type instanceof IntersectionType) {
            ((IntersectionType) type).getTypes().stream()
                    .flatMap(t -> getSupertypes(t).stream())
                    .forEach(supertypes::add);
        }
        return supertypes;
    }

    private Set<GenericType> calculateMinimalErasedCandidateSet(List<GenericType> types) {
        MinimalErasedCandidateSet mec = new MinimalErasedCandidateSet(typeNavigator);
        for (GenericType type : types) {
            mec.add(type.erasure());
        }
        return mec.calculate();
    }

    static class MinimalErasedCandidateSet {
        private GenericTypeNavigator typeNavigator;
        private Map<GenericType, Node> nodes = new LinkedHashMap<>();
        private int count;

        MinimalErasedCandidateSet(GenericTypeNavigator typeNavigator) {
            this.typeNavigator = typeNavigator;
        }

        Set<GenericType> calculate() {
            return nodes.values().stream()
                    .filter(node -> node.count == count && node.lowermost)
                    .map(node -> node.type)
                    .collect(Collectors.toSet());
        }

        void add(GenericType type) {
            ++count;
            addImpl(getNode(type), true);
        }

        private void addImpl(Node node, boolean lowermost) {
            if (node.mark == count) {
                return;
            }
            node.mark = count;
            node.lowermost = lowermost;
            boolean newLowermost = ++node.count < count;
            for (Node parent : node.parentNodes) {
                addImpl(parent, newLowermost);
            }
        }

        private Node getNode(GenericType type) {
            Node node = nodes.get(type);
            if (node == null) {
                node = new Node(type);
                nodes.put(type, node);
                for (GenericType supertype : getSupertypes(type)) {
                    node.parentNodes.add(getNode(supertype));
                }
            }
            return node;
        }

        private Collection<? extends GenericType> getSupertypes(GenericType type) {
            Set<GenericType> supertypes = new LinkedHashSet<>();
            if (type instanceof GenericClass) {
                ClassDescriber cls = typeNavigator.getClassRepository().describe(((GenericClass) type).getName());
                if (cls != null) {
                    if (cls.getSupertype() != null) {
                        supertypes.add(new GenericClass(cls.getSupertype().getName()));
                    }
                    for (GenericClass itf : cls.getInterfaces()) {
                        supertypes.add(new GenericClass(itf.getName()));
                    }
                    if (cls.isInterface() && cls.getSupertype() == null) {
                        supertypes.add(GenericType.OBJECT);
                    }
                }
            } else if (type instanceof GenericArray) {
                GenericType elementType = ((GenericArray) type).getElementType();
                for (GenericType elementSupertype : getSupertypes(elementType)) {
                    supertypes.add(new GenericArray(elementSupertype));
                }
                supertypes.add(GenericType.OBJECT);
            } else if (type instanceof PrimitiveArray) {
                supertypes.add(GenericType.OBJECT);
            } else if (type instanceof IntersectionType) {
                ((IntersectionType) type).getTypes().stream()
                        .flatMap(t -> getSupertypes(t).stream())
                        .forEach(supertypes::add);
            }
            return supertypes;
        }

        static class Node {
            GenericType type;
            List<Node> parentNodes = new ArrayList<>();
            int mark;
            int count;
            boolean lowermost;

            Node(GenericType type) {
                this.type = type;
            }
        }
    }
}
