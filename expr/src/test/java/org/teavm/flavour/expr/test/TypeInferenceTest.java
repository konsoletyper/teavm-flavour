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
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
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

    static GenericType cls(Class<?> cls, GenericType... args) {
        return new GenericClass(cls.getName(), args);
    }

    static GenericType in(GenericClass cls) {
        TypeVar var = new TypeVar();
        var.withLowerBound(cls);
        return new GenericReference(var);
    }

    static GenericType out(GenericClass cls) {
        TypeVar var = new TypeVar();
        var.withLowerBound(cls);
        return new GenericReference(var);
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
