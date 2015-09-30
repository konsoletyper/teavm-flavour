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

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
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
            ParseResult<Node> result = unionParser().parse(0);
            if (result.data == null) {
                throw new RegexParseException("Unexpected character " + text.charAt(result.index),
                        text, result.index);
            } else if (result.index != text.length()) {
                throw new RegexParseException("Unexpected character " + text.charAt(result.index),
                        text, result.index);
            } else {
                return result.data;
            }
        } finally {
            this.text = null;
        }
    }

    private Parser<Node> unionParser() {
        Var<Node> node = new Var<>();
        return sequence(
                concatParser(),
                repeat(0, sequence(
                        matchChar('|'),
                        concatParser().map(n -> Node.oneOf(node.value, n)).thenStore(node))))
                .thenRead(node);
    }

    private Parser<Node> concatParser() {
        Var<Node> node = new Var<>();
        return sequence(
                quantifierParser(),
                repeat(0, quantifierParser().map(n -> Node.concat(node.value, n)).thenStore(node)))
                .thenRead(node);
    }

    private Parser<Node> quantifierParser() {
        Var<Node> node = new Var<>();
        return sequence(
                termParser().thenStore(node),
                optional(firstOf(
                        optionalQuantifierParser(node),
                        unlimitedQuantifierParser(node),
                        atLeastOneQuantifierParser(node),
                        customQuantifierParser(node))))
                .thenRead(node);
    }

    private Parser<Boolean> optionalQuantifierParser(Var<Node> node) {
        return matchChar('?').thenRead(node).map(Node::optional).thenStore(node);
    }

    private Parser<Boolean> unlimitedQuantifierParser(Var<Node> node) {
        return matchChar('*').thenRead(node).map(Node::unlimited).thenStore(node);
    }

    private Parser<Boolean> atLeastOneQuantifierParser(Var<Node> node) {
        return matchChar('*').thenRead(node).map(n -> Node.atLeast(1, n)).thenStore(node);
    }

    private Parser<Boolean> customQuantifierParser(Var<Node> node) {
        Var<Integer> atLeast = new Var<>();
        Var<Integer> atMost = new Var<>();
        Var<Boolean> hasMaximum = new Var<>();
        return sequence(
                matchChar('{'),
                integerParser().thenStore(atLeast),
                optional(sequence(
                        matchChar(',').map(b -> true).thenStore(hasMaximum),
                        optional(integerParser().thenStore(atMost)))),
                matchChar('}'))
                .thenRead(node)
                .map(n -> {
                    if (atMost.value != null) {
                        return Node.repeat(atLeast.value, atMost.value, n);
                    } else if (hasMaximum.value != null) {
                        return Node.atLeast(atLeast.value, n);
                    } else {
                        return Node.repeat(atLeast.value, atLeast.value, n);
                    }
                })
                .thenStore(node);
    }

    private Parser<Integer> integerParser() {
        return repeat(1, matchRange('0', '9')).then((start, end) -> Integer.parseInt(text.substring(start, end)));
    }

    private Parser<Node> termParser() {
        return firstOf(
                anyCharParser(),
                groupParser(),
                escapeSequenceParser().map(Node::range),
                charRangeParser().map(Node::range),
                except(')', '|', ']', '?', '{', '}', '*', '+')
                        .then((start, end) -> Node.range(text.charAt(end - 1), text.charAt(end - 1))));
    }

    private Parser<Node> anyCharParser() {
        return matchChar('.').then((start, end) -> Node.range(new SetOfChars().set(-1, Character.MAX_VALUE + 1)));
    }

    private Parser<Node> groupParser() {
        Var<Node> node = new Var<>();
        return sequence(matchChar('('), unionParser().thenStore(node), matchChar(')')).thenRead(node);
    }

    private Parser<SetOfChars> charRangeParser() {
        Var<SetOfChars> set = new Var<>();
        Var<Boolean> inverted = new Var<>();
        inverted.value = true;
        return sequence(
                matchChar('[').map(x -> new SetOfChars()).thenStore(set),
                optional(matchChar('^').map(b -> true).thenStore(inverted)),
                firstOf(
                    escapeSequenceParser(),
                    singleCharRangeParser().map(SetOfChars::new),
                    closedCharRangeParser(),
                    charRangeParser())
                    .map(newSet -> newSet.uniteWith(set.value)).thenStore(set),
                matchChar(']'))
                .thenRead(set)
                .map(s -> !inverted.value ? s : excluding(s));
    }

    private Parser<SetOfChars> closedCharRangeParser() {
        Var<Character> start = new Var<>();
        Var<Character> end = new Var<>();
        return sequence(
                singleCharRangeParser().thenStore(start),
                matchChar('-'),
                singleCharRangeParser())
                .then(() -> new SetOfChars().set(start.value, end.value + 1));
    }

    private Parser<Character> singleCharRangeParser() {
        return except('[', '\\', '^', ']').then((start, end) -> text.charAt(end - 1));
    }

    private Parser<SetOfChars> escapeSequenceParser() {
        Var<SetOfChars> set = new Var<>();
        return sequence(matchChar('\\'), firstOf(
                matchChar('t').then(() -> new SetOfChars('\t')),
                matchChar('n').then(() -> new SetOfChars('\n')),
                matchChar('b').then(() -> new SetOfChars('\b')),
                matchChar('f').then(() -> new SetOfChars('\f')),
                matchChar('e').then(() -> new SetOfChars('\u001F')),
                matchChar('d').then(() -> new SetOfChars('0', '9')),
                matchChar('D').then(() -> excluding(range('0', '9'))),
                matchChar('s').then(() -> new SetOfChars(' ', '\t', '\n', '\f', '\r', '\u000B')),
                matchChar('S').then(() -> excluding(new SetOfChars(' ', '\t', '\n', '\f', '\r', '\u000B'))),
                matchChar('w').then(() -> new SetOfChars().set('a', 'z' + 1).set('A', 'Z' + 1).set('0', '9' + 1)),
                matchChar('W').then(() -> excluding(new SetOfChars().set('a', 'z' + 1).set('A', 'Z' + 1)
                        .set('0', '9' + 1))),
                matchChars('(', ')', '|', '+', '*', '[', ']', '\\', '-', '^', '$')
                        .then((start, end) -> new SetOfChars(text.charAt(end - 1)))
        ).thenStore(set)).thenRead(set);
    }

    private static SetOfChars range(char from, char to) {
        return new SetOfChars().set(from, to + 1);
    }

    private static SetOfChars excluding(SetOfChars chars) {
        chars.invert(-1, Character.MAX_VALUE + 1);
        return chars;
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

    private Parser<Boolean> matchChars(char... chars) {
        char[] array = chars.clone();
        Arrays.sort(array);
        return index -> {
            if (index >= text.length() || Arrays.binarySearch(array, text.charAt(index)) < 0) {
                return new ParseResult<>(index, null);
            }
            return new ParseResult<>(index + 1, true);
        };
    }

    private Parser<Boolean> except(char... chars) {
        char[] array = chars.clone();
        Arrays.sort(array);
        return index -> {
            if (index >= text.length() || Arrays.binarySearch(array, text.charAt(index)) >= 0) {
                return new ParseResult<>(index, null);
            }
            return new ParseResult<>(index + 1, true);
        };
    }

    private Parser<Boolean> matchRange(char a, char b) {
        return index -> {
            if (index >= text.length() || text.charAt(index) < a || text.charAt(index) > b) {
                return new ParseResult<>(index, null);
            }
            return new ParseResult<>(index + 1, true);
        };
    }

    @SafeVarargs
    private final <T> Parser<T> firstOf(Parser<T>... arguments) {
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

    @SafeVarargs
    private final Parser<Boolean> sequence(Parser<?>... arguments) {
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

    private Parser<Boolean> repeat(int atLeast, Parser<?> argument) {
        return index -> {
            int repeatCount = 0;
            while (true) {
                ParseResult<?> result = argument.parse(index);
                if (result.data == null) {
                    break;
                }
                index = result.index;
            }
            return new ParseResult<>(index, repeatCount < atLeast ? null : true);
        };
    }

    private <T> Parser<Optional<T>> optional(Parser<T> argument) {
        return index -> {
            ParseResult<T> result = argument.parse(index);
            return new ParseResult<>(result.index, result.data != null ? Optional.of(result.data) : Optional.empty());
        };
    }

    interface Parser<T> {
        ParseResult<T> parse(int index);

        default <S> Parser<S> then(ParserAction<S> action) {
            return index -> {
                ParseResult<T> result = parse(index);
                if (result.data == null) {
                    return new ParseResult<>(index, null);
                }
                return new ParseResult<>(result.index, action.run(index, result.index));
            };
        }

        default <S> Parser<S> then(Supplier<S> action) {
            return index -> {
                ParseResult<T> result = parse(index);
                if (result.data == null) {
                    return new ParseResult<>(index, null);
                }
                return new ParseResult<>(result.index, action.get());
            };
        }

        default Parser<Boolean> thenStore(Var<T> var) {
            return index -> {
                ParseResult<T> result = parse(index);
                if (result.data == null) {
                    return new ParseResult<>(result.index, null);
                }
                var.value = result.data;
                return new ParseResult<>(result.index, true);
            };
        }

        default <S> Parser<S> thenRead(Var<S> var) {
            return index -> {
                ParseResult<T> result = parse(index);
                if (result.data == null) {
                    return new ParseResult<>(result.index, null);
                }
                return new ParseResult<>(result.index, var.value);
            };
        }

        default <S> Parser<S> map(Function<T, S> f) {
            return index -> {
                ParseResult<T> result = parse(index);
                if (result.data == null) {
                    return new ParseResult<>(result.index, null);
                }
                return new ParseResult<>(result.index, f.apply(result.data));
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
