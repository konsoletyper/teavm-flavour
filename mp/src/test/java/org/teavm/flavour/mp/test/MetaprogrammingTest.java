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
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectField;

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

    //@Test
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

    static native int eval(Object context, String expression);
    static void eval(Emitter<Integer> em, ReflectValue<Object> context, String expression) {
        Parser parser = new Parser();
        parser.em = em;
        parser.evalContext = context;
        parser.string = expression;
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
                sb.append(string.charAt(index));
            }
            String name = sb.toString();
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
