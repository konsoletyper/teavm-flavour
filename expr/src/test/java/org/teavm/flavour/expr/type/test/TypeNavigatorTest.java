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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import java.io.Serializable;
import java.util.*;
import org.junit.Test;
import org.teavm.flavour.expr.type.*;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;

public class TypeNavigatorTest {
    private ClassPathClassDescriberRepository classRepository = new ClassPathClassDescriberRepository();
    private GenericTypeNavigator navigator = new GenericTypeNavigator(classRepository);

    @Test
    public void getsGenericClass() {
        GenericClass cls = navigator.getGenericClass(List.class.getName());
        assertThat(cls.getName(), is(List.class.getName()));
        assertThat(cls.getArguments().size(), is(1));
        assertThat(cls.getArguments().get(0), is(instanceOf(GenericReference.class)));
    }

    @Test
    public void getsParent() {
        GenericClass parent = navigator.getParent(cls(A.class, cls(String.class)));
        assertThat(parent, is(cls(ArrayList.class, cls(Set.class, cls(String.class)))));
    }

    @Test
    public void getsSubclassPath() {
        GenericClass cls = cls(A.class, cls(String.class));
        List<GenericClass> path = navigator.sublassPath(cls, Iterable.class.getName());
        assertThat(path.size(), greaterThan(2));
        assertThat(path.get(0), is(cls));
        assertThat(path.get(path.size() - 1), is(cls(Iterable.class, cls(Set.class, cls(String.class)))));
    }

    @Test
    public void reportsNoSubclassPath() {
        GenericClass cls = cls(A.class, cls(String.class));
        List<GenericClass> path = navigator.sublassPath(cls, String.class.getName());
        assertThat(path, is(nullValue()));
    }

    @Test
    public void findsTrivialCommonSuperTypes() {
        Set<String> commonSupertypes = navigator.commonSupertypes(Collections.singleton(A.class.getName()),
                Collections.singleton(String.class.getName()));
        assertThat(commonSupertypes, hasItems(Object.class.getName(), Serializable.class.getName()));
    }

    private GenericClass cls(Class<?> javaClass, GenericType... args) {
        return new GenericClass(javaClass.getName(), Arrays.asList(args));
    }

    class A<X> extends ArrayList<Set<X>> implements Collection<Set<X>> {
        private static final long serialVersionUID = 1L;
    }
}
