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
package org.teavm.flavour.templates.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.parboiled.Action;
import org.parboiled.BaseParser;
import org.parboiled.Context;
import org.parboiled.Rule;
import org.parboiled.support.Var;
import org.teavm.flavour.templates.expr.ast.*;

/**
 *
 * @author Alexey Andreev
 */
class ExprParser extends BaseParser<Expr<Void>> {
    private static final double[] positiveExponents = { 1E1, 1E2, 1E4, 1E8, 1E16, 1E32, 1E64, 1E128 };
    private static final double[] negativeExponents = { 1E-1, 1E-2, 1E-4, 1E-8, 1E-16, 1E-32, 1E-64, 1E-128 };
    ClassSet importedClasses;

    Rule Expression() {
        return Or();
    }

    Rule Or() {
        return Sequence(And(), ZeroOrMore(Keyword("or"), And(),
                swap(), push(new BinaryExpr<>(pop(), pop(), BinaryOperation.OR)), setLocations));
    }

    Rule And() {
        return Sequence(Not(), ZeroOrMore(Keyword("and"), Not(),
                swap(), push(new BinaryExpr<>(pop(), pop(), BinaryOperation.AND)), setLocations));
    }

    Rule Not() {
        return FirstOf(
                Comparison(),
                Sequence(Keyword("not"), Not(), push(new UnaryExpr<>(pop(), UnaryOperation.NOT)), setLocations));
    }

    Rule Comparison() {
        Var<BinaryOperation> op = new Var<>();
        return Sequence(Additive(), ZeroOrMore(
                FirstOf(
                    Sequence(Keyword("=="), op.set(BinaryOperation.EQUAL)),
                    Sequence(Keyword("!="), op.set(BinaryOperation.NOT_EQUAL)),
                    Sequence(OneOfKeyword("<", "less"), op.set(BinaryOperation.LESS)),
                    Sequence(OneOfKeyword("<=", "loe"), op.set(BinaryOperation.LESS_OR_EQUAL)),
                    Sequence(OneOfKeyword(">", "greater"), op.set(BinaryOperation.GREATER)),
                    Sequence(OneOfKeyword(">=", "goe"), op.set(BinaryOperation.GREATER_OR_EQUAL))),
                Additive(),
                swap(), push(new BinaryExpr<>(pop(), pop(), op.get())), setLocations));
    }

    Rule Additive() {
        Var<BinaryOperation> op = new Var<>();
        return Sequence(Multiplicative(), ZeroOrMore(
                FirstOf(
                    Sequence(Keyword("+"), op.set(BinaryOperation.ADD)),
                    Sequence(Keyword("-"), op.set(BinaryOperation.SUBTRACT))),
                Additive(),
                swap(), push(new BinaryExpr<>(pop(), pop(), op.get())), setLocations));
    }

    Rule Multiplicative() {
        Var<BinaryOperation> op = new Var<>();
        return Sequence(Arithmetic(), ZeroOrMore(
                FirstOf(
                    Sequence(Keyword("*"), op.set(BinaryOperation.MULTIPLY)),
                    Sequence(Keyword("/"), op.set(BinaryOperation.DIVIDE)),
                    Sequence(Keyword("%"), op.set(BinaryOperation.REMAINDER))),
                Arithmetic(),
                swap(), push(new BinaryExpr<>(pop(), pop(), op.get())), setLocations));
    }

    Rule Arithmetic() {
        return FirstOf(
                Path(),
                Sequence(Keyword("-"), Arithmetic(), push(new UnaryExpr<>(pop(), UnaryOperation.NEGATE)),
                        setLocations));
    }

    Rule Path() {
        return Sequence(Primitive(), ZeroOrMore(Navigation()));
    }

    Rule Primitive() {
        return FirstOf(
                Number(),
                Identifier(),
                Sequence(Keyword("true"), push(new ConstantExpr<Void>(true)), setLocations),
                Sequence(Keyword("false"), push(new ConstantExpr<Void>(false)), setLocations),
                Sequence(Keyword("null"), push(new ConstantExpr<Void>(true)), setLocations),
                Sequence(Keyword("("), Expression(), Keyword(")")));
    }

    Rule Navigation() {
        return FirstOf(Qualification(), ArraySubscript());
    }

    Rule Qualification() {
        Var<Expr<Void>> instance = new Var<>();
        Var<String> property = new Var<>();
        Var<List<Expr<Void>>> list = new Var<>();
        return Sequence(list.set(null), instance.set(pop()), Keyword("."), Identifier(property),
                Optional(Keyword("("), list.set(new ArrayList<Expr<Void>>()), ExpressionList(list), Keyword(")")),
                qualify(instance, property, list), setLocations);
    }

