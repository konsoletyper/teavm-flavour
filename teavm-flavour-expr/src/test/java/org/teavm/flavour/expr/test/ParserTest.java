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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import org.junit.Test;
import org.teavm.flavour.expr.ClassLoaderClassSet;
import org.teavm.flavour.expr.Parser;
import org.teavm.flavour.expr.ast.*;

/**
 *
 * @author Alexey Andreev
 */
public class ParserTest {
    private Parser parser = new Parser(new ClassLoaderClassSet(ParserTest.class.getClassLoader()));

    @Test
    public void parseInteger() {
        Expr<?> expr = parser.parse("23");
        assertThat(expr, is(instanceOf(ConstantExpr.class)));
        ConstantExpr<?> constant = (ConstantExpr<?>)expr;
        assertThat(constant.getValue(), is((Object)23));
    }

    @Test
    public void parseIdentifier() {
        Expr<?> expr = parser.parse("foo");
        assertThat(expr, is(instanceOf(VariableExpr.class)));
        VariableExpr<?> var = (VariableExpr<?>)expr;
        assertThat(var.getName(), is("foo"));
    }

    @Test
    public void parseExpression() {
        Expr<?> expr = parser.parse("x * q.f(2 + u, -v)");
        assertThat(parser.getSyntaxErrors().size(), is(0));
        assertThat(expr, is(instanceOf(BinaryExpr.class)));
        BinaryExpr<?> binary = (BinaryExpr<?>)expr;
        assertThat(binary.getOperation(), is(BinaryOperation.MULTIPLY));
        assertThat(binary.getFirstOperand(), is(instanceOf(VariableExpr.class)));
        assertThat(((VariableExpr<?>)binary.getFirstOperand()).getName(), is("x"));
        assertThat(binary.getSecondOperand(), is(instanceOf(InvocationExpr.class)));
        InvocationExpr<?> invocation = (InvocationExpr<?>)binary.getSecondOperand();
        assertThat(invocation.getMethodName(), is("f"));
        assertThat(invocation.getArguments().size(), is(2));
        assertThat(invocation.getArguments().get(0), is(instanceOf(BinaryExpr.class)));
        assertThat(invocation.getArguments().get(1), is(instanceOf(UnaryExpr.class)));
    }
}
