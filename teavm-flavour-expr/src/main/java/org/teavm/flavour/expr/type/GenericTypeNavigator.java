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

import java.util.*;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.FieldDescriber;
import org.teavm.flavour.expr.type.meta.MethodDescriber;

/**
 *
 * @author Alexey Andreev
 */
public class GenericTypeNavigator {
    private ClassDescriberRepository classRepository;

    public GenericTypeNavigator(ClassDescriberRepository classRepository) {
        this.classRepository = classRepository;
    }

    public ClassDescriberRepository getClassRepository() {
        return classRepository;
    }

    public List<GenericClass> sublassPath(GenericClass subclass, String superclass) {
        List<GenericClass> path = new ArrayList<>();
        if (!subclassPathImpl(subclass, superclass, path)) {
            return null;
        }
        return path;
    }

    private boolean subclassPathImpl(GenericClass subclass, String superclass, List<GenericClass> path) {
        path.add(subclass);
        if (subclass.getName().equals(superclass)) {
            return true;
        }
        GenericClass parent = getParent(subclass);
        if (parent != null) {
            if (subclassPathImpl(parent, superclass, path)) {
                return true;
            }
        }
        for (GenericClass iface : getInterfaces(subclass)) {
            if (subclassPathImpl(iface, superclass, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    public Set<GenericClass> commonSupertypes(Set<GenericClass> firstSet, Set<GenericClass> secondSet) {
        Set<GenericClass> firstAncestors = allAncestors(firstSet);
        Set<String> rawFirstAncestors = new HashSet<>();
        for (GenericClass cls : firstAncestors) {
            rawFirstAncestors.add(cls.getName());
        }
        Set<GenericClass> commonSupertypes = new HashSet<>();
        for (GenericClass cls : secondSet) {
            commonSupertypesImpl(cls, firstAncestors, rawFirstAncestors, new HashSet<GenericClass>(), commonSupertypes);
        }
        return commonSupertypes;
    }

    private void commonSupertypesImpl(GenericClass cls, Set<GenericClass> ancestors, Set<String> rawAncestors,
            Set<GenericClass> visited, Set<GenericClass> commonSupertypes) {
        if (!visited.add(cls)) {
            return;
        }
        if (ancestors.contains(cls)) {
            commonSupertypes.add(cls);
            return;
        }
        GenericClass parent = getParent(cls);
        if (parent != null) {
            commonSupertypesImpl(parent, ancestors, rawAncestors, visited, commonSupertypes);
        }
        for (GenericClass iface : getInterfaces(cls)) {
            commonSupertypesImpl(iface, ancestors, rawAncestors, visited, commonSupertypes);
        }
    }

    public Set<GenericClass> allAncestors(Collection<GenericClass> classes) {
        Set<GenericClass> ancestors = new HashSet<>();
        for (GenericClass cls : classes) {
            allAncestorsImpl(cls, ancestors);
        }
        return ancestors;
    }

    private void allAncestorsImpl(GenericClass cls, Set<GenericClass> ancestors) {
        if (!ancestors.add(cls)) {
            return;
        }
        GenericClass parent = getParent(cls);
        if (parent != null) {
            allAncestorsImpl(parent, ancestors);
        }
        for (GenericClass iface : getInterfaces(cls)) {
            allAncestorsImpl(iface, ancestors);
        }
    }

    public GenericClass getGenericClass(String className) {
        ClassDescriber describer = classRepository.describe(className);
        if (describer == null) {
            return null;
        }
        List<GenericType> arguments = new ArrayList<>();
        for (TypeVar var : describer.getTypeVariables()) {
            arguments.add(new GenericReference(var));
        }
        return new GenericClass(className, arguments);
    }

    public GenericClass getParent(GenericClass cls) {
        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return null;
        }
        GenericClass superType = describer.getSupertype();
        if (superType == null) {
            return null;
        }

        TypeVar[] typeVars = describer.getTypeVariables();
        List<GenericType> typeValues = cls.getArguments();
        if (typeVars.length != typeValues.size()) {
            return null;
        }
        Map<TypeVar, GenericType> substitutions = new HashMap<>();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutions.put(typeVars[i], typeValues.get(i));
        }

        return superType.substitute(substitutions);
    }

    public GenericClass[] getInterfaces(GenericClass cls) {
        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return null;
        }

        TypeVar[] typeVars = describer.getTypeVariables();
        List<GenericType> typeValues = cls.getArguments();
        if (typeVars.length != typeValues.size()) {
            return null;
        }
        Map<TypeVar, GenericType> substitutions = new HashMap<>();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutions.put(typeVars[i], typeValues.get(i));
        }

        GenericClass[] interfaces = describer.getInterfaces();
        GenericClass[] result = new GenericClass[interfaces.length];
        for (int i = 0; i < interfaces.length; ++i) {
            result[i] = interfaces[i].substitute(substitutions);
        }
        return result;
    }

