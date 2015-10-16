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

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class RegexParser {
    private String text;
    private int index;

    public Node parse(String text) throws RegexParseException {
        this.text = text;
        try {
            Optional<Node> result = parseUnion();
            if (!result.isPresent() || index != text.length()) {
                throw new RegexParseException("Unexpected character", text, index);
            }
            return result.get();
        } catch (ParseException e) {
            throw new RegexParseException("Unexpected character" , text, index);
        } finally {
            this.text = null;
        }
    }

    private Optional<Node> parseUnion() {
        return parseConcat().map(node -> {
            while (attempt(() -> matchChar('|'))) {
                node = Node.oneOf(node, require(() -> parseConcat()));
            }
            return node;
        });
    }

    private Optional<Node> parseConcat() {
        return parseQuantifier().map(node -> {
            while (true) {
                Optional<Node> next = attempt(() -> parseQuantifier());
                if (!next.isPresent()) {
                    break;
                }
                node = Node.concat(node, next.get());
            }
            return node;
        });
    }

    private Optional<Node> parseQuantifier() {
        return parseTerm().map(node -> {
            loop: while (true) {
                if (index >= text.length()) {
                    break;
                }
                switch (text.charAt(index)) {
                    case '?':
                        ++index;
                        node = Node.optional(node);
                        break;
                    case '*':
                        ++index;
                        node = Node.unlimited(node);
                        break;
                    case '+':
                        ++index;
                        node = Node.atLeast(1, node);
                        break;
                    case '{':
                        ++index;
                        node = parseCustomQuantifier(node);
                        break;
                    default:
                        break loop;
                }
            }
            return node;
        });
    }

    private Node parseCustomQuantifier(Node node) {
        int min = require(() -> parseInteger());
        if (attempt(() -> matchChar(','))) {
            Optional<Integer> max = attempt(() -> parseInteger());
            return max.isPresent() ? Node.repeat(min, max.get(), node) : Node.atLeast(min, node);
        }
        return Node.repeat(min, min, node);

    }

    private Optional<Integer> parseInteger() {
        if (!attempt(() -> matchRange('0', '9'))) {
            return Optional.empty();
        }
        int num = text.charAt(index - 1) - '0';
        while (matchRange('0', '9')) {
            num = num * 10 + text.charAt(index - 1) - '0';
        }
        return Optional.of(num);
    }

    private Optional<Node> parseTerm() {
        if (index >= text.length()) {
            return Optional.empty();
        }
        char c = text.charAt(index);
        switch (c) {
            case '.':
                ++index;
                return Optional.of(Node.range(new SetOfChars().set(0, Character.MAX_VALUE + 1)));
            case '(':
                ++index;
                return Optional.of(parseGroup());
            case '[':
                ++index;
                return Optional.of(Node.range(parseCharRange()));
            case '\\': {
                ++index;
                SetOfChars set = new SetOfChars();
                parseEscapeSequence(set);
                return Optional.of(Node.range(set));
            }
            case ')':
            case '|':
            case ']':
            case '?':
            case '{':
            case '}':
            case '*':
            case '+':
                return Optional.empty();
            default:
                ++index;
                return Optional.of(Node.character(c));
        }
    }

    private Node parseGroup() {
        Node result = require(() -> parseUnion());
        require(() -> matchChar(')'));
        return result;
    }

    private SetOfChars parseCharRange() {
        boolean inverted = false;
        SetOfChars set = new SetOfChars();

        if (attempt(() -> matchChar('^'))) {
            inverted = true;
        }
        loop: while (index < text.length()) {
            switch (text.charAt(index)) {
                case '\\':
                    ++index;
                    parseEscapeSequence(set);
                    break;
                case '[':
                    ++index;
                    set.uniteWith(parseCharRange());
                    break;
                case ']':
                    break loop;
                default:
                    parseRange(set);
                    break;
            }
        }

        require(() -> matchChar(']'));
        return !inverted ? set : excluding(set);
    }

    private void parseRange(SetOfChars set) {
        char min = text.charAt(index++);
        if (attempt(() -> matchChar('-'))) {
            if (index == text.length()) {
                throw new ParseException(index);
            }
            char max = text.charAt(index);
            switch (max) {
                case ']':
                case '[':
                case '^':
                    throw new ParseException(index);
            }
            ++index;
            set.set(min, max + 1);
        } else {
            set.set(min);
        }
    }

    private void parseEscapeSequence(SetOfChars set) {
        if (index >= text.length()) {
            throw new ParseException(index);
        }
        char c = text.charAt(index);
        switch (c) {
            case 't':
                set.set('\t');
                break;
            case 'n':
                set.set('\n');
                break;
            case 'b':
                set.set('\b');
                break;
            case 'f':
                set.set('\f');
                break;
            case 'e':
                set.set('\u001F');
                break;
            case 'd':
                set.set('0', '9' + 1);
                break;
            case 'D':
                set.set(0, '0').set('9' + 1);
                break;
            case 's':
                set.set(' ').set('\t').set('\n').set('\f').set('\r').set('\u000B');
                break;
            case 'S':
                set.uniteWith(excluding(new SetOfChars().set(' ').set('\t').set('\n').set('\f').set('\r')
                        .set('\u000B')));
                break;
            case 'w':
                set.set('a', 'z' + 1).set('A', 'Z' + 1).set('0', '9' + 1);
                break;
            case 'W':
                set.uniteWith(excluding(new SetOfChars().set('a', 'z' + 1).set('A', 'Z' + 1).set('0', '9' + 1)));
                break;
            case '(':
            case ')':
            case '|':
            case '+':
            case '*':
            case '[':
            case ']':
            case '\\':
            case '-':
            case '^':
            case '$':
                set.set(c);
                break;
            default:
                --index;
                throw new ParseException(index);
        }
    }

    private static SetOfChars excluding(SetOfChars chars) {
        chars.invert(-1, Character.MAX_VALUE + 1);
        return chars;
    }

    private boolean matchChar(char c) {
        boolean result = index < text.length() && text.charAt(index) == c;
        if (result) {
            ++index;
        }
        return result;
    }

    private boolean matchRange(char a, char b) {
        boolean result = index < text.length() && text.charAt(index) >= a && text.charAt(index) <= b;
        if (result) {
            ++index;
        }
        return result;
    }

    private <T> Optional<T> attempt(Supplier<Optional<T>> supplier) {
        int indexBackup = index;
        Optional<T> result = supplier.get();
        if (!result.isPresent()) {
            index = indexBackup;
        }
        return result;
    }

    private <T> T require(Supplier<Optional<T>> supplier) {
        Optional<T> result = attempt(supplier);
        if (!result.isPresent()) {
            throw new ParseException(index);
        }
        return result.get();
    }

    private void require(BooleanSupplier supplier) {
        if (!attempt(supplier)) {
            throw new ParseException(index);
        }
    }

    private <T> boolean attempt(BooleanSupplier supplier) {
        int indexBackup = index;
        boolean result = supplier.getAsBoolean();
        if (!result) {
            index = indexBackup;
        }
        return result;
    }

    static class ParseException extends RuntimeException {
        private static final long serialVersionUID = 419713497576180925L;
        private int index;

        public ParseException(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}
