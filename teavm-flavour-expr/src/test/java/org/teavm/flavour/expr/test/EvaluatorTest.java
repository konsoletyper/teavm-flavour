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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.teavm.flavour.expr.Evaluator;
import org.teavm.flavour.expr.EvaluatorBuilder;
import org.teavm.flavour.expr.InterpretingEvaluatorBuilder;

/**
 *
 * @author Alexey Andreev
 */
public class EvaluatorTest {
    TestVars vars;

    @Test
    public void evaluatesIntConstant() {
        IntComputation c = parseExpr(IntComputation.class, "23");
        assertThat(c.compute(), is(23));
    }

    @Test
    public void evaluatesBooleanConstant() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "true");
        assertThat(c.compute(), is(true));
    }

    @Test
    public void evaluatesStringConstant() {
        StringComputation c = parseExpr(StringComputation.class, "'foo'");
        assertThat(c.compute(), is("foo"));
    }

    @Test
    public void evaluatesVariable() {
        IntComputation c = parseExpr(IntComputation.class, "intValue");
        vars.intValue(42);
        assertThat(c.compute(), is(42));
    }

    @Test
    public void evaluatesArithmetics() {
        IntComputation c = parseExpr(IntComputation.class, "intValue - 3");
        vars.intValue(8);
        assertThat(c.compute(), is(5));
    }

    @Test
    public void leftAssociativity() {
        IntComputation c = parseExpr(IntComputation.class, "3 - 2 + 1");
        assertThat(c.compute(), is(2));
    }

    @Test
    public void implicitlyCastsByteToInt() {
        IntComputation c = parseExpr(IntComputation.class, "byteValue - 3");
        vars.byteValue((byte)8);
        assertThat(c.compute(), is(5));
    }

    @Test
    public void implicitlyCastsByteToLong() {
        LongComputation c = parseExpr(LongComputation.class, "longWrapper - byteValue");
        vars.longWrapper(8L);
        vars.byteValue((byte)3);
        assertThat(c.compute(), is(5L));
    }

    @Test
    public void addsNumbers() {
        LongComputation c = parseExpr(LongComputation.class, "longWrapper + byteValue");
        vars.longWrapper(2L);
        vars.byteValue((byte)3);
        assertThat(c.compute(), is(5L));
    }

    @Test
    public void concatenatesStrings() {
        StringComputation c = parseExpr(StringComputation.class, "byteValue + '-' + stringValue + '!' + longWrapper");
        vars.byteValue((byte)1);
        vars.stringValue("x");
        vars.longWrapper(2L);
        assertThat(c.compute(), is("1-x!2"));
    }

    @Test
    public void comparesNumbersForEquality() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "byteValue == intValue");

        vars.byteValue((byte)2);
        vars.intValue(2);
        assertThat(c.compute(), is(true));
        vars.intValue(3);
        assertThat(c.compute(), is(false));

        vars.byteValue((byte)-1);
        vars.intValue(-1);
        assertThat(c.compute(), is(true));
    }

    @Test
    public void comparesNumberAndWrapper() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "byteValue == longWrapper");

        vars.byteValue((byte)2);
        vars.longWrapper(2L);
        assertThat(c.compute(), is(true));

        vars.longWrapper(3L);
        assertThat(c.compute(), is(false));
    }

    @Test
    public void comparesNumbers() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "byteValue > intValue");

        vars.byteValue((byte)2);
        vars.intValue(1);
        assertThat(c.compute(), is(true));

        vars.intValue(2);
        assertThat(c.compute(), is(false));

        vars.intValue(3);
        assertThat(c.compute(), is(false));
    }

    @Test
    public void comparesReferences() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "object == longWrapper");

        Long value = Long.valueOf(2);
        vars.longWrapper(value);
        vars.object(value);
        assertThat(c.compute(), is(true));

        vars.object(new Object());
        assertThat(c.compute(), is(false));

        vars.longWrapper(null);
        vars.object(null);
        assertThat(c.compute(), is(true));
    }

    @Test
    public void computesAndOperation() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "object == null and intValue == 3");

        vars.object(null);
        vars.intValue(3);
        assertThat(c.compute(), is(true));

        vars.object(new Object());
        vars.intValue(3);
        assertThat(c.compute(), is(false));

        vars.object(null);
        vars.intValue(2);
        assertThat(c.compute(), is(false));
    }

    @Test
    public void computesOrOperation() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "object == null or intValue == 3");

        vars.object(new Object());
        vars.intValue(2);
        assertThat(c.compute(), is(false));

        vars.object(new Object());
        vars.intValue(3);
        assertThat(c.compute(), is(true));

        vars.object(null);
        vars.intValue(2);
        assertThat(c.compute(), is(true));
    }

    @Test
    public void castsLongToInt() {
        IntComputation c = parseExpr(IntComputation.class, "(int)longWrapper");
        vars.longWrapper(23L);
        assertThat(c.compute(), is(23));
    }

    @Test
    public void castsObjectToLong() {
        LongComputation c = parseExpr(LongComputation.class, "(java.lang.Long)object");
        vars.object(2L);
        assertThat(c.compute(), is(2L));
    }

    @Test
    public void castsObjectToPrimitiveArray() {
        IntComputation c = parseExpr(IntComputation.class, "((int[])object)[0]");
        vars.object(new int[] { 23 });
        assertThat(c.compute(), is(23));
    }

    @Test
    public void evaluatesInstanceOf() {
        BooleanComputation c = parseExpr(BooleanComputation.class, "object instanceof java.lang.Long");

        vars.object(null);
        assertThat(c.compute(), is(false));

        vars.object("foo");
        assertThat(c.compute(), is(false));

        vars.object(23L);
        assertThat(c.compute(), is(true));
    }

    private <T> T parseExpr(Class<T> cls, String str) {
        EvaluatorBuilder builder = new InterpretingEvaluatorBuilder();
        Evaluator<T, TestVars> e = builder.build(cls, TestVars.class, str);
        vars = e.getVariables();
        return e.getFunction();
    }

    interface TestVars {
        void intValue(int v);

        void stringValue(String v);

        void doubleValue(double v);

        void byteValue(byte v);

        void longWrapper(Long v);

        void object(Object v);
    }

    interface BooleanComputation {
        boolean compute();
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
}
