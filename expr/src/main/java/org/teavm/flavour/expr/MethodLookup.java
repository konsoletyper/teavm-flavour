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
import java.util.function.Function;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.MethodWithFreshTypeVars;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeInferenceStatePoint;
import org.teavm.flavour.expr.type.TypeUtils;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

public class MethodLookup {
    private TypeInference inference;
    private GenericTypeNavigator navigator;
    private TypeEstimator typeEstimator;
    private List<GenericMethod> candidates = new ArrayList<>();
    private List<GenericMethod> safeCandidates = Collections.unmodifiableList(candidates);
    private boolean varArgs;
    private ValueType returnType;

    public MethodLookup(TypeInference inference, ClassResolver classResolver, GenericTypeNavigator navigator,
            Scope scope) {
        this.inference = inference;
        this.navigator = navigator;
        typeEstimator = new TypeEstimator(inference, classResolver, navigator, scope);
    }

    public GenericMethod lookupVirtual(Collection<GenericClass> classes, String name, List<Expr> args) {
        return lookupMethod(classes, name, args, false);
    }

    public GenericMethod lookupStatic(Collection<GenericClass> classes, String name, List<Expr> args) {
        return lookupMethod(classes, name, args, true);
    }

    private GenericMethod lookupMethod(Collection<GenericClass> classes, String name, List<Expr> args,
            boolean isStatic) {
        varArgs = false;
        returnType = null;
        candidates.clear();
        candidates.addAll(findAllMethods(classes, name, isStatic));
        if (candidates.isEmpty()) {
            return null;
        }

        GenericMethod result = lookupMethodStrict(args);
        if (result != null) {
            return result;
        }
        result = lookupMethodCompatible(args);
        if (result != null) {
            return result;
        }
        result = lookupVarargMethod(args);
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

    private GenericMethod lookupMethodStrict(List<Expr> args) {
        return lookup(params -> {
            if (params.length != args.size()) {
                return false;
            }

            for (int i = 0; i < params.length; ++i) {
                if (args.get(i) == null) {
                    continue;
                }
                ValueType argType = typeEstimator.estimate(args.get(i), params[i]);
                if (argType != null && !inference.equalConstraint(argType, params[i])) {
                    return false;
                }
            }

            return true;
        });
    }

    private GenericMethod lookupMethodCompatible(List<Expr> args) {
        return lookup(params -> {
            if (params.length != args.size()) {
                return false;
            }

            for (int i = 0; i < params.length; ++i) {
                if (args.get(i) == null) {
                    continue;
                }
                ValueType argType = typeEstimator.estimate(args.get(i), params[i]);
                if (argType != null && !inference.subtypeConstraint(argType, params[i])) {
                    return false;
                }
            }

            return true;
        });
    }

    private GenericMethod lookupVarargMethod(List<Expr> args) {
        return lookup(params -> {
            if (params.length == 0 || params.length > args.size() - 1) {
                return false;
            }

            ValueType lastParam;
            ValueType lastParamType = params[params.length - 1];
            if (lastParamType instanceof PrimitiveArray) {
                lastParam = ((PrimitiveArray) lastParamType).getElementType();
            } else if (lastParamType instanceof GenericArray) {
                lastParam = ((GenericArray) lastParamType).getElementType();
            } else {
                return false;
            }

            for (int i = 0; i < params.length - 1; ++i) {
                if (args.get(i) == null) {
                    continue;
                }

                ValueType argType = typeEstimator.estimate(args.get(i), params[i]);
                if (argType != null && !inference.subtypeConstraint(argType, params[i])) {
                    return false;
                }
            }

            for (int i = params.length - 1; i < args.size(); ++i) {
                ValueType argType = typeEstimator.estimate(args.get(i), lastParam);
                if (argType != null && !inference.subtypeConstraint(argType, lastParam)) {
                    return false;
                }
            }

            return true;
        });
    }

    private GenericMethod lookup(Function<ValueType[], Boolean> constraintSupplier) {
        GenericMethod result = null;
        GenericMethod resultWithFixedVars = null;
        TypeVar[] bestMatchingTypeVars = null;
        TypeInferenceStatePoint statePoint = inference.createStatePoint();

        for (GenericMethod method : candidates) {
            statePoint.restoreTo();

            MethodWithFreshTypeVars methodWithFreshTypeVars = TypeUtils.withFreshTypeVars(method, inference);
            if (methodWithFreshTypeVars == null) {
                continue;
            }
            method = methodWithFreshTypeVars.getMethod();

            ValueType[] paramTypes = method.getActualParameterTypes();
            if (!constraintSupplier.apply(paramTypes)) {
                continue;
            }

            if (!inference.resolve()) {
                continue;
            }
            GenericMethod methodWithFixedVars = method.substitute(inference.getSubstitutions());

            if (result != null) {
                if (isMoreSpecific(methodWithFixedVars, resultWithFixedVars)) {
                    resultWithFixedVars = methodWithFixedVars;
                    result = method;
                    bestMatchingTypeVars = methodWithFreshTypeVars.getFreshTypeVars();
                    returnType = method.getActualReturnType();
                } else if (!isMoreSpecific(resultWithFixedVars, methodWithFixedVars)) {
                    statePoint.restoreTo();
                    return null;
                }
            } else {
                resultWithFixedVars = methodWithFixedVars;
                result = method;
                bestMatchingTypeVars = methodWithFreshTypeVars.getFreshTypeVars();
                returnType = method.getActualReturnType();
            }
        }

        statePoint.restoreTo();

        if (result != null) {
            boolean ok = inference.addVariables(Arrays.asList(bestMatchingTypeVars));
            assert ok;

            ok = constraintSupplier.apply(result.getActualParameterTypes());
            assert ok;
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

        Map<TypeVar, TypeArgument> substitutionMap = new HashMap<>();
        TypeVar[] typeVars = desc.getTypeVariables();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutionMap.put(typeVars[i], cls.getArguments().get(i));
        }
        for (MethodDescriber methodDesc : desc.getMethods()) {
            if (!methodDesc.getName().equals(name) || methodDesc.isStatic() != isStatic) {
                continue;
            }
            ValueType[] params = Arrays.stream(methodDesc.getParameterTypes())
                    .map(arg -> {
                        if (arg instanceof GenericType) {
                            arg = ((GenericType) arg).substituteArgs(substitutionMap::get);
                        }
                        return arg;
                    })
                    .toArray(ValueType[]::new);
            ValueType returnType = methodDesc.getReturnType();
            if (returnType instanceof GenericType) {
                returnType = ((GenericType) returnType).substituteArgs(substitutionMap::get);
            }
            GenericMethod method = new GenericMethod(methodDesc, cls, params, returnType);
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

        ValueType[] specificArgs = specific.getActualParameterTypes();
        ValueType[] generalArgs = general.getActualParameterTypes();
        for (int i = 0; i < specificArgs.length; ++i) {
            if (!CompilerCommons.isLooselyCompatibleType(generalArgs[i], specificArgs[i], navigator)) {
                return false;
            }
        }

        if (specific.getActualReturnType() == null && general.getActualReturnType() == null) {
            return true;
        }

        if (specific.getActualReturnType() == null || general.getActualReturnType() == null) {
            return false;
        }

        return CompilerCommons.isLooselyCompatibleType(specific.getActualReturnType(), general.getActualReturnType(),
                navigator);
    }
}
