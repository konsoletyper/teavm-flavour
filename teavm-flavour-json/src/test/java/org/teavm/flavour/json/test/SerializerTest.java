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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Test;

/**
 *
 * @author Alexey Andreev
 */
public class SerializerTest {
    @Test
    public void writesProperty() {
        A obj = new A();
        obj.setA("foo");
        obj.setB(23);
        JsonNode node = JSONRunner.serialize(obj);

        assertTrue("Root node shoud be JSON object", node.isObject());

        assertTrue("Property `a' exists", node.has("a"));
        JsonNode aNode = node.get("a");
        assertEquals("foo", aNode.asText());

        assertTrue("Property `b' exists", node.has("b"));
        JsonNode bNode = node.get("b");
        assertEquals(23, bNode.asInt());
    }

    @Test
    public void writesReference() {
        B obj = new B();
        A ref = new A();
        ref.setA("foo");
        ref.setB(23);
        obj.setFoo(ref);
        JsonNode node = JSONRunner.serialize(obj);

        assertTrue("Root node should be JSON object", node.isObject());

        assertTrue("Property `foo' should exist", node.has("foo"));
        JsonNode fooNode = node.get("foo");
        assertTrue("Property `foo' must be an object", fooNode.isObject());
        assertTrue("Property `foo.a` expected", fooNode.has("a"));
        assertTrue("Property `foo.b` expected", fooNode.has("b"));
    }

    @Test
    public void writesArray() {
        int[] array = { 23, 42 };
        JsonNode node = JSONRunner.serialize(array);

        assertTrue("Root node should be JSON array", node.isArray());

        ArrayNode arrayNode = (ArrayNode)node;
        assertEquals("Length must be 2", 2, arrayNode.size());

        JsonNode firstNode = arrayNode.get(0);
        assertTrue("Item must be numeric", firstNode.isNumber());
        assertEquals(23, firstNode.asInt());
    }

    @Test
    public void writesArrayProperty() {
        ArrayProperty o = new ArrayProperty();
        o.setArray(new int[] { 23, 42 });
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Root node should be JSON object", node.isObject());
        assertTrue("Root node should contain `array' property", node.has("array"));
        JsonNode propertyNode = node.get("array");
        assertTrue("Property `array' should be JSON array", propertyNode.isArray());
        assertEquals("Length must be 2", 2, propertyNode.size());

        JsonNode firstNode = propertyNode.get(0);
        assertTrue("Item must be numeric", firstNode.isNumber());
        assertEquals(23, firstNode.asInt());
    }

    @Test
    public void writesArrayOfObjectProperty() {
        A item = new A();
        ArrayOfObjectProperty o = new ArrayOfObjectProperty();
        o.setArray(new A[] { item });
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Root node should be JSON object", node.isObject());
        assertTrue("Root node should contain `array' property", node.has("array"));

        JsonNode propertyNode = node.get("array");
        assertTrue("Property `array' should be JSON array", propertyNode.isArray());
        assertEquals("Length must be 1", 1, propertyNode.size());

        JsonNode itemNode = propertyNode.get(0);
        assertTrue("Item must be object", itemNode.isObject());
        assertTrue(itemNode.has("a"));
        assertTrue(itemNode.has("b"));
    }

    @Test
    public void renamesProperty() {
        RenamedProperty o = new RenamedProperty();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `foo_' property", node.has("foo_"));
        assertFalse("Shouldn't have `foo' property", node.has("foo"));
    }

    @Test
    public void ignoresProperty() {
        IgnoredProperty o = new IgnoredProperty();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `bar' property", node.has("bar"));
        assertFalse("Shouldn't have `foo' property", node.has("foo"));
    }

    @Test
    public void getterHasPriorityOverField() {
        FieldAndGetter o = new FieldAndGetter();
        o.foo = 23;
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `foo' property", node.has("foo"));
        assertEquals(25, node.get("foo").asInt());
    }

    @Test
    public void fieldRenamesGetter() {
        NamedFieldAndGetter o = new NamedFieldAndGetter();
        o.foo = 23;
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `foo_' property", node.has("foo_"));
        assertEquals("23!", node.get("foo_").asText());
    }

    @Test
    public void emitsField() {
        FieldVisible o = new FieldVisible();
        o.foo = 23;
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `foo' property", node.has("foo"));
        assertEquals(23, node.get("foo").asInt());
    }

    public static class A {
        private String a;
        private int b;

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
        private Object foo;

        public Object getFoo() {
            return foo;
        }

        public void setFoo(Object foo) {
            this.foo = foo;
        }
    }

    public static class ArrayProperty {
        int[] array;

        public int[] getArray() {
            return array;
        }

        public void setArray(int[] array) {
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
        int foo;
        String bar;

        @JsonIgnore
        public int getFoo() {
            return foo;
        }

        public void setFoo(int foo) {
            this.foo = foo;
        }

        public String getBar() {
            return bar;
        }

        public void setBar(String bar) {
            this.bar = bar;
        }
    }

    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class FieldAndGetter {
        public int foo;

        public int getFoo() {
            return foo + 2;
        }
    }

    public static class NamedFieldAndGetter {
        @JsonProperty("foo_")
        public int foo;

        public String getFoo() {
            return foo + "!";
        }
    }

    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class FieldVisible {
        public int foo;
    }
}
