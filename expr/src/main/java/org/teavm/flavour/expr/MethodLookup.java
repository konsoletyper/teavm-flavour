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
package org.teavm.flavour.expr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.MapSubstitutions;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

public class MethodLookup {
    private GenericTypeNavigator navigator;
    private List<GenericMethod> candidates = new ArrayList<>();
    private List<GenericMethod> safeCandidates = Collections.unmodifiableList(candidates);
    private boolean varArgs;
    private ValueType returnType;

    public MethodLookup(GenericTypeNavigator navigator) {
        this.navigator = navigator;
    }

    public GenericMethod lookupVirtual(Collection<GenericClass> classes, String name, ValueType[] args,
            ValueType returnType) {
        return lookupMethod(classes, name, args, returnType, false);
    }

    public GenericMethod lookupStatic(Collection<GenericClass> classes, String name, ValueType[] args,
            ValueType returnType) {
        return lookupMethod(classes, name, args, returnType, true);
    }

    private GenericMethod lookupMethod(Collection<GenericClass> classes, String name, ValueType[] args,
            ValueType expectedReturnType, boolean isStatic) {
        varArgs = false;
        returnType = null;
        candidates.clear();
        candidates.addAll(findAllMethods(classes, name, isStatic));
        if (candidates.isEmpty()) {
            return null;
        }

        GenericMethod result = lookupMethodStrict(args, expectedReturnType);
        if (result != null) {
            return result;
        }
        result = lookupMethodCompatible(args, expectedReturnType);
        if (result != null) {
            return result;
        }
        result = lookupVarargMethod(args, expectedReturnType);
        if (result != null) {
            varArgs = true;
        }
        return result;
    }

    public boolean isVarArgs() {
        return varArgs;
    }

    public List<GenericMethod> getCandidates() {
        return safeCandidates;
    }

    public ValueType getReturnType() {
        return returnType;
    }

    private GenericMethod lookupMethodStrict(ValueType[] args, ValueType expectedReturnType) {
        GenericMethod result = null;
        lookup: for (GenericMethod method : candidates) {
            ValueType[] paramTypes = method.getActualArgumentTypes();
            if (paramTypes.length != args.length) {
                continue;
            }
            TypeInference inference = new TypeInference(navigator);
            for (int i = 0; i < paramTypes.length; ++i) {
                if (args[i] != null && !TypeUtil.same(args[i], paramTypes[i], inference)) {
                    continue lookup;
                }
            }
            if (expectedReturnType != null) {
                if (method.getActualReturnType() == null
                        || !TypeUtil.subtype(method.getActualReturnType(), expectedReturnType, inference)) {
                    continue;
                }
            }

            method = method.substitute(inference.getSubstitutions());
            if (result != null) {
                if (isMoreSpecific(method, result)) {
                    result = method;
                    returnType = inferReturnType(method, inference);
                } else if (!isMoreSpecific(result, method)) {
                    return null;
                }
            } else {
                result = method;
                returnType = inferReturnType(method, inference);
            }
        }

        return result;
    }

    private GenericMethod lookupMethodCompatible(ValueType[] args, ValueType expectedReturnType) {
        GenericMethod result = null;
        lookup: for (GenericMethod method : candidates) {
            ValueType[] paramTypes = method.getActualArgumentTypes();
            if (paramTypes.length != args.length) {
                continue;
            }
            TypeInference inference = new TypeInference(navigator);
            for (int i = 0; i < paramTypes.length; ++i) {
                if (args[i] != null && !TypeUtil.subtype(args[i], paramTypes[i], inference)) {
                    continue lookup;
                }
            }
            if (expectedReturnType != null) {
                if (method.getActualReturnType() == null
                        || !TypeUtil.subtype(method.getActualReturnType(), expectedReturnType, inference)) {
                    continue;
                }
            }

            method = method.substitute(inference.getSubstitutions());
            if (result != null) {
                if (isMoreSpecific(method, result)) {
                    result = method;
                    returnType = inferReturnType(method, inference);
                } else if (!isMoreSpecific(result, method)) {
                    return null;
                }
            } else {
                result = method;
                inferReturnType(result, inference);
            }
        }
        return result;
    }