    Rule ExpressionList(Var<List<Expr<Void>>> list) {
        return Optional(Sequence(Expression(), append(list), ZeroOrMore(Keyword(","), Expression(), append(list))));
    }

    Rule ArraySubscript() {
        Var<Expr<Void>> array = new Var<>();
        Var<Expr<Void>> index = new Var<>();
        return Sequence(
                array.set(pop()),
                Keyword("["), Expression(), index.set(pop()), Keyword("]"),
                push(new BinaryExpr<>(array.get(), index.get(), BinaryOperation.GET_ELEMENT)),
                setLocations);
    }

    Rule Keyword(String delimiter) {
        return Sequence(String(delimiter), Whitespace());
    }

    Rule OneOfKeyword(String... delimiters) {
        Rule[] rules = new Rule[delimiters.length];
        for (int i = 0; i < delimiters.length; ++i) {
            rules[i] = Keyword(delimiters[i]);
        }
        return FirstOf(rules);
    }

    Rule Identifier() {
        Var<String> id = new Var<>();
        return Sequence(Identifier(id), push(new VariableExpr<Void>(id.get())), setLocations);
    }

    Rule Identifier(Var<String> id) {
        return Sequence(
                Sequence(IdentifierStart(), ZeroOrMore(IdentifierPart())), id.set(match()),
                Whitespace());
    }

    Rule IdentifierStart() {
        return FirstOf(CharRange('A', 'Z'), CharRange('a', 'z'), Ch('_'));
    }

    Rule IdentifierPart() {
        return FirstOf(IdentifierStart(), CharRange('0', '9'));
    }

    Rule Number() {
        return Sequence(FirstOf(IntNumber(), FracNumber()), Whitespace());
    }

