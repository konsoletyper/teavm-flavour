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
    public void concatenatesStrings() {
        StringComputation c = parseExpr(StringComputation.class, "byteValue + '-' + stringValue + '!' + longWrapper");
        vars.byteValue((byte)1);
        vars.stringValue("x");
        vars.longWrapper(2L);
        assertThat(c.compute(), is("1-x!2"));
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
