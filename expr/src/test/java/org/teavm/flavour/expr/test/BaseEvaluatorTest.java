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
package org.teavm.flavour.expr.test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.teavm.flavour.expr.VariableName;

/**
 *
 * @author Alexey Andreev
 */
public abstract class BaseEvaluatorTest {
    interface TestVars {
        void intValue(int v);

        void stringValue(String v);

        void doubleValue(double v);

        void byteValue(byte v);

        void longWrapper(Long v);

        void object(Object v);

        void intArray(int[] array);

        void integerArray(Integer[] array);

        void stringArray(String[] array);

        void integerList(List<Integer> list);

        void longList(List<Long> list);

        void stringList(List<String> list);

        void stringIntMap(Map<String, Integer> map);

        void foo(Foo v);

        @VariableName("this")
        void self(Foo v);
    }

    interface BooleanComputation {
        boolean compute();
    }

    interface ByteComputation {
        byte compute();
    }

    interface IntComputation {
        int compute();
    }

    interface LongComputation {
        long compute();
    }

    interface StringComputation {
        String compute();
    }

    interface ObjectComputation {
        Object compute();
    }

    interface StringMappingComputation {
        Mapping<String, String> compute();
    }

    public interface Mapping<S, T> {
        T apply(S value);
    }

    public interface Reduction<T> {
        T apply(T a, T b);
    }

    public static class Foo {
        public int y;

        public Foo(int y) {
            this.y = y;
        }

        public int bar(int x) {
            return x + y;
        }

        public String bar(String x) {
            return x + y;
        }

        public String bar(Date x) {
            return String.valueOf(y) + x.getTime();
        }

        public <K, V> V extract(Map<K, V> map, K key) {
            return map.get(key);
        }

        public static String staticMethod() {
            return "foo";
        }

        public static String getStaticProperty() {
            return "foo";
        }
    }
}
