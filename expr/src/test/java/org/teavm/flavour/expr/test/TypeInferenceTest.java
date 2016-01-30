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
package org.teavm.flavour.expr.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.GenericWildcard;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.ValueTypeFormatter;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeInferenceTest {
    private TypeInference inf;

    public TypeInferenceTest() {
        inf = new TypeInference(new GenericTypeNavigator(new ClassPathClassDescriberRepository()));
    }

    @Test
    public void infersCommonSupertype() {
        TypeVar t = new TypeVar("T");
        GenericType pattern = ref(t);

        boolean ok = true;
        ok &= inf.subtypeConstraint(cls(Class.class, cls(Integer.class)), pattern);
        ok &= inf.subtypeConstraint(cls(Class.class, cls(Long.class)), pattern);

        assertTrue(ok);
        assertEquals("Class<? extends Comparable<T> & Number>", string(pattern));
    }

    @Test
    public void infersExaсtVariable() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, ref(k), ref(v));
        GenericType second = ref(k);

        boolean ok = true;
        ok &= inf.subtypeConstraint(cls(HashMap.class, cls(Number.class), cls(String.class)), first);
        ok &= inf.subtypeConstraint(cls(Integer.class), second);

        assertTrue(ok);
        assertEquals("String", string(ref(v)));
        assertEquals("Number", string(ref(k)));
    }

    @Test
    public void infersExaсtVariableReverse() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, ref(k), ref(v));
        GenericType second = ref(k);

        boolean ok = true;
        ok &= inf.subtypeConstraint(cls(Integer.class), second);
        ok &= inf.subtypeConstraint(cls(HashMap.class, cls(Number.class), cls(String.class)), first);

        assertTrue(ok);
        assertEquals("String", string(ref(v)));
        assertEquals("Number", string(ref(k)));
    }

    @Test
    public void exactVariableInferenceFailsOnContradiction() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, ref(k), ref(v));
        GenericType second = ref(k);

        boolean ok = true;
        ok &= inf.subtypeConstraint(cls(HashMap.class, cls(Long.class), cls(String.class)), first);
        ok &= inf.subtypeConstraint(cls(Integer.class), second);

        assertFalse(ok);
    }

    @Test
    public void mergesVariables() {
        TypeVar t = new TypeVar("T");
        GenericType actual = cls(List.class, in(cls(Number.class)));
        GenericType formal = cls(List.class, ref(t));

        boolean ok = true;
        ok &= inf.subtypeConstraint(actual, formal);

        assertTrue(ok);
        assertEquals("Number", string(ref(t)));
    }

    @Test
    public void checksEqualClasses() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, ref(k), cls(Integer.class));
        GenericType second = cls(Map.class, cls(String.class), ref(v));
        GenericType actual = cls(Map.class, cls(String.class), cls(Integer.class));

        boolean ok = true;
        ok &= inf.subtypeConstraint(actual, first);
        ok &= inf.subtypeConstraint(actual, second);

        assertTrue(ok);
        assertEquals("String", string(ref(k)));
        assertEquals("Integer", string(ref(v)));
    }

    @Test
    public void respectsDependencies() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        k.withUpperBound(ref(v));
        GenericType formal = cls(Map.class, ref(k), ref(v));
        GenericType actual = cls(Map.class, cls(Integer.class), cls(Number.class));

        boolean ok = true;
        ok &= inf.subtypeConstraint(actual, formal);

        assertTrue(ok);
        assertEquals("Integer", string(ref(k)));
        assertEquals("Number", string(ref(v)));
    }

    @Test
    public void respectsLowerDependencies() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        k.withUpperBound(ref(v));
        GenericType formal = cls(Map.class, ref(k), out(ref(v)));
        GenericType actual = cls(HashMap.class, cls(Integer.class), cls(Object.class));

        boolean ok = true;
        ok &= inf.subtypeConstraint(actual, formal);

        assertTrue(ok);
        assertEquals("Integer", string(ref(k)));
        assertEquals("Integer", string(ref(v)));
    }

    @Test
    public void findsLowerDependenciesViolations() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        k.withUpperBound(ref(v));
        GenericType formal = cls(Map.class, ref(k), out(ref(v)));
        GenericType actual = cls(HashMap.class, cls(Number.class), cls(Integer.class));

        boolean ok = true;
        ok &= inf.subtypeConstraint(actual, formal);

        assertFalse(ok);
    }

    @Test
    public void arrayCommonItem() {
        TypeVar t = new TypeVar("T");
        GenericType pattern = ref(t);

        boolean ok = true;
        ok &= inf.subtypeConstraint(array(cls(Integer.class)), pattern);
        ok &= inf.subtypeConstraint(array(cls(Long.class)), pattern);

        assertTrue(ok);
        assertEquals("? extends Comparable<T> & Number[]", string(pattern));
    }

    @Test
    public void primitiveArrayCommonItem() {
        TypeVar t = new TypeVar("T");
        GenericType pattern = ref(t);

        boolean ok = true;
        ok &= inf.subtypeConstraint(array(cls(Integer.class)), pattern);
        ok &= inf.subtypeConstraint(array(Primitive.LONG), pattern);

        assertTrue(ok);
        assertEquals("? extends Object", string(pattern));
    }

    private static native <T> T f(T a, T b);

    static GenericType cls(Class<?> cls, GenericType... args) {
        return new GenericClass(cls.getName(), args);
    }

    static GenericType array(ValueType item) {
        return new GenericArray(item);
    }

    static GenericType in(GenericType cls) {
        return GenericWildcard.lowerBounded(Arrays.asList(cls));
    }

    static GenericType out(GenericType cls) {
        return GenericWildcard.upperBounded(Arrays.asList(cls));
    }

    static GenericType ref(TypeVar var) {
        return new GenericReference(var);
    }

    private String string(GenericType type) {
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        formatter.setUsingShortClassNames(true);
        formatter.setUsingWildcardChars(true);
        return formatter.format(type.substitute(inf.getSubstitutions()));
    }
}
