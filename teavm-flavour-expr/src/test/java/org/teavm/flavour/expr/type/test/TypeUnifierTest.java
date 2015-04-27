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
package org.teavm.flavour.expr.type.test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;
import org.teavm.flavour.expr.type.*;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeUnifierTest {
    private TypeUnifier unifier = new TypeUnifier(new ClassPathClassDescriberRepository());

    @Test
    public void unifiesSameTypes() {
        GenericClass cls = cls(String.class);
        assertThat(unifier.unify(cls, cls, false), is(true));
    }

    @Test
    public void substitutesVariable() {
        TypeVar var = new TypeVar();
        GenericClass cls = cls(A.class, cls(String.class));
        GenericClass pattern = cls(Iterable.class, ref(var));
        assertThat(unifier.unify(pattern, cls, true), is(true));
        assertThat(unifier.getSubstitutions().get(var), is((GenericType)cls(Set.class, cls(String.class))));
    }

    @Test
    public void rejectsIncompatibleArgument() {
        GenericClass cls = cls(A.class, cls(String.class));
        GenericClass pattern = cls(Iterable.class, cls(Long.class));
        assertThat(unifier.unify(pattern, cls, true), is(false));
    }

    private GenericClass cls(Class<?> javaClass, GenericType... args) {
        return new GenericClass(javaClass.getName(), Arrays.asList(args));
    }

    private GenericReference ref(TypeVar var) {
        return new GenericReference(var);
    }

    class A<X> extends ArrayList<Set<X>> implements Collection<Set<X>> {
        private static final long serialVersionUID = 1L;
    }
}
