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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.flavour.json.JsonPersistable;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
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
    public void readsEmpty() {
        A objA = JSONRunner.deserialize("{ \"b\" : 23 }", A.class);
        assertNull(objA.getA());
        assertEquals(23, objA.getB());
        B objB = JSONRunner.deserialize("{}", B.class);
        assertNull(objB.getFoo());
        C objC = JSONRunner.deserialize("{}", C.class);
        assertNull(objC.getC());
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
    public void setsViaConstructor() {
        WithConstructor obj = JSONRunner.deserialize("{ \"foo\" : 23, \"bar\" : \"q\" }", WithConstructor.class);
        assertEquals(23, obj.getFoo());
        assertEquals("q", obj.getBar());

        WithConstructorAsCreator obj2 = JSONRunner.deserialize("{ \"foo\" : 42, \"bar\" : \"w\" }",
                WithConstructorAsCreator.class);
        assertEquals(42, obj2.getFoo());
        assertEquals("w", obj2.getBar());
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
                "\"visibility\" : \"FOO\" }",
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
        assertEquals(SomeEnum.FOO, obj.visibility);
    }

    @Test
    public void readsPolymorhic() {
        InheritanceBase obj = JSONRunner.deserialize("{ \"@c\" : \".DeserializerTest$Inheritance\"," +
                "\"foo\" : 2, \"bar\": 3 }", InheritanceBase.class);
        assertTrue("Must be instance of " + Inheritance.class.getName(), obj instanceof Inheritance);
        Inheritance poly = (Inheritance)obj;
        assertEquals(2, poly.foo);
        assertEquals(3, poly.bar);
    }

    @Test
    public void readsPolymorhicByTypeName() {
        InheritanceByTypeNameBase obj = JSONRunner.deserialize("{ \"@type\" : \"subtype\"," +
                "\"foo\" : 2, \"bar\": 3 }", InheritanceByTypeNameBase.class);

        assertTrue("Must be instance of " + InheritanceByTypeName.class.getName(),
                obj instanceof InheritanceByTypeName);
        InheritanceByTypeName poly = (InheritanceByTypeName)obj;
        assertEquals(2, poly.foo);
        assertEquals(3, poly.bar);

        obj = JSONRunner.deserialize("{ \"@type\" : \"basetype\", \"foo\" : 4 }", InheritanceByTypeNameBase.class);
        assertFalse("Must not be instance of " + InheritanceByTypeName.class.getName(),
                obj instanceof InheritanceByTypeName);
        assertEquals(4, obj.foo);
    }

    @Test
    public void readsPolymorhicAsWrappedObject() {
        InheritanceAsWrapperObjectBase obj = JSONRunner.deserialize("{ \"subtype\" : { " +
                "\"foo\" : 2, \"bar\": 3 } }", InheritanceAsWrapperObjectBase.class);

        assertTrue("Must be instance of " + InheritanceAsWrapperObject.class.getName(),
                obj instanceof InheritanceAsWrapperObject);
        InheritanceAsWrapperObject poly = (InheritanceAsWrapperObject)obj;
        assertEquals(2, poly.foo);
        assertEquals(3, poly.bar);

        obj = JSONRunner.deserialize("{ \"base\" : { \"foo\" : 4 } }", InheritanceAsWrapperObjectBase.class);
        assertFalse("Must not be instance of " + InheritanceAsWrapperObject.class.getName(),
                obj instanceof InheritanceAsWrapperObject);
        assertEquals(4, obj.foo);
    }

    @Test
    public void readsPolymorhicAsWrappedArray() {
        InheritanceAsWrapperArrayBase obj = JSONRunner.deserialize("[ \"subtype\", { " +
                "\"foo\" : 2, \"bar\": 3 } ]", InheritanceAsWrapperArrayBase.class);

        assertTrue("Must be instance of " + InheritanceAsWrapperArray.class.getName(),
                obj instanceof InheritanceAsWrapperArray);
        InheritanceAsWrapperArray poly = (InheritanceAsWrapperArray)obj;
        assertEquals(2, poly.foo);
        assertEquals(3, poly.bar);

        obj = JSONRunner.deserialize("[ \"base\", { \"foo\" : 4 } ]", InheritanceAsWrapperArrayBase.class);
        assertFalse("Must not be instance of " + InheritanceAsWrapperObject.class.getName(),
                obj instanceof InheritanceAsWrapperArray);
        assertEquals(4, obj.foo);
    }

    @Test
    public void readsCircularReferences() {
        GraphNode node = JSONRunner.deserialize("{ \"@id\" : 1, \"successors\" : [ " +
                "1, { \"@id\" : 2, \"successors\" : [ 1 ] }, { \"successors\" : [] } ] }", GraphNode.class);
        assertEquals(3, node.successors.size());
        assertSame(node, node.successors.get(0));
        assertEquals(1, node.successors.get(1).successors.size());
        assertSame(node, node.successors.get(1).successors.get(0));
    }

    @Test
    public void arrayOfStrings() {
        String[] stringArray = JSONRunner.deserialize("[\"one\", \"two\"]", String[].class);
        assertEquals(2, stringArray.length);
        assertEquals("one", stringArray[0]);
        assertEquals("two", stringArray[1]);
    }

    @Test
    public void readsPrivateField() {
        PrivateField obj = JSONRunner.deserialize("{ \"a\" : 123 }", PrivateField.class);
        assertEquals(123, obj.a);
    }

    @Test
    public void readsDate() {
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

        DateFormats o = JSONRunner.deserialize("{ \"numeric\": " + date.getTime() + ", "
                + "\"textual\": \"2015-08-02 16:25:35 Z\" }", DateFormats.class);
        assertEquals(date.getTime(), o.numeric.getTime(), 5);
        assertEquals(date.getTime(), o.textual.getTime(), 5);
    }

    @Test
    public void readsNullDate() {
        DateFormats o = JSONRunner.deserialize("{ \"numeric\": null, \"textual\": null }", DateFormats.class);
        assertNull(o.numeric);
        assertNull(o.textual);
    }

    @Test
    public void abstractNonPersistableSuperclassWithConstructor() {
        SubClass o = JSONRunner.deserialize("{ \"superField\": \"foo\" }", SubClass.class);
        assertEquals("foo", o.superField);
    }

    @Test
    public void abstractSuperclass() {
        // Workaround for issue in TeaVM
        System.out.println(AbstractPersistableSuperclass.class.getName());

        AbstractPersistableSuperclass[] array = JSONRunner.deserialize("[ "
                + "{ \"type\": \"A\", \"foo\": 1, \"bar\": 2 },"
                + "{ \"type\": \"B\", \"foo\": 3, \"baz\": 4 } "
        + "]", AbstractPersistableSuperclass[].class);

        assertEquals("Unexpected array length", 2, array.length);
        assertTrue("Unexpected type of first element", array[0] instanceof ConcreteSubtypeA);
        ConcreteSubtypeA a = (ConcreteSubtypeA) array[0];
        assertEquals(1, a.foo);
        assertEquals(2, a.bar);
        assertTrue("Unexpected type of second element", array[1] instanceof ConcreteSubtypeB);
        ConcreteSubtypeB b = (ConcreteSubtypeB) array[1];
        assertEquals(3, b.foo);
        assertEquals(4, b.baz);
    }

    @JsonPersistable
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
    }

    @JsonPersistable
    public static class C {
        Integer c;

        public Integer getC() {
            return c;
        }

        public void setC(Integer c) {
            this.c = c;
        }
    }

    @JsonPersistable
    public static class B {
        private A foo;

        public A getFoo() {
            return foo;
        }

        public void setFoo(A foo) {
            this.foo = foo;
        }
    }

    @JsonPersistable
    public static class ArrayProperty {
        int[] array;

        void setArray(int[] array) {
            this.array = array;
        }
    }

    @JsonPersistable
    public static class ArrayOfObjectProperty {
        A[] array;

        public A[] getArray() {
            return array;
        }

        public void setArray(A[] array) {
            this.array = array;
        }
    }

    @JsonPersistable
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

    @JsonPersistable
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

    @JsonPersistable
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class FieldAndSetter {
        public int foo;

        public void setFoo(int foo) {
            this.foo = foo + 1;
        }
    }

    @JsonPersistable
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    public static class FieldVisible {
        public int foo;
    }

    @JsonPersistable
    public static class WithConstructor {
        private int foo;
        private String bar;

        public WithConstructor(@JsonProperty("foo") int foo, @JsonProperty("bar") String bar) {
            this.foo = foo;
            this.bar = bar;
        }

        public int getFoo() {
            return foo;
        }

        public String getBar() {
            return bar;
        }
    }

    @JsonPersistable
    public static class WithConstructorAsCreator {
        private int foo;
        private String bar;

        @JsonCreator
        public WithConstructorAsCreator(@JsonProperty("foo") int foo, @JsonProperty("bar") String bar) {
            this.foo = foo;
            this.bar = bar;
        }

        public WithConstructorAsCreator() {
            this.foo = 2323;
            this.bar = "fail";
        }

        public int getFoo() {
            return foo;
        }

        public String getBar() {
            return bar;
        }
    }

    @JsonPersistable
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
        public SomeEnum visibility;
    }

    @JsonPersistable
    public enum SomeEnum {
        FOO,
        BAR
    }

    @JsonPersistable
    @JsonTypeInfo(use = Id.MINIMAL_CLASS)
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    @JsonSubTypes({ @Type(Inheritance.class) })
    public static class InheritanceBase {
        public int foo;
    }

    @JsonPersistable
    public static class Inheritance extends InheritanceBase {
        public int bar;
    }

    @JsonPersistable
    @JsonTypeInfo(use = Id.NAME)
    @JsonAutoDetect(fieldVisibility = Visibility.PROTECTED_AND_PUBLIC)
    @JsonTypeName("basetype")
    @JsonSubTypes({ @Type(InheritanceByTypeName.class) })
    public static class InheritanceByTypeNameBase {
        public int foo;
    }

    @JsonPersistable
    @JsonTypeName("subtype")
    public static class InheritanceByTypeName extends InheritanceByTypeNameBase {
        public int bar;
    }

    @JsonPersistable
    @JsonTypeInfo(use = Id.NAME, include = As.WRAPPER_OBJECT)
    @JsonTypeName("base")
    @JsonSubTypes({ @Type(InheritanceAsWrapperObject.class) })
    public static class InheritanceAsWrapperObjectBase {
        public int foo;
    }

    @JsonPersistable
    @JsonTypeName("subtype")
    public static class InheritanceAsWrapperObject extends InheritanceAsWrapperObjectBase {
        public int bar;
    }

    @JsonPersistable
    @JsonTypeInfo(use = Id.NAME, include = As.WRAPPER_ARRAY)
    @JsonTypeName("base")
    @JsonSubTypes({ @Type(InheritanceAsWrapperArray.class) })
    public static class InheritanceAsWrapperArrayBase {
        public int foo;
    }

    @JsonPersistable
    @JsonTypeName("subtype")
    public static class InheritanceAsWrapperArray extends InheritanceAsWrapperArrayBase {
        public int bar;
    }

    @JsonPersistable
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
    public static class GraphNode {
        private List<GraphNode> successors;

        public List<GraphNode> getSuccessors() {
            return successors;
        }
    }

    @JsonPersistable
    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    public static class PrivateField {
        private int a;
    }

    @JsonPersistable
    public static class DateFormats {
        public Date numeric;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss XX")
        public Date textual;
    }

    public static abstract class SuperClass {
        public final String superField;

        public SuperClass(String superField) {
            this.superField = superField;
        }
    }

    @JsonPersistable
    public static class SubClass extends SerializerTest.SuperClass {
        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public SubClass(@JsonProperty("superField") String superField) {
            super(superField);
        }
    }

    @JsonPersistable
    @JsonTypeInfo(use = Id.NAME, property = "type", include = As.PROPERTY)
    @JsonSubTypes({ @JsonSubTypes.Type(ConcreteSubtypeA.class), @JsonSubTypes.Type(ConcreteSubtypeB.class) })
    public static abstract class AbstractPersistableSuperclass {
        public int foo;
    }

    @JsonPersistable
    @JsonTypeName("A")
    public static class ConcreteSubtypeA extends AbstractPersistableSuperclass {
        public int bar;
    }

    @JsonPersistable
    @JsonTypeName("B")
    public static class ConcreteSubtypeB extends AbstractPersistableSuperclass {
        public int baz;
    }
}
