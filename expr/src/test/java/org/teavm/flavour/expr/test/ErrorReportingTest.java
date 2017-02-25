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
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import java.util.List;
import org.junit.Test;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.EvaluatorBuilder;
import org.teavm.flavour.expr.InterpretingEvaluatorBuilder;
import org.teavm.flavour.expr.InvalidExpressionException;

/**
 *
 * @author Alexey Andreev
 */
public class ErrorReportingTest extends BaseEvaluatorTest {
    @Test
    public void reportsUndefinedVariable() {
        Diagnostic d = parseExpr(IntComputation.class, " wrongVar").get(0);
        assertThat(d.getMessage(), is("Variable wrongVar was not found"));
        assertThat(d.getStart(), is(1));
        assertThat(d.getEnd(), is(9));
    }

    @Test
    public void reportsArithmeticError() {
        List<Diagnostic> list = parseExpr(IntComputation.class, "object + foo");
        assertThat(list.get(0).getMessage(), is("Invalid operand type: java.lang.Object"));
        assertThat(list.get(0).getStart(), is(0));
        assertThat(list.get(0).getEnd(), is(6));
        assertThat(list.get(1).getMessage(), is("Invalid operand type: " +
                "org.teavm.flavour.expr.test.BaseEvaluatorTest$Foo"));
        assertThat(list.get(1).getStart(), is(9));
        assertThat(list.get(1).getEnd(), is(12));
    }

