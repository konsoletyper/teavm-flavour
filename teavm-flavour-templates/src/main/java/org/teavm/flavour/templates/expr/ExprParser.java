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

import org.parboiled.BaseParser;
import org.parboiled.Rule;

/**
 *
 * @author Alexey Andreev
 */
public class ExprParser extends BaseParser<Expr<Void>> {
    Rule expression() {
        return or();
    }

    Rule or() {
        return Sequence(and(), ZeroOrMore(keyword("or"), and()));
    }

    Rule and() {
        return Sequence(not(), ZeroOrMore(keyword("and"), not()));
    }

    Rule not() {
        return Sequence(OneOrMore(keyword("not")), comparison());
    }

    Rule comparison() {
        return Sequence(additive(), ZeroOrMore(comparisonKeyword(), additive()));
    }

    Rule comparisonKeyword() {
        return oneOfKeyword("==", "!=", "<", "<=", ">", ">=", "eq", "neq", "less", "leq", "gt", "geq");
    }

    Rule additive() {
        return Sequence(multiplicative(), ZeroOrMore(oneOfKeyword("+", "-"), additive()));
    }

    Rule multiplicative() {
        return Sequence(arithmetic(), ZeroOrMore(oneOfKeyword("*", "/", "%"), arithmetic()));
    }

    Rule arithmetic() {
        return Sequence(OneOrMore(oneOfKeyword("-", "+")), primitive());
    }

    Rule primitive() {
        return FirstOf(
                identifier(),
                number(),
                keyword("true"),
                keyword("false"),
                keyword("null"),
                invocation(),
                arraySubscript(),
                qualification(),
                Sequence(keyword("("), expression(), keyword(")")));
    }

    Rule qualification() {
        return Sequence(primitive(), keyword("."), identifier());
    }

    Rule invocation() {
        return Sequence(primitive(), keyword("."), identifier(), keyword("("), expressionList(), keyword(")"));
    }

    Rule expressionList() {
        return Sequence(expression(), ZeroOrMore(keyword(","), expression()));
    }

    Rule arraySubscript() {
        return Sequence(primitive(), keyword("["), expression(), keyword("]"));
    }

    Rule keyword(String delimiter) {
        return Sequence(String(delimiter), whitespace());
    }

    Rule oneOfKeyword(String... delimiters) {
        Rule[] rules = new Rule[delimiters.length];
        for (int i = 0; i < delimiters.length; ++i) {
            rules[i] = keyword(delimiters[i]);
        }
        return FirstOf(rules);
    }

    Rule identifier() {
        return Sequence(identifierStart(), ZeroOrMore(identifierPart()), whitespace());
    }

    Rule identifierStart() {
        return FirstOf(CharRange('A', 'Z'), CharRange('a', 'z'), Ch('_'));
    }

    Rule identifierPart() {
        return FirstOf(identifierStart(), CharRange('0', '9'));
    }

    Rule number() {
        return Sequence(FirstOf(intNumber(), fracNumber()), whitespace());
    }

    Rule intNumber() {
        return FirstOf(
                Ch('0'),
                Sequence(CharRange('1', '9'), digits()));
    }

    Rule fracNumber() {
        return FirstOf(
                Sequence(intNumber(), Ch('.'), digits(), Optional(exponent())),
                Sequence(intNumber(), exponent()));
    }

    Rule exponent() {
        return Sequence(IgnoreCase('e'), Optional(AnyOf("+-")), digits());
    }

    Rule digits() {
        return OneOrMore(digit());
    }

    Rule stringLiteral() {
        return Sequence(Ch('\''), ZeroOrMore(stringLiteralChar()), Ch('\''), whitespace());
    }

    Rule stringLiteralChar() {
        return FirstOf(
                TestNot(CharRange('\0', '\u001F'), AnyOf("'\\")),
                Sequence("\\", stringEscapeSequence()));
    }

    Rule stringEscapeSequence() {
        return FirstOf(
                AnyOf("rnt\\'"),
                Sequence(IgnoreCase('u'), NTimes(4, hexDigit())));
    }

    Rule hexDigit() {
        return FirstOf(digit(), CharRange('a', 'f'), CharRange('A', 'F'));
    }

    Rule digit() {
        return CharRange('0', '9');
    }

    Rule whitespace() {
        return ZeroOrMore(whitespaceChar());
    }

    Rule whitespaceChar() {
        return AnyOf(" \t\r\n");
    }
}
