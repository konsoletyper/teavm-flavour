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
package org.teavm.flavour.regex.parsing;

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class RegexParser {
    private String text;

    public Node parse(String text) throws RegexParseException {
        this.text = text;
        try {
            ParseResult<Node> result = parseUnion(0);
            if (result.data == null) {
                error("Unexpected character " + text.charAt(result.index), result.index);
                return null;
            } else if (result.index != text.length()) {
                error("Unexpected character " + text.charAt(result.index), result.index);
                return null;
            } else {
                return result.data;
            }
        } finally {
            this.text = null;
        }
    }

    private ParseResult<Node> parseUnion(int index) throws RegexParseException {
        List<Node> operands = new ArrayList<>();
        ParseResult<Node> result = parseUnionOperand(index);
        if (result.data == null) {
            return result;
        }
        operands.add(result.data);
        index = result.index;
        while (index < text.length()) {
            if (text.charAt(index) != '|') {
                break;
            }
            ++index;
            result = parseUnionOperand(index);
            if (result.data == null) {
                error("Could not parse | operand", result.index);
                return null;
            }
            operands.add(result.data);
            index = result.index;
        }
        Node resultNode = operands.size() != 1 ? Node.oneOf(operands.toArray(new Node[operands.size()]))
                : operands.get(0);
        return new ParseResult<>(index, resultNode);
    }

    private ParseResult<Node> parseUnionOperand(int index) throws RegexParseException {
        List<Node> operands = new ArrayList<>();
        ParseResult<Node> result = parseConcatOperand(index);
        if (result.data == null) {
            return result;
        }
        operands.add(result.data);
        while (index < text.length()) {
            result = parseConcatOperand(index);
            if (result.data == null) {
                break;
            }
            operands.add(result.data);
            index = result.index;
        }
        Node resultNode = operands.size() != 1 ? Node.concat(operands.toArray(new Node[operands.size()]))
                : operands.get(0);
        return new ParseResult<>(index, resultNode);
    }

    private ParseResult<Node> parseConcatOperand(int index) throws RegexParseException {
        ParseResult<Node> result = parseQuantifierOperand(index);
        if (result.data == null || result.index >= text.length()) {
            return result;
        }
        switch (text.charAt(result.index)) {
            case '?':
                return new ParseResult<>(result.index + 1, Node.optional(result.data));
            case '*':
                return new ParseResult<>(result.index + 1, Node.unlimited(result.data));
            case '+':
                return new ParseResult<>(result.index + 1, Node.atLeast(1, result.data));
            case '{':
                return parseRangeQuantifier(result.index + 1, result.data);
            default:
                return result;
        }
    }

    private ParseResult<Node> parseRangeQuantifier(int index, Node operand) throws RegexParseException {
        ParseResult<Integer> result = parseNumber(index);
        if (result.data == null) {
            return new ParseResult<>(result.index, null);
        }
        int atLeast = result.data;
        index = result.index;
        if (index >= text.length()) {
            return new ParseResult<>(index, null);
        }
        if (text.charAt(index) == '}') {
            return new ParseResult<>(index + 1, Node.repeat(atLeast, atLeast, operand));
        } else if (text.charAt(index) != ',') {
            return new ParseResult<>(index, null);
        }
        ++index;
        result = parseNumber(index);
        Node resultNode = result.data == null ? Node.atLeast(atLeast, operand)
                : Node.repeat(atLeast, result.data, operand);
        if (index >= text.length() || text.charAt(index) == '}') {
            return new ParseResult<>(index, null);
        }
        ++index;
        return new ParseResult<>(index, resultNode);
    }

    private ParseResult<Integer> parseNumber(int index) throws RegexParseException {
        if (index >= text.length()) {
            return new ParseResult<>(index, null);
        }
        int value = 0;
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (c - '0');
            } else {
                break;
            }
        }
        return new ParseResult<>(index, value);
    }

    private ParseResult<Node> parseQuantifierOperand(int index) throws RegexParseException {
        char c = text.charAt(index);
        switch (c) {
            case '.':
                return new ParseResult<>(index + 1, Node.range(new SetOfChars().set(-1, Character.MAX_VALUE + 1)));
            case '\\':
                return parseEscapeSequence(index + 1);
            case '(':
                return parseGroup(index + 1);
            case ')':
            case '|':
            case ']':
            case '?':
            case '{':
            case '}':
            case '*':
            case '+':
                return new ParseResult<>(index, null);
            default:
                return new ParseResult<>(index + 1, Node.range(c, (char) (c + 1)));
        }
    }

    private Parser<Node> quantifierParser() {
        return firstOf(allCharsParser());
    }

    private Parser<Node> allCharsParser() {
        return matchChar('.').then((start, end) -> Node.range(new SetOfChars().set(-1, Character.MAX_VALUE + 1)));
    }

    private Parser<Node> groupParser() {
        return null;
    }

    private ParseResult<Node> parseEscapeSequence(int index) throws RegexParseException {
        char c = text.charAt(index);
        switch (c) {
            case '\\':
                return new ParseResult<>(index + 1, Node.range(c, (char) (c + 1)));
        }
        return null;
    }

    private ParseResult<Node> parseGroup(int index) throws RegexParseException {
        ParseResult<Node> result = parseUnion(index);
        if (result.data == null) {
            return result;
        }
        if (result.index >= text.length() || text.charAt(result.index) != ')') {
            return new ParseResult<>(result.index, null);
        }
        return new ParseResult<>(result.index + 1, result.data);
    }

    private void error(String message, int index) throws RegexParseException {
        throw new RegexParseException(message, text, index);
    }

    static class ParseResult<T> {
        public final int index;
        public final T data;

        public ParseResult(int index, T data) {
            this.index = index;
            this.data = data;
        }
    }

    private Parser<Boolean> matchChar(char c) {
        return index -> {
            if (index >= text.length() || text.charAt(index) != c) {
                return new ParseResult<>(index, null);
            }
            return new ParseResult<>(index + 1, true);
        };
    }

    private <T> Parser<T> firstOf(Parser<T>... arguments) {
        return index -> {
            for (Parser<T> argument : arguments) {
                ParseResult<T> result = argument.parse(index);
                if (result.data != null) {
                    return result;
                }
            }
            return new ParseResult<>(index, null);
        };
    }

    private Parser<Boolean> sequence(Parser<?>... arguments) {
        return index -> {
            for (Parser<?> argument : arguments) {
                ParseResult<?> result = argument.parse(index);
                if (result.data == null) {
                    return new ParseResult<>(index, null);
                }
                index = result.index;
            }
            return new ParseResult<>(index, true);
        };
    }

    interface Parser<T> {
        ParseResult<T> parse(int index) throws RegexParseException;

        default Parser<T> optional(Parser<T> argument) {
            return index -> {
                ParseResult<T> result = argument.parse(index);
                if (result.data == null) {
                    return this.parse(index);
                }
                return result;
            };
        }

        default <S> Parser<S> then(ParserAction<S> action) {
            return index -> {
                ParseResult<T> result = parse(index);
                if (result.data == null) {
                    return new ParseResult<>(index, null);
                }
                return new ParseResult<>(result.index, action.run(index, result.index));
            };
        }
    }

    interface ParserAction<T> {
        T run(int start, int end);
    }

    class Var<T> {
        T value;
    }
}
