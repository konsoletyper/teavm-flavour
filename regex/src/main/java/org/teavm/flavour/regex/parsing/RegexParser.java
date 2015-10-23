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

    public Node parse(String text, int start, char terminalChar) throws RegexParseException {
        index = start;
        this.text = text;
        try {
            Optional<Node> result = parseUnion();
            if (!result.isPresent()) {
                throw new RegexParseException("Unexpected character at " + index, text, index);
            }
            if (index != text.length()) {
                throw new RegexParseException("Unexpected end of string", text, index);
            } else if (text.charAt(index) != terminalChar) {
                throw new RegexParseException("Unexpected character at " + index, text, index);
            }
            return result.get();
        } finally {
            this.text = null;
        }
    }

    public int getIndex() {
        return index;
    }

    public Node parse(String text) throws RegexParseException {
        index = 0;
        this.text = text;
        try {
            Optional<Node> result = parseUnion();
            if (!result.isPresent() || index != text.length()) {
                throw new RegexParseException("Unexpected character at " + index, text, index);
            }
            return result.get();
        } finally {
            this.text = null;
        }
    }

    private Optional<Node> parseUnion() {
        return parseConcat().map(head -> {
            List<Node> nodes = new ArrayList<>();
            nodes.add(head);
            while (attempt(() -> matchChar('|'))) {
                nodes.add(require(() -> parseConcat()));
            }
            return nodes.size() == 1 ? head : Node.oneOf(nodes.toArray(new Node[nodes.size()]));
        });
    }

    private Optional<Node> parseConcat() {
        return parseQuantifier().map(head -> {
            List<Node> nodes = new ArrayList<>();
            nodes.add(head);
            while (true) {
                Optional<Node> next = attempt(() -> parseQuantifier());
                if (!next.isPresent()) {
                    break;
                }
                nodes.add(next.get());
            }
            return nodes.size() == 1 ? head : Node.concat(nodes.toArray(new Node[nodes.size()]));
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
        try {
            if (attempt(() -> matchChar(','))) {
                Optional<Integer> max = attempt(() -> parseInteger());
                return max.isPresent() ? Node.repeat(min, max.get(), node) : Node.atLeast(min, node);
            }
            return Node.repeat(min, min, node);
        } finally {
            require(() -> matchChar('}'));
        }
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
        SetOfChars lower = new SetOfChars();
        parseSingleChar(lower);
        int[] lowerIndexes = lower.getToggleIndexes();
        if (lowerIndexes.length != 2 || lowerIndexes[0] + 1 < lowerIndexes[1]) {
            set.uniteWith(lower);
            return;
        }

        char min = (char) lowerIndexes[0];
        if (attempt(() -> matchChar('-'))) {
            if (index == text.length()) {
                throw new RegexParseException("Unexpected end of input", text, index);
            }
            SetOfChars upper = new SetOfChars();
            parseSingleChar(upper);
            int[] upperIndexes = upper.getToggleIndexes();
            if (upperIndexes.length != 2 || upperIndexes[0] + 1 < upperIndexes[1]) {
                throw new RegexParseException("Only single-char escape sequences are allowed here", text, index);
            }
            char max = (char) upperIndexes[0];
            set.set(min, max + 1);
        } else {
            set.set(min);
        }
    }

    private void parseSingleChar(SetOfChars set) {
        if (index >= text.length()) {
            throw new RegexParseException("Unexpected end of input", text, index);
        }
        char c = text.charAt(index);
        switch (c) {
            case ']':
            case '[':
            case '^':
                throw new RegexParseException("Unexpected special char " + c, text, index);
            case '\\':
                ++index;
                parseEscapeSequence(set);
                break;
            default:
                ++index;
                set.set(c);
                break;
        }
    }

    private void parseEscapeSequence(SetOfChars set) {
        if (index >= text.length()) {
            throw new RegexParseException("Unexpected end of input", text, index);
        }
        char c = text.charAt(index);
        switch (c) {
            case 't':
                ++index;
                set.set('\t');
                break;
            case 'r':
                ++index;
                set.set('\r');
                break;
            case 'n':
                ++index;
                set.set('\n');
                break;
            case 'b':
                ++index;
                set.set('\b');
                break;
            case 'f':
                ++index;
                set.set('\f');
                break;
            case 'e':
                ++index;
                set.set('\u001F');
                break;
            case 'd':
                ++index;
                set.set('0', '9' + 1);
                break;
            case 'D':
                ++index;
                set.set(0, '0').set('9' + 1, Character.MAX_VALUE + 1);
                break;
            case 's':
                ++index;
                set.set(' ').set('\t').set('\n').set('\f').set('\r').set('\u000B');
                break;
            case 'S':
                ++index;
                set.uniteWith(excluding(new SetOfChars().set(' ').set('\t').set('\n').set('\f').set('\r')
                        .set('\u000B')));
                break;
            case 'w':
                ++index;
                set.set('a', 'z' + 1).set('A', 'Z' + 1).set('0', '9' + 1);
                break;
            case 'W':
                ++index;
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
            case '.':
                ++index;
                set.set(c);
                break;
            default:
                throw new RegexParseException("Wrong escape sequence " + c, text, index);
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
            throw new RegexParseException("Syntax error", text, index);
        }
        return result.get();
    }

    private void require(BooleanSupplier supplier) {
        if (!attempt(supplier)) {
            throw new RegexParseException("Syntax error", text, index);
        }
    }

    private boolean attempt(BooleanSupplier supplier) {
        int indexBackup = index;
        boolean result = supplier.getAsBoolean();
        if (!result) {
            index = indexBackup;
        }
        return result;
    }
}
