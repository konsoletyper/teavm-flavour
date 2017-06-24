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
import java.util.ArrayList;
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
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.PrimitiveArray;
import org.teavm.flavour.expr.type.TypeArgument;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.ValueTypeFormatter;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;

public class TypeInferenceTest {
    private ClassDescriberRepository types = new ClassPathClassDescriberRepository();
    private TypeInference inf = new TypeInference(new GenericTypeNavigator(types));

    @Test
    public void infersCommonSupertype() {
        TypeVar t = new TypeVar("T");
        GenericType pattern = ref(t);

        addVariable(t);

        start();
        subtypeConstraint(cls(Class.class, inv(cls(Integer.class))), pattern);
        subtypeConstraint(cls(Class.class, inv(cls(Long.class))), pattern);
        infer();

        assertEquals("Class<? extends Comparable<?> & Number>", string(pattern));
    }

    @Test
    public void infersExaсtVariable() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, inv(ref(k)), inv(ref(v)));
        GenericType second = ref(k);

        addVariable(k);
        addVariable(v);

        start();
        subtypeConstraint(cls(HashMap.class, inv(cls(Number.class)), inv(cls(String.class))), first);
        subtypeConstraint(cls(Integer.class), second);
        infer();

        assertEquals("String", string(ref(v)));
        assertEquals("Number", string(ref(k)));
    }

    @Test
    public void infersExaсtVariableReverse() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, inv(ref(k)), inv(ref(v)));
        GenericType second = ref(k);

        addVariable(k);
        addVariable(v);

        start();
        subtypeConstraint(cls(Integer.class), second);
        subtypeConstraint(cls(HashMap.class, inv(cls(Number.class)), inv(cls(String.class))), first);
        infer();

        assertEquals("String", string(ref(v)));
        assertEquals("Number", string(ref(k)));
    }

    @Test
    public void exactVariableInferenceFailsOnContradiction() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");

        addVariable(k);
        addVariable(v);

        start();
        subtypeConstraint(cls(HashMap.class, inv(cls(Long.class)), inv(cls(String.class))),
                cls(Map.class, inv(ref(k)), inv(ref(v))));
        assertFalse(inf.subtypeConstraint(ref(k), cls(Integer.class)));
    }

    @Test
    public void checksEqualClasses() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        GenericType first = cls(Map.class, inv(ref(k)), inv(cls(Integer.class)));
        GenericType second = cls(Map.class, inv(cls(String.class)), inv(ref(v)));
        GenericType actual = cls(Map.class, inv(cls(String.class)), inv(cls(Integer.class)));

        addVariable(k);
        addVariable(v);

        start();
        subtypeConstraint(actual, first);
        subtypeConstraint(actual, second);
        infer();

        assertEquals("String", string(ref(k)));
        assertEquals("Integer", string(ref(v)));
    }

    @Test
    public void respectsDependencies() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        k.withUpperBound(ref(v));
        GenericType formal = cls(Map.class, inv(ref(k)), inv(ref(v)));
        GenericType actual = cls(Map.class, inv(cls(Integer.class)), inv(cls(Number.class)));

        addVariable(k);
        addVariable(v);

        start();
        subtypeConstraint(actual, formal);
        infer();

        assertEquals("Integer", string(ref(k)));
        assertEquals("Number", string(ref(v)));
    }

    @Test
    public void respectsLowerDependencies() {
        TypeVar k = new TypeVar("K");
        TypeVar v = new TypeVar("V");
        k.withUpperBound(ref(v));
        GenericType formal = cls(Map.class, inv(ref(k)), out(ref(v)));
        GenericType actual = cls(HashMap.class, inv(cls(Integer.class)), inv(cls(Object.class)));

        addVariable(k);
        addVariable(v);

        start();
        subtypeConstraint(actual, formal);
        infer();

        assertEquals("Integer", string(ref(k)));
        assertEquals("Object", string(ref(v)));
    }

    @Test
    public void arrayCommonItem() {
        TypeVar t = new TypeVar("T");
        GenericType pattern = ref(t);

        addVariable(t);

        start();
        subtypeConstraint(array(cls(Integer.class)), pattern);
        subtypeConstraint(array(cls(Long.class)), pattern);
        infer();

        assertEquals("Comparable<? extends Comparable<?> & Number> & Number[]", string(pattern));
    }

    @Test
    public void addsLowerBoundsFromTypeVarToInferenceVar() {
        TypeVar t = new TypeVar("T");
        t.withLowerBound(cls(List.class, inv(cls(A.class))));

        addVariable(t);
        subtypeConstraint(cls(List.class, inv(cls(B.class))), ref(t));
        infer();

        assertEquals("List<? extends BaseClass>", string(ref(t)));
    }

    @Test
    public void circularDependency() {
        TypeVar a = new TypeVar("A");
        TypeVar b = new TypeVar("B");
        a.withLowerBound(ref(b));

        addVariable(a);
        addVariable(b);

        start();
        subtypeConstraint(ref(a), ref(b));
        infer();

        assertEquals("Object", string(ref(a)));
        assertEquals("Object", string(ref(b)));
    }

    @Test
    public void inconsistentCircularDependency() {
        TypeVar a = new TypeVar("A");
        TypeVar b = new TypeVar("B");
        a.withLowerBound(ref(b));

        addVariable(a);
        addVariable(b);

        start();
        subtypeConstraint(ref(a), ref(b));
        subtypeConstraint(cls(Integer.class), ref(a));
        assertFalse("Should not resolve constraint set", inf.resolve());
    }

    @Test
    public void equalConstraintBetweenInferenceVars() {
        TypeVar a = new TypeVar("A");
        TypeVar b = new TypeVar("B");

        addVariable(a);
        addVariable(b);

        start();
        subtypeConstraint(cls(ArrayList.class, inv(ref(a))), cls(Iterable.class, inv(ref(b))));
        subtypeConstraint(cls(Integer.class), ref(a));
        infer();

        assertEquals("Integer", string(ref(a)));
        assertEquals("Integer", string(ref(b)));
    }

    @Test
    public void equalConstraintBetweenInferenceVars2() {
        TypeVar a = new TypeVar("A");
        TypeVar b = new TypeVar("B");

        addVariable(a);
        addVariable(b);

        start();
        subtypeConstraint(cls(Integer.class), ref(a));
        subtypeConstraint(cls(ArrayList.class, inv(ref(a))), cls(Iterable.class, inv(ref(b))));
        infer();

        assertEquals("Integer", string(ref(a)));
        assertEquals("Integer", string(ref(b)));
    }

    @Test
    public void captureConversion() {
        TypeVar t = types.describe(List.class.getName()).getTypeVariables()[0];

        start();
        // for example, List<T> get(), with receiver = List<? extends Integer>()
        captureConversionConstraint(Arrays.asList(t), Arrays.asList(out(cls(Integer.class)))).get(0);
        infer();

        assertEquals("Integer", string(ref(t)));
    }

    private void addVariable(TypeVar typeVar) {
        inf.addVariable(typeVar);
    }

    private void subtypeConstraint(GenericType subtype, GenericType supertype) {
        System.out.println(subtype + " <: " + supertype);
        if (!inf.subtypeConstraint(subtype, supertype)) {
            throw new AssertionError("Could not add subtype constraint: " + subtype + " <: " + supertype);
        }
    }

    private List<? extends TypeVar> captureConversionConstraint(List<TypeVar> typeParameters,
            List<TypeArgument> typeArguments) {
        List<? extends TypeVar> result = inf.captureConversionConstraint(typeParameters, typeArguments);
        if (result == null) {
            throw new AssertionError("Could not add capture conversion constraint");
        }
        return result;
    }

    private void start() {
        if (!inf.start()) {
            throw new AssertionError("Could not build initial constraint set");
        }
    }

    private void infer() {
        if (!inf.resolve()) {
            throw new AssertionError("Could not resolve constraint set");
        }
    }

    static GenericType cls(Class<?> cls, TypeArgument... args) {
        return new GenericClass(cls.getName(), args);
    }

    static GenericType array(ValueType item) {
        return item instanceof Primitive
                ? new PrimitiveArray((Primitive) item)
                : new GenericArray((GenericType) item);
    }

    static TypeArgument inv(GenericType cls) {
        return TypeArgument.invariant(cls);
    }

    static TypeArgument in(GenericType cls) {
        return TypeArgument.contravariant(cls);
    }

    static TypeArgument out(GenericType cls) {
        return TypeArgument.covariant(cls);
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