    public GenericMethod[] findMethods(GenericClass cls, String name, int paramCount) {
        Map<MethodSignature, GenericMethod> methods = new HashMap<>();
        findMethodsImpl(cls, name, paramCount, new HashSet<String>(), methods);
        return methods.values().toArray(new GenericMethod[0]);
    }

    private Map<TypeVar, GenericType> prepareSubstitutions(ClassDescriber describer, GenericClass cls) {
        TypeVar[] typeVars = describer.getTypeVariables();
        List<GenericType> typeValues = cls.getArguments();
        if (typeVars.length != typeValues.size()) {
            return null;
        }
        Map<TypeVar, GenericType> substitutions = new HashMap<>();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutions.put(typeVars[i], typeValues.get(i));
        }
        return substitutions;
    }

    public GenericField getField(GenericClass cls, String name) {
        return getFieldRec(cls, name, new HashSet<GenericClass>());
    }

    private GenericField getFieldRec(GenericClass cls, String name, Set<GenericClass> visited) {
        if (!visited.add(cls)) {
            return null;
        }

        GenericField field = getFieldImpl(cls, name);
        if (field != null) {
            return field;
        }

        GenericClass parent = getParent(cls);
        if (parent != null) {
            field = getFieldRec(parent, name, visited);
            if (field != null) {
                return field;
            }
        }

        for (GenericClass iface : getInterfaces(cls)) {
            field = getFieldRec(iface, name, visited);
            if (field != null) {
                return field;
            }
        }

        return field;
    }

    private GenericField getFieldImpl(GenericClass cls, String name) {
        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return null;
        }

        Map<TypeVar, GenericType> substitutions = prepareSubstitutions(describer, cls);
        if (substitutions == null) {
            return null;
        }

        FieldDescriber fieldDescriber = describer.getField(name);
        if (fieldDescriber == null) {
            return null;
        }

        ValueType type = fieldDescriber.getType();
        if (type instanceof GenericType) {
            type = ((GenericType)type).substitute(substitutions);
        }

        return new GenericField(fieldDescriber, type);
    }

    public GenericMethod getMethod(GenericClass cls, String name, GenericClass... argumentTypes) {
        return getMethodRec(cls, name, argumentTypes, new HashSet<GenericClass>());
    }

    private GenericMethod getMethodRec(GenericClass cls, String name, GenericClass[] argumentTypes,
            Set<GenericClass> visited) {
        if (!visited.add(cls)) {
            return null;
        }

        GenericMethod method = getMethodImpl(cls, name, argumentTypes);
        if (method != null) {
            return method;
        }

        GenericClass parent = getParent(cls);
        if (parent != null) {
            method = getMethodRec(parent, name, argumentTypes, visited);
            if (method != null) {
                return method;
            }
        }

        for (GenericClass iface : getInterfaces(cls)) {
            method = getMethodRec(iface, name, argumentTypes, visited);
            if (method != null) {
                return method;
            }
        }

        return method;
    }

    private GenericMethod getMethodImpl(GenericClass cls, String name, GenericClass... argumentTypes) {
        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return null;
        }

        Map<TypeVar, GenericType> substitutions = prepareSubstitutions(describer, cls);
        if (substitutions == null) {
            return null;
        }

        MethodDescriber methodDescriber = describer.getMethod(name, argumentTypes);
        if (methodDescriber == null) {
            return null;
        }
        ValueType[] argTypes = methodDescriber.getArgumentTypes();
        for (int i = 0; i < argTypes.length; ++i) {
            if (argTypes[i] instanceof GenericType) {
                argTypes[i] = ((GenericType)argTypes[i]).substitute(substitutions);
            }
        }
        ValueType returnType = methodDescriber.getReturnType();
        if (returnType instanceof GenericType) {
            returnType = ((GenericType)returnType).substitute(substitutions);
        }

        return new GenericMethod(methodDescriber, cls, argumentTypes, returnType);
    }

    private void findMethodsImpl(GenericClass cls, String name, int paramCount, Set<String> visitedClasses,
            Map<MethodSignature, GenericMethod> methods) {
        if (!visitedClasses.add(cls.getName())) {
            return;
        }

        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return;
        }

        Map<TypeVar, GenericType> substitutions = prepareSubstitutions(describer, cls);
        if (substitutions == null) {
            return;
        }

        for (MethodDescriber methodDesc : describer.getMethods()) {
            if (!methodDesc.getName().equals(name)) {
                continue;
            }

            ValueType[] paramTypes = methodDesc.getArgumentTypes();
            if (paramTypes.length != paramCount) {
                continue;
            }
            for (int i = 0; i < paramTypes.length; ++i) {
                if (paramTypes[i] instanceof GenericType) {
                    paramTypes[i] = ((GenericType)paramTypes[i]).substitute(substitutions);
                }
            }

            ValueType returnType = methodDesc.getReturnType();
            if (returnType instanceof GenericType) {
                returnType = ((GenericType)returnType).substitute(substitutions);
            }

            MethodSignature signature = new MethodSignature(methodDesc.getRawArgumentTypes());
            methods.put(signature, new GenericMethod(methodDesc, cls, paramTypes, returnType));
        }

        GenericClass supertype = getParent(cls);
        if (supertype != null) {
            findMethodsImpl(supertype, name, paramCount, visitedClasses, methods);
        }
        for (GenericClass iface : getInterfaces(cls)) {
            findMethodsImpl(iface, name, paramCount, visitedClasses, methods);
        }
    }

    public GenericMethod isSingleAbstractMethod(GenericClass cls) {
        Map<MethodSignature, GenericMethod> methods = new HashMap<>();
        int count = isSingleAbstractMethodImpl(cls, new HashSet<String>(), methods);
        if (count != 1) {
            return null;
        }
        for (GenericMethod method : methods.values()) {
            if (method.getDescriber().isAbstract()) {
                return method;
            }
        }
        return null;
    }

    private int isSingleAbstractMethodImpl(GenericClass cls, Set<String> visitedClasses,
            Map<MethodSignature, GenericMethod> methods) {
        if (!visitedClasses.add(cls.getName())) {
            return 0;
        }

        ClassDescriber describer = classRepository.describe(cls.getName());
        if (describer == null) {
            return 0;
        }

        Map<TypeVar, GenericType> substitutions = prepareSubstitutions(describer, cls);
        if (substitutions == null) {
            return 0;
        }

        int result = 0;
        for (MethodDescriber methodDesc : describer.getMethods()) {
            ValueType[] paramTypes = methodDesc.getArgumentTypes();
            for (int i = 0; i < paramTypes.length; ++i) {
                if (paramTypes[i] instanceof GenericType) {
                    paramTypes[i] = ((GenericType)paramTypes[i]).substitute(substitutions);
                }
            }

            ValueType returnType = methodDesc.getReturnType();
            if (returnType instanceof GenericType) {
                returnType = ((GenericType)returnType).substitute(substitutions);
            }

            MethodSignature signature = new MethodSignature(methodDesc.getRawArgumentTypes());
            if (!methods.containsKey(signature)) {
                methods.put(signature, new GenericMethod(methodDesc, cls, paramTypes, returnType));
                if (methodDesc.isAbstract()) {
                    ++result;
                    if (result > 1) {
                        break;
                    }
                }
            }
        }

        GenericClass supertype = getParent(cls);
        if (supertype != null && result <= 1) {
            result += isSingleAbstractMethodImpl(supertype, visitedClasses, methods);
        }
        for (GenericClass iface : getInterfaces(cls)) {
            if (result > 1) {
                break;
            }
            result += isSingleAbstractMethodImpl(iface, visitedClasses, methods);
        }

        return result;
    }

    static class MethodSignature {
        ValueType[] paramTypes;

        public MethodSignature(ValueType[] paramTypes) {
            this.paramTypes = paramTypes;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(paramTypes);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof MethodSignature)) {
                return false;
            }
            MethodSignature other = (MethodSignature)obj;
            return Arrays.equals(paramTypes, other.paramTypes);
        }
    }
}