    Rule IntNumber() {
        final Var<String> digits = new Var<>();
        Action<Expr<Void>> action = new Action<Expr<Void>>() {
            @Override public boolean run(Context<Expr<Void>> context) {
                try {
                    int value = Integer.parseInt(digits.get());
                    context.getValueStack().push(new ConstantExpr<Void>(value));
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        };
        return Sequence(IntNumber(digits), action, setLocations);
    }

    Rule IntNumber(Var<String> s) {
        Var<String> firstDigit = new Var<>();
        return FirstOf(
                Sequence(Ch('0'), s.set("0")),
                Sequence(CharRange('1', '9'), s.set(match()),
                        Optional(firstDigit.set(s.get()), Digits(s), s.set(firstDigit.get() + match()))));
    }

    Rule FracNumber() {
        final Var<String> intPart = new Var<>();
        final Var<String> fracPart = new Var<>();
        final Var<Character> exponentSign = new Var<>();
        final Var<String> exponent = new Var<>();
        Action<Expr<Void>> action = new Action<Expr<Void>>() {
            @Override public boolean run(Context<Expr<Void>> context) {
                return stringToDouble(context, intPart.get(), fracPart.get(), exponentSign.get(), exponent.get());
            }
        };
        return Sequence(FirstOf(
                Sequence(IntNumber(intPart), Ch('.'), Digits(fracPart), Optional(Exponent(exponent, exponentSign))),
                Sequence(IntNumber(intPart), Exponent(exponent, exponentSign))),
                action, setLocations);
    }

    Rule Exponent(Var<String> s, Var<Character> sign) {
        return Sequence(IgnoreCase('e'), Optional(AnyOf("+-")), sign.set(currentChar()), Digits(s));
    }

    Rule Digits(Var<String> s) {
        return Sequence(OneOrMore(Digit()), s.set(match()));
    }

    Rule StringLiteral() {
        Var<StringBuilder> sb = new Var<>();
        Var<Character> ch = new Var<>();
        return Sequence(Ch('\''),
                ZeroOrMore(StringLiteralChar(ch), append(sb, ch)),
                Ch('\''),
                push(new ConstantExpr<Void>(sb.get().toString())),
                setLocations,
                Whitespace());
    }

    Rule StringLiteralChar(Var<Character> ch) {
        return FirstOf(
                Sequence(TestNot(CharRange('\0', '\u001F'), AnyOf("'\\")), ch.set(currentChar())),
                Sequence("\\", StringEscapeSequence(ch)));
    }

    Rule StringEscapeSequence(Var<Character> ch) {
        return FirstOf(
                Sequence(Ch('r'), ch.set('\r')),
                Sequence(Ch('n'), ch.set('\n')),
                Sequence(Ch('t'), ch.set('\t')),
                Sequence(Ch('\''), ch.set('\'')),
                Sequence(Ch('\\'), ch.set('\\')),
                Sequence(IgnoreCase('u'), NTimes(4, HexDigit()), ch.set((char)Integer.parseInt(match(), 16))));
    }

    Rule HexDigit() {
        return FirstOf(Digit(), CharRange('a', 'f'), CharRange('A', 'F'));
    }

    Rule Digit() {
        return CharRange('0', '9');
    }

    Rule Whitespace() {
        return ZeroOrMore(WhitespaceChar());
    }

    Rule WhitespaceChar() {
        return AnyOf(" \t\r\n");
    }

    private boolean stringToDouble(Context<Expr<Void>> context,  String integer, String fractional,
            Character exponentSign, String exponent) {
        StringBuilder digits = new StringBuilder(integer);
        if (fractional != null) {
            digits.append(fractional);
        }
        int exponentNum;
        try {
            exponentNum = Integer.parseInt(exponent);
        } catch (NumberFormatException e) {
            return false;
        }
        if (exponentNum > 308) {
            return false;
        }
        if (exponentSign != null && exponentSign.charValue() == '-') {
            exponentNum = -exponentNum;
        }
        exponentNum += integer.length();
        if (digits.length() > 16) {
            digits.setLength(16);
        } else {
            while (digits.length() < 16) {
                digits.append(' ');
            }
        }
        long mantissa = Long.parseLong(digits.toString());
        context.getValueStack().push(new ConstantExpr<Void>(fastExponent(mantissa, exponentNum - 16)));
        return true;
    }

    private double fastExponent(long mantissa, int exponent) {
        double result = mantissa;
        double[] exponents = exponent >= 0 ? positiveExponents : negativeExponents;
        int bit = 1;
        for (int i = 0; i < exponents.length; ++i) {
            if ((exponent |= bit) != 0) {
                result *= exponents[i];
            }
            bit <<= 1;
        }
        return result;
    }

    Action<Expr<Void>> setLocations = new Action<Expr<Void>>() {
        @Override
        public boolean run(Context<Expr<Void>> context) {
            Expr<Void> expr = context.getValueStack().peek();
            if (expr != null) {
                expr.setStart(context.getMatchStartIndex());
                expr.setEnd(context.getMatchEndIndex());
            }
            return true;
        }
    };

    Action<Expr<Void>> append(final Var<StringBuilder> sb, final Var<Character> ch) {
        return new Action<Expr<Void>>() {
            @Override
            public boolean run(Context<Expr<Void>> context) {
                sb.get().append(ch.get().charValue());
                return true;
            }
        };
    }

    Action<Expr<Void>> qualify(final Var<Expr<Void>> instanceVar, final Var<String> propertyVar,
            final Var<List<Expr<Void>>> argumentsVar) {
        return new Action<Expr<Void>>() {
            @Override
            public boolean run(Context<Expr<Void>> context) {
                Expr<Void> instance = instanceVar.get();
                String className = isClassName(instance);
                if (argumentsVar.get() == null) {
                    if (className == null) {
                        context.getValueStack().push(new PropertyExpr<>(instance, propertyVar.get()));
                    } else {
                        context.getValueStack().push(new StaticPropertyExpr<Void>(className, propertyVar.get()));
                    }
                } else {
                    if (className == null) {
                        context.getValueStack().push(new InvocationExpr<>(instance, propertyVar.get(),
                                argumentsVar.get()));
                    } else {
                        context.getValueStack().push(new StaticInvocationExpr<>(className, propertyVar.get(),
                                argumentsVar.get()));
                    }
                }
                return true;
            }
        };
    }

    Action<Expr<Void>> append(final Var<List<Expr<Void>>> list) {
        return new Action<Expr<Void>>() {
            @Override
            public boolean run(Context<Expr<Void>> context) {
                list.get().add(context.getValueStack().pop());
                return true;
            }
        };
    }

    private String isClassName(Expr<Void> expr) {
        List<String> parts = new ArrayList<>();
        while (expr instanceof PropertyExpr<?>) {
            PropertyExpr<Void> property = (PropertyExpr<Void>)expr;
            parts.add(property.getPropertyName());
            expr = property.getInstance();
        }
        if (!(expr instanceof VariableExpr<?>)) {
            return null;
        }
        VariableExpr<Void> root = (VariableExpr<Void>)expr;
        parts.add(root.getName());
        Collections.reverse(parts);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); ++i) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i));
        }
        return importedClasses.findClass(sb.toString());
    }
}