    @Test
    public void reportsConversionError() {
        Diagnostic d = parseExpr(IntComputation.class, "stringValue").get(0);
        assertThat(d.getMessage(), is("Can't convert java.lang.String to int"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(11));
    }

    @Test
    public void reportsArithmeticConversionError() {
        Diagnostic d = parseExpr(ByteComputation.class, "intValue").get(0);
        assertThat(d.getMessage(), is("Can't convert int to byte"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(8));

        d = parseExpr(BooleanComputation.class, "byteValue").get(0);
        assertThat(d.getMessage(), is("Can't convert byte to boolean"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(9));
    }

    @Test
    public void reportsCastError() {
        Diagnostic d = parseExpr(ByteComputation.class, "(byte)object").get(0);
        assertThat(d.getMessage(), is("Can't cast java.lang.Object to byte"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(12));
    }

    @Test
    public void reportsGenericConversionError() {
        Diagnostic d = parseExpr(StringComputation.class, "stringIntMap['k']").get(0);
        assertThat(d.getMessage(), is("Can't convert java.lang.Integer to java.lang.String"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(17));
    }

    @Test
    public void reportsSubscriptArrayError() {
        Diagnostic d = parseExpr(StringComputation.class, "object[0]").get(0);
        assertThat(d.getMessage(), is("Can't apply subscript operator to java.lang.Object with argument of int"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(9));

        d = parseExpr(StringComputation.class, "intValue[object]").get(0);
        assertThat(d.getMessage(), is("Can't apply subscript operator to int with argument of java.lang.Object"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(16));
    }

    @Test
    public void reportsSubscriptIndexError() {
        Diagnostic d = parseExpr(StringComputation.class, "stringArray[object]").get(0);
        assertThat(d.getMessage(), is("Can't convert java.lang.Object to int"));
        assertThat(d.getStart(), is(12));
        assertThat(d.getEnd(), is(18));
    }

    @Test
    public void reportsInvalidMethodArgumentsError() {
        Diagnostic d = parseExpr(StringComputation.class, "foo.extract(stringIntMap, object)").get(0);
        assertThat(d.getMessage(), is("Method extract(java.util.Map<K, V>, K) is not applicable to "
                + "(java.util.Map<java.lang.String, java.lang.Integer>, java.lang.Object)"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(33));
    }

    @Test
    public void reportsMissingMethod() {
        Diagnostic d = parseExpr(StringComputation.class, "stringValue.doSomethingFancy()").get(0);
        assertThat(d.getMessage(), is("Method not found: doSomethingFancy"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(30));
    }

    @Test
    public void reportsAmbigousMethod() {
        Diagnostic d = parseExpr(StringComputation.class, "foo.bar(null)").get(0);
        assertThat(d.getMessage(), startsWith("Ambigous method invocation bar(?)"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(13));
    }

    @Test
    public void reportsStaticCallOnInstance() {
        Diagnostic d = parseExpr(StringComputation.class, "foo.staticMethod()").get(0);
        assertThat(d.getMessage(), is("Method should be called as a static method: staticMethod()"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(18));
    }

    @Test
    public void reportsVirtualCallOnClass() {
        Diagnostic d = parseExpr(StringComputation.class, "String.toUpperCase()").get(0);
        assertThat(d.getMessage(), is("Method should be called as an instance method: toUpperCase()"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(20));
    }

    @Test
    public void reportsPropertyAccessOnPrimitive() {
        Diagnostic d = parseExpr(StringComputation.class, "intValue.foo").get(0);
        assertThat(d.getMessage(), is("Property foo was not found"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(12));
    }

    @Test
    public void reportsWrongPropertyAccess() {
        Diagnostic d = parseExpr(StringComputation.class, "stringValue.fancyProperty").get(0);
        assertThat(d.getMessage(), is("Property fancyProperty was not found"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(25));
    }

    @Test
    public void reportsInstancePropertyAccessFromClass() {
        Diagnostic d = parseExpr(StringComputation.class, "Class.primitive").get(0);
        assertThat(d.getMessage(), is("Method isPrimitive should be static"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(15));
    }

    @Test
    public void reportsStaticPropertyAccessFromInstance() {
        Diagnostic d = parseExpr(StringComputation.class, "foo.staticProperty").get(0);
        assertThat(d.getMessage(), is("Method getStaticProperty should not be static"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(18));
    }

    public String instanceField;

    @Test
    public void reportsInstanceFieldAccessFromClass() {
        Diagnostic d = parseExpr(StringComputation.class, "ErrorReportingTest.instanceField").get(0);
        assertThat(d.getMessage(), is("Field instanceField should be static"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(32));
    }

    @Test
    public void reportsStaticFieldAccessFromInstance() {
        Diagnostic d = parseExpr(StringComputation.class, "stringValue.CASE_INSENSITIVE_ORDER").get(0);
        assertThat(d.getMessage(), is("Field CASE_INSENSITIVE_ORDER should not be static"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(34));
    }

    @Test
    public void reportsInstanceOfPrimitive() {
        Diagnostic d = parseExpr(StringComputation.class, "intValue instanceof Integer").get(0);
        assertThat(d.getMessage(), is("Can't check against java.lang.Integer"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(27));
    }

    @Test
    public void reportsUnknownClass() {
        Diagnostic d = parseExpr(StringComputation.class, "intValue instanceof java.lang.FancyClass").get(0);
        assertThat(d.getMessage(), is("Unknown class java.lang.FancyClass"));
        assertThat(d.getStart(), is(20));
        assertThat(d.getEnd(), is(40));
    }

    @Test
    public void reportsIncompatibleTernary() {
        Diagnostic d = parseExpr(StringComputation.class, "object == null ? stringValue : foo").get(0);
        assertThat(d.getMessage(), is("Can't convert java.lang.Object to java.lang.String"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(34));
    }

    @Test
    public void reportsIncompatibleTernary2() {
        Diagnostic d = parseExpr(StringComputation.class, "object == null ? true : intValue").get(0);
        assertThat(d.getMessage(), is("Clauses of ternary conditional operator are not compatible: boolean vs. int"));
        assertThat(d.getStart(), is(0));
        assertThat(d.getEnd(), is(32));
    }

    private List<Diagnostic> parseExpr(Class<?> cls, String str) {
        EvaluatorBuilder builder = new InterpretingEvaluatorBuilder()
                .importPackage("java.lang")
                .importPackage("java.util")
                .importClass(ErrorReportingTest.class.getName());
        try {
            builder.build(cls, TestVars.class, str);
            fail("This expression is meant to contain errors: " + str);
            return null;
        } catch (InvalidExpressionException e) {
            return e.getDiagnostics();
        }
    }
}
