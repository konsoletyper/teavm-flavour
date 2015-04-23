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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class GenericTypeNavigator {
    private ClassDescriberRepository classRepository;

    public GenericTypeNavigator(ClassDescriberRepository classRepository) {
        this.classRepository = classRepository;
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
}
