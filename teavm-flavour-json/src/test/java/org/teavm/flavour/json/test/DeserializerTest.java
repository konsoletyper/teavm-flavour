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
package org.teavm.flavour.json.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import org.junit.Test;

/**
 *
 * @author Alexey Andreev
 */
public class DeserializerTest {
    @Test
    public void readsProperty() {
        A obj = JSONRunner.deserialize("{ \"a\" : \"foo\", \"b\" : 23 }", A.class);
        assertEquals("foo", obj.getA());
        assertEquals(23, obj.getB());
    }

    @Test
    public void readsReference() {
        B obj = JSONRunner.deserialize("{ \"foo\" : { \"a\" : \"foo\", \"b\" : 23 } }", B.class);
        assertEquals("foo", obj.getFoo().getA());
        assertEquals(23, obj.getFoo().getB());
    }

    @Test
    public void readsArray() {
        int[] array = JSONRunner.deserialize("[ 23, 42 ]", int[].class);
        assertEquals(2, array.length);
        assertEquals(23, array[0]);
        assertEquals(42, array[1]);
    }

    @Test
    public void readsArrayProperty() {
        int[] array = JSONRunner.deserialize("{ \"array\" : [ 23, 42 ] }", ArrayProperty.class).array;
        assertEquals(2, array.length);
        assertEquals(23, array[0]);
        assertEquals(42, array[1]);
    }

    @Test
    public void readsObjectArrayProperty() {
        A[] array = JSONRunner.deserialize("{ \"array\" : [ { \"a\" : \"foo\", \"b\" : 23 } ] }",
                ArrayOfObjectProperty.class).array;
        assertEquals(1, array.length);
        assertEquals("foo", array[0].a);
        assertEquals(23, array[0].b);
    }

    @Test
    public void readsRenamedProperty() {
        RenamedProperty obj = JSONRunner.deserialize("{ \"foo_\" : 23 }", RenamedProperty.class);
        assertEquals(23, obj.getFoo());
    }

    @Test
    public void readsIgnoredProperty() {
        IgnoredProperty obj = JSONRunner.deserialize("{ \"foo\" : 1, \"bar\" : \"qwe\" }", IgnoredProperty.class);
        assertEquals(2, obj.foo);
        assertEquals("qwe", obj.bar);
    }

    @Test
    public void setterHasPriorityOverField() {
        FieldAndSetter obj = JSONRunner.deserialize("{ \"foo\" : 3 }", FieldAndSetter.class);
        assertEquals(4, obj.foo);
    }

    @Test
    public void setsViaField() {
        FieldVisible obj = JSONRunner.deserialize("{ \"foo\" : 3 }", FieldVisible.class);
        assertEquals(3, obj.foo);
    }

    @Test
    public void readsBuiltInTypes() {
        BuiltInTypes obj = JSONRunner.deserialize("{ " +
                "\"boolField\" : true," +
                "\"byteField\" : 1," +
                "\"charField\" : \"a\"," +
                "\"shortField\" : 2," +
                "\"intField\" : 3," +
                "\"longField\" : 4," +
                "\"floatField\" : 5.1," +
                "\"doubleField\" : 6.1," +
                "\"list\" : [ { \"a\" : \"q\", \"b\" : 7 } ]," +
                "\"set\" : [ { \"a\" : \"w\", \"b\" : 8 } ]," +
                "\"map\" : { \"e\" : { \"a\" : \"r\", \"b\" : 8 } }," +
                "\"visibility\" : \"ANY\" }",
                BuiltInTypes.class);
        assertTrue(obj.boolField);
        assertEquals('a', (char)obj.charField);
        assertEquals(2, (short)obj.shortField);
        assertEquals(3, (int)obj.intField);
        assertEquals(4, (long)obj.longField);
        assertEquals(5.1, obj.floatField, 0.01);
        assertEquals(6.1, obj.doubleField, 0.01);
        assertEquals(1, obj.list.size());
        assertEquals("q", obj.list.get(0).a);
        assertEquals(1, obj.set.size());
        assertEquals("w", obj.set.iterator().next().a);
        assertEquals(1, obj.map.size());
        assertTrue(obj.map.containsKey("e"));
        assertEquals("r", obj.map.get("e").a);
        assertEquals(Visibility.ANY, obj.visibility);
    }

    public static class A {
        String a;
        int b;

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public int getB() {
            return b;
        }

        public void setB(int b) {
            this.b = b;
        }
    }

    public static class B {
        private A foo;

        public A getFoo() {
            return foo;
        }

        public void setFoo(A foo) {
            this.foo = foo;
        }
    }

    public static class ArrayProperty {
        int[] array;

        void setArray(int[] array) {
            this.array = array;
        }
    }

    public static class ArrayOfObjectProperty {
        A[] array;

        public A[] getArray() {
            return array;
        }

        public void setArray(A[] array) {
            this.array = array;
        }
    }

    public static class RenamedProperty {
        int foo;

        @JsonProperty("foo_")
        public int getFoo() {
            return foo;
        }

        public void setFoo(int foo) {
            this.foo = foo;
        }
    }

    public static class IgnoredProperty {
        int foo = 2;
        String bar;

        public int getFoo() {
            return foo;
        }

        @JsonIgnore
        void setFoo(int foo) {
            this.foo = foo;
        }

        void setBar(String bar) {
            this.bar = bar;
        }
    }

    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class FieldAndSetter {
        public int foo;

        public void setFoo(int foo) {
            this.foo = foo + 1;
        }
    }

    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class FieldVisible {
        public int foo;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class BuiltInTypes {
        public Boolean boolField;
        public Byte byteField;
        public Character charField;
        public Short shortField;
        public Integer intField;
        public Long longField;
        public Float floatField;
        public Double doubleField;
        public List<A> list;
        public Set<A> set;
        public Map<String, A> map;
        public Visibility visibility;
    }
}
