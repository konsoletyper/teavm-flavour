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
import static org.junit.Assert.assertNull;
import java.util.function.Consumer;
import org.junit.Test;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;

/**
 *
 * @author Alexey Andreev
 */
public class MetaprogrammingTest {
    @Test
    public void works() {
        assertEquals("java.lang.Object".length() + 2, classNameLength(new Object(), 2));
        assertEquals("java.lang.Integer".length() + 3, classNameLength(5, 3));
    }

    @Reflected
    static native int classNameLength(Object obj, int add);
    static void classNameLength(Emitter<Integer> em, ReflectValue<Object> obj, Value<Integer> add) {
        int length = obj.getReflectClass().getName().length();
        em.returnValue(() -> length + add.get());
    }

    @Test
    public void getsField() {
        Context ctx = new Context();
        ctx.a = 2;
        ctx.b = 3;

        assertEquals(2, getField(ctx, "a"));
        assertEquals(3, getField(ctx, "b"));
    }

    @Reflected
    private static native Object getField(Object obj, String name);
    private static void getField(Emitter<Object> em, ReflectValue<Object> obj, String name) {
        ReflectField field = obj.getReflectClass().getField(name);
        em.returnValue(() -> field.get(obj));
    }

    @Test
    public void setsField() {
        Context ctx = new Context();
        setField(ctx, "a", 3);
        setField(ctx, "b", 2);

        assertEquals(3, ctx.a);
        assertEquals(2, ctx.b);
    }

    @Reflected
    private static native void setField(Object obj, String name, Object value);
    private static void setField(Emitter<Void> em, ReflectValue<Object> obj, String name, Value<Object> value) {
        ReflectField field = obj.getReflectClass().getField(name);
        em.emit(() -> field.set(obj, value));
    }

    @Test
    public void conditionalWorks() {
        Context ctx = new Context();

        assertEquals("int", fieldType(ctx, "a"));
        assertEquals("int", fieldType(ctx, "b"));
        assertNull(fieldType(ctx, "c"));
    }

    @Reflected
    private static native String fieldType(Object obj, String name);
    private static void fieldType(Emitter<String> em, ReflectValue<Object> obj, Value<String> name) {
        Choice<String> result = em.choose(String.class);
        ReflectClass<Object> cls = obj.getReflectClass();
        for (ReflectField field : cls.getDeclaredFields()) {
            String type = field.getType().getName();
            String fieldName = field.getName();
            result.option(() -> fieldName.equals(name.get())).returnValue(() -> type);
        }
        em.returnValue(result.getValue());
    }

    @Test
    public void conditionalActionWorks() {
        Context ctx = new Context();
        class TypeConsumer implements Consumer<String> {
            String type;
            @Override public void accept(String t) {
                type = t;
            }
        }
        TypeConsumer consumer = new TypeConsumer();

        fieldType(ctx, "a", consumer);
        assertEquals("int", consumer.type);

        fieldType(ctx, "b", consumer);
        assertEquals("int", consumer.type);

        fieldType(ctx, "c", consumer);
        assertNull(consumer.type);
    }

    @Reflected
    private static native void fieldType(Object obj, String name, Consumer<String> typeConsumer);
    private static void fieldType(Emitter<Void> em, ReflectValue<Object> obj, Value<String> name,
            Value<Consumer<String>> typeConsumer) {
        Choice<Void> result = em.choose(Void.class);
        ReflectClass<Object> cls = obj.getReflectClass();
        for (ReflectField field : cls.getDeclaredFields()) {
            String type = field.getType().getName();
            String fieldName = field.getName();
            result.option(() -> fieldName.equals(name.get())).emit(() -> typeConsumer.get().accept(type));
        }
        result.defaultOption().emit(() -> typeConsumer.get().accept(null));
    }

    @Test
    public void methodInvoked() {
        assertEquals("debug!", callDebug(new A()));
        assertEquals("missing", callDebug(new B()));
        assertEquals("missing", callDebug(new A(), "foo", 23));
        assertEquals("debug!foo:23", callDebug(new B(), "foo", 23));
    }

    @Reflected
    private static native String callDebug(Object obj);
    private static void callDebug(Emitter<String> em, ReflectValue<Object> obj) {
        ReflectMethod method = obj.getReflectClass().getMethod("debug");
        if (method == null) {
            em.returnValue(() -> "missing");
        } else {
            em.returnValue(() -> (String) method.invoke(obj.get()));
        }
    }

    @Reflected
    private static native String callDebug(Object obj, String a, int b);
    private static void callDebug(Emitter<String> em, ReflectValue<Object> obj, Value<String> a, Value<Integer> b) {
        ReflectClass<String> stringClass = em.getContext().findClass(String.class);
        ReflectClass<Integer> intClass = em.getContext().findClass(int.class);
        ReflectMethod method = obj.getReflectClass().getMethod("debug", stringClass, intClass);
        if (method == null) {
            em.returnValue(() -> "missing");
        } else {
            em.returnValue(() -> (String) method.invoke(obj.get(), a.get(), b.get()));
        }
    }

    class A {
        public String debug() {
            return "debug!";
        }
    }

    class B {
        public String debug(String a, int b) {
            return "debug!" + a + ":" + b;
        }
    }

