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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.function.Consumer;
import org.junit.Test;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.CompileTime;
import org.teavm.flavour.mp.Computation;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.flavour.mp.test.subpackage.MetaprogrammingGenerator;

/**
 *
 * @author Alexey Andreev
 */
@CompileTime
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
        assertEquals(C.class.getName(), callConstructor(C.class).getClass().getName());
        assertNull(callConstructor(D.class));

        assertNull(callConstructor(C.class, "foo", 23));

        D instance = (D) callConstructor(D.class, "foo", 23);
        assertEquals(D.class.getName(), instance.getClass().getName());
        assertEquals("foo", instance.a);
        assertEquals(23, instance.b);
    }

    @Reflected
    private static native Object callConstructor(Class<?> type);
    private static void callConstructor(Emitter<Object> em, ReflectClass<?> type) {
        ReflectMethod ctor = type.getMethod("<init>");
        if (ctor != null) {
            em.returnValue(() -> ctor.construct());
        } else {
            em.returnValue(() -> null);
        }
    }

    @Reflected
    private static native Object callConstructor(Class<?> type, String a, int b);
    private static void callConstructor(Emitter<Object> em, ReflectClass<?> type, Value<String> a, Value<Integer> b) {
        ReflectClass<String> stringClass = em.getContext().findClass(String.class);
        ReflectClass<Integer> intClass = em.getContext().findClass(int.class);
        ReflectMethod ctor = type.getMethod("<init>", stringClass, intClass);
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
    public void capturesArray() {
        assertEquals("23:foo", captureArray(23, "foo"));
    }

    @Reflected
    private static native String captureArray(int a, String b);
    private static void captureArray(Emitter<String> em, Value<Integer> a, Value<String> b) {
        Value<?>[] array = { a, em.emit(() -> ":"), b };
        em.returnValue(() -> String.valueOf(array[0].get()) + array[1].get() + array[2].get());
    }

    @Test
    public void createsProxy() {
        E proxy = createProxy(E.class, "!");
        assertEquals("foo!", proxy.foo());
        assertEquals("bar!", proxy.bar());
    }

    @Reflected
    private static native <T> T createProxy(Class<T> proxyType, String add);
    private static <T> void createProxy(Emitter<T> em, ReflectClass<T> proxyType, Value<String> add) {
        Computation<T> proxy = em.proxy(proxyType, (proxyEm, instance, method, args) -> {
            String name = method.getName();
            proxyEm.returnValue(() -> name + add.get());
        });
        em.returnValue(proxy);
    }

    @Test
    public void isInstanceWorks() {
        assertTrue(isInstance("foo", String.class));
        assertFalse(isInstance(23, String.class));
    }

    @Reflected
    private static native boolean isInstance(Object obj, Class<?> type);
    private static void isInstance(Emitter<Boolean> em, Value<Object> obj, ReflectClass<?> type) {
        em.returnValue(() -> type.isInstance(obj.get()));
    }

    interface E {
        String foo();

        String bar();
    }

    @Test
    public void capturesNull() {
        assertEquals("foo:bar", captureArgument("foo", "bar"));
        assertEquals("foo:null", captureArgument("foo", null));
    }

    @Reflected
    private static native String captureArgument(String a, String b);
    private static void captureArgument(Emitter<String> em, Value<String> a, String b) {
        em.returnValue(() -> a.get() + ":" + b);
    }

    static class Context {
        public int a;
        public int b;
    }

    @Test
    public void lazyWorks() {
        WithSideEffect a = new WithSideEffect(10);
        WithSideEffect b = new WithSideEffect(20);
        assertEquals(1, withLazy(a, b));
        assertEquals(1, a.reads);
        assertEquals(0, b.reads);

        a = new WithSideEffect(-10);
        b = new WithSideEffect(20);
        assertEquals(1, withLazy(a, b));
        assertEquals(1, a.reads);
        assertEquals(1, b.reads);

        a = new WithSideEffect(-10);
        b = new WithSideEffect(-20);
        assertEquals(2, withLazy(a, b));
        assertEquals(1, a.reads);
        assertEquals(1, b.reads);
    }

    @Reflected
    private static native int withLazy(WithSideEffect a, WithSideEffect b);
    private static void withLazy(Emitter<Integer> em, Value<WithSideEffect> a, Value<WithSideEffect> b) {
        Value<Boolean> first = em.lazy(() -> a.get().getValue() > 0);
        Value<Boolean> second = em.lazy(() -> b.get().getValue() > 0);
        em.returnValue(() -> first.get() || second.get() ? 1 : 2);
    }

    static class WithSideEffect {
        private int value;
        public int reads;

        public WithSideEffect(int value) {
            this.value = value;
        }

        public int getValue() {
            ++reads;
            return value;
        }
    }

    @Test
    public void annotationsWork() {
        assertEquals(""
                + "foo:23:Object\n"
                + "foo=!:42:String:int\n"
                + "f=!:23\n",
                readAnnotations(new WithAnnotations()));
    }

    @Reflected
    private static native String readAnnotations(Object obj);
    private static void readAnnotations(Emitter<String> em, ReflectValue<Object> obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(describeAnnotation(obj.getReflectClass().getAnnotation(TestAnnotation.class))).append('\n');
        for (ReflectMethod method : obj.getReflectClass().getDeclaredMethods()) {
            TestAnnotation annot = method.getAnnotation(TestAnnotation.class);
            if (annot == null) {
                continue;
            }
            sb.append(method.getName()).append('=').append(describeAnnotation(annot)).append('\n');
        }
        for (ReflectField field : obj.getReflectClass().getDeclaredFields()) {
            TestAnnotation annot = field.getAnnotation(TestAnnotation.class);
            if (annot == null) {
                continue;
            }
            sb.append(field.getName()).append('=').append(describeAnnotation(annot)).append('\n');
        }
        String result = sb.toString();
        em.returnValue(() -> result);
    }

    private static String describeAnnotation(TestAnnotation annot) {
        StringBuilder sb = new StringBuilder();
        sb.append(annot.a()).append(':').append(annot.b());
        for (Class<?> cls : annot.c()) {
            sb.append(':').append(cls.getSimpleName());
        }
        return sb.toString();
    }

    @TestAnnotation(a = "foo", c = Object.class)
    static class WithAnnotations {
        @TestAnnotation(c = {})
        int f;

        @TestAnnotation(b = 42, c = { String.class, int.class })
        int foo() {
            return 0;
        }
    }

    @Test
    public void compileTimeAnnotationRespectsPackage() {
        assertEquals("(foo)", compileTimePackage());
    }

    @Reflected
    private static native String compileTimePackage();
    private static void compileTimePackage(Emitter<String> em) {
        em.returnValue(new MetaprogrammingGenerator(em).addParentheses("foo"));
    }

    @Test
    public void compileTimeAnnotationRespectsClass() {
        assertEquals("[foo]", compileTimeClass());
    }

    @Reflected
    private static native String compileTimeClass();
    private static void compileTimeClass(Emitter<String> em) {
        em.returnValue(new MetaprogrammingGenerator2(em).addParentheses("foo"));
    }

    @Test
    public void compileTimeAnnotationRespectsNestedClass() {
        assertEquals("{foo}", compileTimeNestedClass());
    }

    @Reflected
    private static native String compileTimeNestedClass();
    private static void compileTimeNestedClass(Emitter<String> em) {
        em.returnValue(new MetaprogrammingGenerator3(em).addParentheses("foo"));
    }

    static class MetaprogrammingGenerator3 {
        private Emitter<?> emitter;

        public MetaprogrammingGenerator3(Emitter<?> emitter) {
            this.emitter = emitter;
        }

        public Value<String> addParentheses(String value) {
            return emitter.emit(() -> "{" + value + "}");
        }
    }

    @Test
    public void emitsClassLiteralFromReflectClass() {
        assertEquals(String[].class.getName(), emitClassLiteral(String.class));
    }

    @Reflected
    private static native String emitClassLiteral(Class<?> cls);
    private static void emitClassLiteral(Emitter<String> em, ReflectClass<?> cls) {
        ReflectClass<?> arrayClass = em.getContext().arrayClass(cls);
        em.returnValue(() -> arrayClass.asJavaClass().getName());
    }

    @Test
    public void createsArrayViaReflection() {
        Object array = createArrayOfType(String.class, 10);
        assertEquals(String[].class, array.getClass());
        assertEquals(10, ((String[]) array).length);
    }

    @Reflected
    private static native Object createArrayOfType(Class<?> cls, int size);
    private static void createArrayOfType(Emitter<Object> em, ReflectClass<?> cls, Value<Integer> size) {
        em.returnValue(() -> cls.createArray(size.get()));
    }

    @Test
    public void getsArrayElementViaReflection() {
        assertEquals("foo", getArrayElement(String.class, new String[] { "foo" }, 0));
    }

    @Reflected
    private static native Object getArrayElement(Class<?> type, Object array, int index);
    private static void getArrayElement(Emitter<Object> em, ReflectClass<?> type, Value<Object> array,
            Value<Integer> index) {
        em.returnValue(() -> type.getArrayElement(array.get(), index.get()));
    }

    // TODO: test returnValue(lazyValue) and emit(lazyValue)

    // TODO: test proxy method with no returnValue ever called
}