    private GenericMethod lookupVarargMethod(ValueType[] args, ValueType expectedReturnType) {
        GenericMethod result = null;
        lookup: for (GenericMethod method : candidates) {
            if (!method.getDescriber().isVariableArgument()) {
                continue;
            }
            ValueType[] paramTypes = method.getActualArgumentTypes();
            if (args.length < paramTypes.length - 1) {
                continue;
            }

            TypeInference inference = new TypeInference(navigator);
            for (int i = 0; i < paramTypes.length - 1; ++i) {
                if (args[i] != null && !TypeUtil.subtype(args[i], paramTypes[i], inference)) {
                    continue lookup;
                }
            }
            if (expectedReturnType != null) {
                if (method.getActualReturnType() == null
                        || !TypeUtil.subtype(method.getActualReturnType(), expectedReturnType, inference)) {
                    continue;
                }
            }

            ValueType lastParam = ((GenericArray) paramTypes[paramTypes.length - 1]).getElementType();
            for (int i = paramTypes.length - 1; i < args.length; ++i) {
                if (!TypeUtil.subtype(args[i], lastParam, inference)) {
                    continue lookup;
                }
            }
            method = method.substitute(inference.getSubstitutions());
            if (result != null) {
                return null;
            } else {
                result = method;
                returnType = inferReturnType(result, inference);
            }
        }

        return result;
    }

    private List<GenericMethod> findAllMethods(Collection<GenericClass> classes, String name, boolean isStatic) {
        List<GenericMethod> methods = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (GenericClass cls : classes) {
            findAllMethodsRec(cls, name, isStatic, visited, methods);
        }
        return methods;
    }

    private void findAllMethodsRec(GenericClass cls, String name, boolean isStatic, Set<String> visited,
            List<GenericMethod> methods) {
        if (!visited.add(cls.getName())) {
            return;
        }
        ClassDescriber desc = navigator.getClassRepository().describe(cls.getName());
        if (desc == null) {
            return;
        }

        Map<TypeVar, GenericType> substitutionMap = new HashMap<>();
        TypeVar[] typeVars = desc.getTypeVariables();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutionMap.put(typeVars[i], cls.getArguments().get(i));
        }
        MapSubstitutions substitutions = new MapSubstitutions(substitutionMap);
        for (MethodDescriber methodDesc : desc.getMethods()) {
            if (!methodDesc.getName().equals(name) || methodDesc.isStatic() != isStatic) {
                continue;
            }
            ValueType[] args = Arrays.stream(methodDesc.getArgumentTypes())
                    .map(arg -> arg.substitute(substitutions))
                    .toArray(sz -> new ValueType[sz]);
            ValueType returnType = methodDesc.getReturnType();
            if (returnType != null) {
                returnType = returnType.substitute(substitutions);
            }
            GenericMethod method = new GenericMethod(methodDesc, cls, args, returnType);
            methods.add(method);
        }

        GenericClass parentClass = navigator.getParent(cls);
        if (parentClass != null) {
            findAllMethodsRec(parentClass, name, isStatic, visited, methods);
        }
        for (GenericClass iface : navigator.getInterfaces(cls)) {
            findAllMethodsRec(iface, name, isStatic, visited, methods);
        }
    }

    private boolean isMoreSpecific(GenericMethod specific, GenericMethod general) {
        if (!specific.getDescriber().isStatic() && general.getDescriber().isStatic()) {
            if (navigator.sublassPath(specific.getActualOwner(), general.getActualOwner().getName()) == null) {
                return false;
            }
        }

        ValueType[] specificArgs = specific.getActualArgumentTypes();
        ValueType[] generalArgs = general.getActualArgumentTypes();
        TypeInference inference = new TypeInference(navigator);
        for (int i = 0; i < specificArgs.length; ++i) {
            if (!TypeUtil.subtype(specificArgs[i], generalArgs[i], inference)) {
                return false;
            }
        }
        return TypeUtil.subtype(general.getActualReturnType(), specific.getActualReturnType(), inference);
    }

    private ValueType inferReturnType(GenericMethod method, TypeInference inference) {
        if (method == null || method.getActualReturnType() == null) {
            return null;
        }
        if (method.getActualReturnType() instanceof GenericType) {
            return method.getActualReturnType().substitute(inference.getSubstitutions());
        } else {
            return method.getActualReturnType();
        }
    }
}