    @Test
    public void constructorInvoked() {
        assertEquals(C.class.getName(), callConstructor("org.teavm.flavour.mp.test.MetaprogrammingTest$C")
                .getClass().getName());
        assertNull(callConstructor("org.teavm.flavour.mp.test.MetaprogrammingTest$D"));

        assertNull(callConstructor("org.teavm.flavour.mp.test.MetaprogrammingTest$C", "foo", 23));

        D instance = (D) callConstructor("org.teavm.flavour.mp.test.MetaprogrammingTest$D", "foo", 23);
        assertEquals(D.class.getName(), instance.getClass().getName());
        assertEquals("foo", instance.a);
        assertEquals(23, instance.b);
    }

    @Reflected
    private static native Object callConstructor(String type);
    private static void callConstructor(Emitter<Object> em, String type) {
        ReflectClass<?> cls = em.getContext().findClass(type);
        ReflectMethod ctor = cls.getMethod("<init>");
        if (ctor != null) {
            em.returnValue(() -> ctor.construct());
        } else {
            em.returnValue(() -> null);
        }
    }

    @Reflected
    private static native Object callConstructor(String type, String a, int b);
    private static void callConstructor(Emitter<Object> em, String type, Value<String> a, Value<Integer> b) {
        ReflectClass<String> stringClass = em.getContext().findClass(String.class);
        ReflectClass<Integer> intClass = em.getContext().findClass(int.class);
        ReflectClass<?> cls = em.getContext().findClass(type);
        ReflectMethod ctor = cls.getMethod("<init>", stringClass, intClass);
        if (ctor != null) {
            em.returnValue(() -> ctor.construct(a, b));
        } else {
            em.returnValue(() -> null);
        }
    }

    static class C {
        public C() {
        }
    }

    static class D {
        String a;
        int b;

        public D(String a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    @Test
    public void parser() {
        Context ctx = new Context();
        ctx.a = 2;
        ctx.b = 3;
        assertEquals(8, eval(ctx, "a+b*2"));
    }

    static class Context {
        public int a;
        public int b;
    }

    @Reflected
    static native int eval(Object context, String expression);
    static void eval(Emitter<Integer> em, ReflectValue<Object> context, String expression) {
        Parser parser = new Parser();
        parser.em = em;
        parser.evalContext = context;
        parser.string = expression + "$";
        em.returnValue(parser.additive());
    }

    static class Parser {
        Emitter<Integer> em;
        ReflectValue<Object> evalContext;
        int index;
        String string;

        Value<Integer> additive() {
            Value<Integer> value = multiplicative();
            while (true) {
                switch (string.charAt(index)) {
                    case '+': {
                        ++index;
                        Value<Integer> left = value;
                        Value<Integer> right = multiplicative();
                        value = em.emit(() -> left.get() + right.get());
                        break;
                    }
                    case '-': {
                        ++index;
                        Value<Integer> left = value;
                        Value<Integer> right = multiplicative();
                        value = em.emit(() -> left.get() - right.get());
                        break;
                    }
                    default:
                        return value;
                }
            }
        }

        Value<Integer> multiplicative() {
            Value<Integer> value = atom();
            while (true) {
                switch (string.charAt(index)) {
                    case '*': {
                        ++index;
                        Value<Integer> left = value;
                        Value<Integer> right = atom();
                        value = em.emit(() -> left.get() * right.get());
                        break;
                    }
                    case '/': {
                        ++index;
                        Value<Integer> left = value;
                        Value<Integer> right = atom();
                        value = em.emit(() -> left.get() / right.get());
                        break;
                    }
                    default:
                        return value;
                }
            }
        }

        Value<Integer> atom() {
            switch (string.charAt(index)) {
                case '(':
                    ++index;
                    Value<Integer> result = additive();
                    if (string.charAt(index) != ')') {
                        throw new ParseException();
                    }
                    ++index;
                    return result;
                default:
                    if (isDigit(string.charAt(index))) {
                        return parseNumber();
                    } else if (isIdentifierStart(string.charAt(index))) {
                        return parseIdentifier();
                    }
                    throw new ParseException();
            }
        }

        Value<Integer> parseNumber() {
            int value = 0;
            while (isDigit(string.charAt(index))) {
                value = value * 10 + string.charAt(index++) - '0';
            }
            int result = value;
            return em.emit(() -> result);
        }

        Value<Integer> parseIdentifier() {
            StringBuilder sb = new StringBuilder();
            while (isIdentifierPart(string.charAt(index))) {
                sb.append(string.charAt(index++));
            }
            String name = sb.toString();
            ReflectValue<Object> evalContext = this.evalContext;
            ReflectField field = evalContext.getReflectClass().getField(name);
            return em.emit(() -> (Integer) field.get(evalContext));
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isAlpha(char c) {
            return c >= 'a' && c <= 'z' || c >= 'a' && c <= 'z';
        }

        private static boolean isIdentifierStart(char c) {
            return isAlpha(c) || c == '_';
        }

        private static boolean isIdentifierPart(char c) {
            return isIdentifierStart(c) || isDigit(c);
        }
    }

    static class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1080254140610200947L;

        public ParseException() {
            super();
        }
    }
}
