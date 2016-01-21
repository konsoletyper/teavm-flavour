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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeConstraints {
    private GenericTypeNavigator navigator;
    private Map<TypeVar, Variable> varCache = new WeakHashMap<>();

    public TypeConstraints(GenericTypeNavigator navigator) {
        this.navigator = navigator;
    }

    public boolean assertEqual(Type s, Type t) {
        if (s == t) {
            return true;
        }
        if (s instanceof Variable.Reference && t instanceof Variable.Reference) {
            Variable.Reference x = (Variable.Reference) s;
            Variable.Reference y = (Variable.Reference) t;
            return x.getVariable().assertEqualTo(y.getVariable());
        } else if (s instanceof Variable.Reference && t instanceof ClassType) {
            Variable.Reference x = (Variable.Reference) s;
            ClassType y = (ClassType) t;
            return x.getVariable().assertEqualTo(y);
        } else if (s instanceof ClassType && t instanceof Variable.Reference) {
            Variable.Reference x = (Variable.Reference) t;
            ClassType y = (ClassType) s;
            return x.getVariable().assertEqualTo(y);
        } else if (s instanceof ClassType && t instanceof ClassType) {
            ClassType x = (ClassType) s;
            ClassType y = (ClassType) t;
            if (x.getName() != y.getName() || x.getArguments().size() != y.getArguments().size()) {
                return false;
            }
            boolean ok = true;
            for (int i = 0; i < x.getArguments().size(); ++i) {
                ok &= assertEqual(x.getArguments().get(i), y.getArguments().get(i));
            }
            return ok;
        }
        return false;
    }

    public boolean assertSubtype(Type s, Type t) {
        if (s == t) {
            return true;
        }
        if (s instanceof Variable.Reference && t instanceof Variable.Reference) {
            Variable.Reference x = (Variable.Reference) s;
            Variable.Reference y = (Variable.Reference) t;
            return x.getVariable().assertSubtypeOf(y.getVariable());
        } else if (s instanceof Variable.Reference && t instanceof ClassType) {
            Variable.Reference x = (Variable.Reference) s;
            ClassType y = (ClassType) t;
            return x.getVariable().assertSubtypeOf(Arrays.asList(y));
        } else if (s instanceof ClassType && t instanceof Variable.Reference) {
            Variable.Reference x = (Variable.Reference) t;
            ClassType y = (ClassType) s;
            return x.getVariable().assertSupertypeOf(Arrays.asList(y));
        } else if (s instanceof ClassType && t instanceof ClassType) {
            ClassType x = (ClassType) s;
            ClassType y = (ClassType) t;
            List<ClassType> path = sublassPath(x, y);
            if (path == null) {
                return false;
            }
            x = path.get(path.size() - 1);
            boolean ok = true;
            for (int i = 0; i < x.getArguments().size(); ++i) {
                ok &= assertEqual(x.getArguments().get(i), y.getArguments().get(i));
            }
            return ok;
        }
        return false;
    }

    public boolean assertSubtype(Type s, List<ClassType> t) {
        if (t.isEmpty()) {
            return true;
        }
        if (t.size() == 1 && t.iterator().next() == s) {
            return true;
        }
        if (s instanceof Variable.Reference) {
            Variable.Reference v = (Variable.Reference) s;
            return v.getVariable().assertSubtypeOf(t);
        } else {
            ClassType cls = (ClassType) s;
            boolean ok = true;
            for (ClassType other : t) {
                List<ClassType> path = sublassPath(cls, other);
                if (path == null) {
                    ok = false;
                    continue;
                }
                other = path.get(path.size() - 1);
                for (int i = 0; i < other.getArguments().size(); ++i) {
                    ok &= assertEqual(other.getArguments().get(i), cls.getArguments().get(i));
                }
            }
            return ok;
        }
    }

    public boolean assertSubtype(List<ClassType> s, Type t) {
        if (s.isEmpty()) {
            return false;
        }
        if (s.size() == 1 && s.iterator().next() == t) {
            return true;
        }
        if (t instanceof Variable.Reference) {
            Variable.Reference v = (Variable.Reference) t;
            return v.getVariable().assertSubtypeOf(s);
        } else {
            ClassType cls = (ClassType) t;
            boolean ok = true;
            for (ClassType other : s) {
                List<ClassType> path = sublassPath(other, cls);
                if (path == null) {
                    ok = false;
                    continue;
                }
                other = path.get(path.size() - 1);
                for (int i = 0; i < other.getArguments().size(); ++i) {
                    ok &= assertEqual(other.getArguments().get(i), cls.getArguments().get(i));
                }
            }
            return ok;
        }
    }

    public Set<String> commonSupertypes(Set<String> first, Set<String> second) {
        if (first.isEmpty()) {
            return second;
        } else if (second.isEmpty()) {
            return first;
        } else if (first.size() == 1 && second.size() == 1) {
            String s = first.iterator().next();
            String t = second.iterator().next();
            if (s.equals(t)) {
                return Collections.singleton(s);
            } else if (isSubclass(t, s)) {
                return Collections.singleton(s);
            } else if (isSubclass(s, t)) {
                return Collections.singleton(t);
            }
        }
        Set<String> a = new HashSet<>();
        Set<String> b = new HashSet<>();
        for (String s : first) {
            frontier(s, new HashSet<>(), a, new HashSet<>());
        }
        for (String t : second) {
            frontier(t, a, new HashSet<String>(), b);
        }
        return b;
    }

    private void frontier(String s, Set<String> ancestors, Set<String> visited, Set<String> result) {
        if (!visited.add(s)) {
            return;
        }
        if (ancestors.contains(s)) {
            result.add(s);
            return;
        }

        ClassDescriberRepository repository = navigator.getClassRepository();
        ClassDescriber cls = repository.describe(s);
        if (cls.getSupertype() != null) {
            frontier(cls.getSupertype().getName(), ancestors, visited, result);
        }
        for (GenericClass iface : cls.getInterfaces()) {
            frontier(iface.getName(), ancestors, visited, result);
        }
    }

    public ClassType genericClass(String className) {
        if (className.startsWith("[")) {
            if (!className.endsWith("[")) {
                return new ClassType(className);
            } else {
                return new ClassType(className, createVariable().createReference());
            }
        } else {
            ClassDescriberRepository repository = navigator.getClassRepository();
            ClassDescriber cls = repository.describe(className);
            int argCount = cls.getTypeVariables().length;
            List<Type> args = new ArrayList<>();
            for (int i = 0; i < argCount; ++i) {
                args.add(createVariable().createReference());
            }
            return new ClassType(className, args);
        }
    }

    public boolean isSubclass(String subclass, String superclass) {
        if (subclass.startsWith("[")) {
            return superclass.equals("java.lang.Object");
        }
        ClassDescriberRepository repository = navigator.getClassRepository();
        return isSubclass(subclass, repository.describe(superclass));
    }

    private boolean isSubclass(String subclassName, ClassDescriber superclass) {
        if (superclass.getName().equals(subclassName)) {
            return true;
        }
        ClassDescriberRepository repository = navigator.getClassRepository();
        ClassDescriber subclass = repository.describe(subclassName);
        if (subclass.getSupertype() != null) {
            if (isSubclass(subclass.getSupertype().getName(), superclass)) {
                return true;
            }
        }
        for (GenericClass iface : subclass.getInterfaces()) {
            if (isSubclass(iface.getName(), superclass)) {
                return true;
            }
        }
        return false;
    }

    public List<ClassType> sublassPath(ClassType subclass, ClassType superclass) {
        if (subclass.getName().startsWith("[")) {
            return Arrays.asList(subclass, new ClassType("java.lang.Object"));
        }

        List<GenericClass> path = navigator.sublassPath((GenericClass) convert(subclass), superclass.getName());
        return path.stream().map(this::convert).map(t -> (ClassType) t).collect(Collectors.toList());
    }

    public Type convert(GenericType t) {
        if (t instanceof GenericClass) {
            GenericClass cls = (GenericClass) t;
            return new ClassType(cls.getName(), cls.getArguments().stream().map(this::convert)
                    .collect(Collectors.toList()));
        } else if (t instanceof GenericArray) {
            StringBuilder sb = new StringBuilder();
            ValueType s = t;
            while (t instanceof GenericArray) {
                sb.append('[');
                s = ((GenericArray) s).getElementType();
            }
            if (s instanceof GenericType) {
                return new ClassType(sb.toString(), convert((GenericType) s));
            } else {
                sb.append(convertPrimitive((Primitive) s));
                return new ClassType(sb.toString());
            }
        } else if (t instanceof GenericReference) {
            GenericReference ref = (GenericReference) t;
            return varCache.computeIfAbsent(ref.getVar(), v -> new Variable(this, v)).createReference();
        }
        throw new AssertionError();
    }

    public GenericType convert(Type t) {
        if (t instanceof ClassType) {
            ClassType cls = (ClassType) t;
            if (cls.getName().startsWith("[")) {
                if (cls.getName().endsWith("[")) {
                    return new GenericArray(convert(cls.getArguments().get(0)));
                } else {
                    return new GenericArray(convertPrimitive(cls.getName().charAt(cls.getName().length() - 1)));
                }
            } else {
                return new GenericClass(cls.getName(), cls.getArguments().stream().map(this::convert)
                        .collect(Collectors.toList()));
            }
        } else if (t instanceof Variable.Reference) {
            Variable v = ((Variable.Reference) t).getVariable();
            return new GenericReference(v.impl);
        }
        throw new AssertionError();
    }

    private String convertPrimitive(Primitive primitive) {
        switch (primitive.getKind()) {
            case BOOLEAN:
                return "Z";
            case BYTE:
                return "B";
            case SHORT:
                return "S";
            case CHAR:
                return "C";
            case INT:
                return "I";
            case LONG:
                return "J";
            case FLOAT:
                return "F";
            case DOUBLE:
                return "D";
        }
        throw new AssertionError();
    }

    private ValueType convertPrimitive(char c) {
        switch (c) {
            case 'Z':
                return Primitive.BOOLEAN;
            case 'B':
                return Primitive.BYTE;
            case 'S':
                return Primitive.SHORT;
            case 'C':
                return Primitive.CHAR;
            case 'I':
                return Primitive.INT;
            case 'J':
                return Primitive.LONG;
            case 'F':
                return Primitive.FLOAT;
            case 'D':
                return Primitive.DOUBLE;
        }
        throw new AssertionError();
    }

    public Variable createVariable() {
        TypeVar var = new TypeVar();
        Variable result = new Variable(this, var);
        varCache.put(var, result);
        return result;
    }
}
