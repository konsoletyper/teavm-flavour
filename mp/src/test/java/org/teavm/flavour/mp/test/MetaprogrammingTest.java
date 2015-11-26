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
package org.teavm.flavour.mp.test;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.Proxy;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Value;

/**
 *
 * @author Alexey Andreev
 */
public class MetaprogrammingTest {
    @Test
    public void works() {
        assertEquals("java.lang.Object".length(), classNameLength(new Object(), 2));
        assertEquals("java.lang.Integer".length(), classNameLength(5, 3));
    }

    @Proxy(ClassNameLengthProxy.class)
    static native int classNameLength(Object obj, int add);

    static class ClassNameLengthProxy {
        public void classNameLength(Emitter<Integer> em, ReflectValue<Object> obj, Value<Integer> add) {
            int length = obj.getClassName().length();
            em.returnValue(() -> length + add.get());
        }
    }
}
