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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

        ObjectNode objectNode = (ObjectNode)node;
        assertTrue("Property `a' exists", objectNode.has("a"));
        JsonNode aNode = objectNode.get("a");
        assertTrue("Property `a' is string", aNode.isTextual());
        assertEquals("foo", aNode.asText());

        assertTrue("Property `b' exists", objectNode.has("b"));
        JsonNode bNode = objectNode.get("b");
        assertTrue("Property `b' is number", bNode.isNumber());
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

        ObjectNode objectNode = (ObjectNode)node;
        assertTrue("Property `foo' should exist", objectNode.has("foo"));

        JsonNode fooNode = objectNode.get("foo");
        assertTrue("Property `foo' must be an object", fooNode.isObject());

        ObjectNode fooObjectNode = (ObjectNode)fooNode;
        assertTrue("Property `foo.a` expected", fooObjectNode.has("a"));
        assertTrue("Property `foo.b` expected", fooObjectNode.has("b"));
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
        ArrayNode arrayNode = (ArrayNode)propertyNode;
        assertEquals("Length must be 2", 2, arrayNode.size());

        JsonNode firstNode = arrayNode.get(0);
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

        ObjectNode objectNode = (ObjectNode)node;
        assertTrue("Root node should contain `array' property", objectNode.has("array"));

        JsonNode propertyNode = objectNode.get("array");
        assertTrue("Property `array' should be JSON array", propertyNode.isArray());

        ArrayNode arrayNode = (ArrayNode)propertyNode;
        assertEquals("Length must be 1", 1, arrayNode.size());

        JsonNode firstNode = arrayNode.get(0);
        assertTrue("Item must be object", firstNode.isObject());

        ObjectNode itemObjectNode = (ObjectNode)firstNode;
        assertTrue(itemObjectNode.has("a"));
        assertTrue(itemObjectNode.has("b"));
    }

    @Test
    public void renamesProperty() {
        RenamedProperty o = new RenamedProperty();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue(node.has("foo_"));
        assertFalse(node.has("foo"));
    }

    @Test
    public void ignoresProperty() {
        IgnoredProperty o = new IgnoredProperty();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue(node.has("bar"));
        assertFalse(node.has("foo"));
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
}
