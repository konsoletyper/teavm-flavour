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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.Test;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

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

    @Test
    public void ignoresByList() {
        IgnoredProperties o = new IgnoredProperties();
        JsonNode node = JSONRunner.serialize(o);

        assertFalse("Should not have `foo' property", node.has("foo"));
        assertTrue("Should have `bar' property", node.has("bar"));
    }

    @Test
    public void serializesBuiltInTypes() {
        BuiltInTypes o = new BuiltInTypes();
        o.boolField = true;
        o.byteField = 1;
        o.charField = '0';
        o.shortField = 2;
        o.intField = 3;
        o.longField = 4L;
        o.floatField = 5F;
        o.doubleField = 6.0;
        o.bigIntField = BigInteger.valueOf(7);
        o.bigDecimalField = BigDecimal.valueOf(8);
        o.list = Arrays.<Object>asList("foo", 1);
        o.map = new HashMap<>();
        o.map.put("key1", "value");
        o.map.put("key2", 23);
        o.set = new HashSet<>(Arrays.<Object>asList("bar", 2));

        JsonNode node = JSONRunner.serialize(o);

        assertEquals(true, node.get("boolField").asBoolean());
        assertEquals(1, node.get("byteField").asInt());
        assertEquals("0", node.get("charField").asText());
        assertEquals(2, node.get("shortField").asInt());
        assertEquals(3, node.get("intField").asInt());
        assertEquals(4, node.get("longField").asInt());
        assertEquals(5, node.get("floatField").asInt());
        assertEquals(6, node.get("doubleField").asInt());
        assertEquals(7, node.get("bigIntField").asInt());
        assertEquals(8, node.get("bigDecimalField").asInt());
        assertEquals(2, node.get("list").size());
        assertEquals("foo", node.get("list").get(0).textValue());
        assertEquals(1, node.get("list").get(1).intValue());
        assertEquals("value", node.get("map").get("key1").asText());
        assertEquals(23, node.get("map").get("key2").asInt());
        assertEquals(2, node.get("set").size());
    }

    @Test
    public void serializesTypeInfo() {
        InheritanceBase o = new InheritanceBase();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `@c' property", node.has("@c"));
        assertTrue("Should have `foo' property", node.has("foo"));
        assertFalse("Should not have `bar' property", node.has("bar"));
        assertEquals(".SerializerTest$InheritanceBase", node.get("@c").asText());

        o = new Inheritance();
        node = JSONRunner.serialize(o);

        assertTrue("Should have `@c' property", node.has("@c"));
        assertTrue("Should have `foo' property", node.has("foo"));
        assertTrue("Should have `bar' property", node.has("bar"));
        assertEquals(".SerializerTest$Inheritance", node.get("@c").asText());
    }

    @Test
    public void serializesTypeInfoByName() {
        InheritanceByTypeNameBase o = new InheritanceByTypeNameBase();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `@type' property", node.has("@type"));
        assertEquals("basetype", node.get("@type").asText());

        o = new InheritanceByTypeName();
        node = JSONRunner.serialize(o);

        assertTrue("Should have `@type' property", node.has("@type"));
        assertEquals("subtype", node.get("@type").asText());

        o = new InheritanceByExplicitTypeName();
        node = JSONRunner.serialize(o);

        assertTrue("Should have `@type' property", node.has("@type"));
        assertEquals("basetype", node.get("@type").asText());
    }

    @Test
    public void serializesTypeInfoByFullClass() {
        InheritanceByFullNameBase o = new InheritanceByFullNameBase();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `@class' property", node.has("@class"));
        assertEquals(InheritanceByFullNameBase.class.getName(), node.get("@class").asText());

        o = new InheritanceByFullName();
        node = JSONRunner.serialize(o);

        assertTrue("Should have `@class' property", node.has("@class"));
        assertEquals(InheritanceByFullName.class.getName(), node.get("@class").asText());
    }

    @Test
    public void serializedTypeInfoAsWrapperObject() {
        InheritanceAsWrapperObjectBase o = new InheritanceAsWrapperObjectBase();
        JsonNode node = JSONRunner.serialize(o);

        assertTrue("Should have `base' property", node.has("base"));
        assertTrue("root.base should be an object", node.get("base").isObject());
        assertTrue("Should have root.base.foo", node.get("base").has("foo"));
        assertFalse("Should not have root.base.bar", node.get("base").has("bar"));

        o = new InheritanceAsWrapperObject();
        node = JSONRunner.serialize(o);

        assertTrue("Should have `subtype' property", node.has("subtype"));
        assertTrue("root.subtype should be an object", node.get("subtype").isObject());
        assertTrue("Should have root.subtype.foo", node.get("subtype").has("foo"));
        assertTrue("Should have root.subtype.bar", node.get("subtype").has("bar"));
    }

    @Test
    public void serializedTypeInfoAsWrapperArray() {
        InheritanceAsWrapperArrayBase o = new InheritanceAsWrapperArrayBase();
        JsonNode node = JSONRunner.serialize(o);

        assertEquals("base", node.get(0).asText());
        assertTrue("Second item should be an object", node.get(1).isObject());
        assertTrue("Should have root[1].foo", node.get(1).has("foo"));
        assertFalse("Should not have root[1].bar", node.get(1).has("bar"));

        o = new InheritanceAsWrapperArray();
        node = JSONRunner.serialize(o);

        assertEquals("subtype", node.get(0).asText());
        assertTrue("Second item should be an object", node.get(1).isObject());
        assertTrue("Should have root[1].foo", node.get(1).has("foo"));
        assertTrue("Should have root[1].bar", node.get(1).has("bar"));
    }

    @Test
    public void generatesIds() {
        GraphNode a = new GraphNode();
        GraphNode b = new GraphNode();
        a.successors.add(a);
        a.successors.add(b);
        b.successors.add(a);
        JsonNode node = JSONRunner.serialize(a);

        assertTrue("Should have `@id' property", node.has("@id"));
        int aId = node.get("@id").intValue();

        JsonNode successors = node.get("successors");
        assertEquals(2, successors.size());

        JsonNode firstSuccessor = successors.get(0);
        assertTrue("`successors[0].successors' should be integer", firstSuccessor.isInt());
        assertEquals(aId, firstSuccessor.asInt());

        JsonNode secondSuccessor = successors.get(1);
        assertTrue("Should have `successors[1].successors'", secondSuccessor.has("successors"));
        assertNotEquals(aId, secondSuccessor.get("@id").asInt());

        successors = secondSuccessor.get("successors");
        assertEquals(1, successors.size());

        firstSuccessor = successors.get(0);
        assertTrue("`successors[1].successors[0]' should be integer", firstSuccessor.isInt());
        assertEquals(aId, firstSuccessor.asInt());
    }

    @Test
    public void writesFormattedDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        calendar.setTimeInMillis(0);
        calendar.set(Calendar.YEAR, 2015);
        calendar.set(Calendar.MONTH, Calendar.AUGUST);
        calendar.set(Calendar.DATE, 2);
        calendar.set(Calendar.HOUR_OF_DAY, 16);
        calendar.set(Calendar.MINUTE, 25);
        calendar.set(Calendar.SECOND, 35);
        Date date = calendar.getTime();

        DateFormats formats = new DateFormats();
        formats.numeric = date;
        formats.textual = date;

        JsonNode node = JSONRunner.serialize(formats);
        assertTrue("Numeric date is a number", node.get("numeric").isNumber());
        assertEquals(date.getTime(), node.get("numeric").asDouble(), 0.1);
        assertTrue("Textual date is a string", node.get("textual").isTextual());
        assertEquals("2015-08-02 16:25:35 Z", node.get("textual").asText());
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
        public BigInteger bigIntField;
        public BigDecimal bigDecimalField;
        public List<Object> list;
        public Set<Object> set;
        public Map<Object, Object> map;
        public Visibility visibility;
    }

    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    @JsonIgnoreProperties("foo")
    public static class IgnoredProperties {
        public int foo;
        public int bar;
    }

    @JsonTypeInfo(use = Id.MINIMAL_CLASS)
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class InheritanceBase {
        public int foo;
    }

    public static class Inheritance extends InheritanceBase {
        public int bar;
    }

    @JsonTypeInfo(use = Id.NAME)
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    @JsonTypeName("basetype")
    public static class InheritanceByTypeNameBase {
        public int foo;
    }

    @JsonTypeName("subtype")
    public static class InheritanceByTypeName extends InheritanceByTypeNameBase {
        public int bar;
    }

    public static class InheritanceByExplicitTypeName extends InheritanceByTypeNameBase {
        public int bar;
    }

    @JsonTypeInfo(use = Id.CLASS)
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class InheritanceByFullNameBase {
        public int foo;
    }

    public static class InheritanceByFullName extends InheritanceByFullNameBase {
        public int bar;
    }

    @JsonTypeInfo(use = Id.NAME, include = As.WRAPPER_OBJECT)
    @JsonTypeName("base")
    public static class InheritanceAsWrapperObjectBase {
        public int foo;
    }

    @JsonTypeName("subtype")
    public static class InheritanceAsWrapperObject extends InheritanceAsWrapperObjectBase {
        public int bar;
    }

    @JsonTypeInfo(use = Id.NAME, include = As.WRAPPER_ARRAY)
    @JsonTypeName("base")
    public static class InheritanceAsWrapperArrayBase {
        public int foo;
    }

    @JsonTypeName("subtype")
    public static class InheritanceAsWrapperArray extends InheritanceAsWrapperArrayBase {
        public int bar;
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class GraphNode {
        private List<GraphNode> successors = new ArrayList<>();

        public List<GraphNode> getSuccessors() {
            return successors;
        }
    }

    public static class DateFormats {
        public Date numeric;

        @JsonFormat(shape = Shape.STRING, pattern = "YYYY-MM-dd HH:mm:ss XX")
        public Date textual;
    }
}
