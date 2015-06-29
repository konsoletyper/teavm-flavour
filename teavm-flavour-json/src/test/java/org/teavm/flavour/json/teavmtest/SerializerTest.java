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
package org.teavm.flavour.json.teavmtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NumberNode;
import org.teavm.flavour.json.tree.ObjectNode;
import org.teavm.flavour.json.tree.StringNode;

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
        Node node = JSON.serialize(obj);

        assertTrue("Root node shoud be JSON object", node.isObject());

        ObjectNode objectNode = (ObjectNode)node;
        assertTrue("Property `a' exists", objectNode.has("a"));
        Node aNode = objectNode.get("a");
        assertTrue("Property `a' is string", aNode.isString());
        assertEquals("foo", ((StringNode)aNode).getValue());

        assertTrue("Property `b' exists", objectNode.has("b"));
        Node bNode = objectNode.get("b");
        assertTrue("Property `b' is number", bNode.isNumber());
        assertEquals(23, ((NumberNode)bNode).getIntValue());
    }

    @Test
    public void writesReference() {
        B obj = new B();
        A ref = new A();
        ref.setA("foo");
        ref.setB(23);
        obj.setFoo(ref);
        Node node = JSON.serialize(obj);

        assertTrue("Root node should be JSON object", node.isObject());

        ObjectNode objectNode = (ObjectNode)node;
        assertTrue("Property `foo' exists", objectNode.has("foo"));

        Node fooNode = objectNode.get("foo");
        assertTrue("Property `foo' must be an object", fooNode.isObject());

        ObjectNode fooObjectNode = (ObjectNode)fooNode;
        assertTrue("Property `foo.a` expected", fooObjectNode.has("a"));
        assertTrue("Property `foo.b` expected", fooObjectNode.has("b"));
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
}
