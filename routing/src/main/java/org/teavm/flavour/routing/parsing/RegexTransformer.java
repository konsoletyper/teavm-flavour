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
package org.teavm.flavour.routing.parsing;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.teavm.flavour.regex.ast.CharSetNode;
import org.teavm.flavour.regex.ast.ConcatNode;
import org.teavm.flavour.regex.ast.EmptyNode;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.ast.NodeVisitor;
import org.teavm.flavour.regex.ast.OneOfNode;
import org.teavm.flavour.regex.ast.RepeatNode;
import org.teavm.flavour.regex.ast.TextNode;
import org.teavm.flavour.regex.core.SetOfChars;
import org.teavm.flavour.regex.core.SetOfCharsIterator;

/**
 *
 * @author Alexey Andreev
 */
public final class RegexTransformer {
    private static Charset utf8 = Charset.forName("UTF-8");
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final SetOfChars VALID_CHARS = new SetOfChars()
            .set('A', 'Z' + 1)
            .set('a', 'z' + 1)
            .set('0', '9' + 1)
            .set('$').set('-').set('_').set('.')
            .set('+').set('!').set('*').set('\'').set('(').set(')').set(',');

    private RegexTransformer() {
    }

    public static Node escape(Node node) {
        TransformationVisitor visitor = new TransformationVisitor();
        node.acceptVisitor(visitor);
        return visitor.replacement;
    }

    public static String escapeString(String text) {
        StringBuilder sb = new StringBuilder();
        ByteBuffer octetBuffer = utf8.encode(text);
        byte[] octets = new byte[octetBuffer.remaining()];
        octetBuffer.get(octets);
        for (int i = 0; i < octets.length; ++i) {
            byte octet = octets[i];
            if (!isValid((char) octet)) {
                sb.append('%').append(HEX_DIGITS[octet / 16]).append(HEX_DIGITS[octet % 16]);
            } else {
                sb.append((char) octet);
            }
        }
        return sb.toString();
    }

    public static boolean isValid(char c) {
        switch (c) {
            case '$':
            case '-':
            case '_':
            case '.':
            case '+':
            case '!':
            case '*':
            case '\'':
            case '(':
            case ')':
            case ',':
                return true;
            default:
                return c >= '0' && c <= '9'
                    || c >= 'a' && c <= 'z'
                    || c >= 'A' && c <= 'Z';
        }
    }

    static class TransformationVisitor implements NodeVisitor {
        Node replacement;

        @Override
        public void visit(TextNode node) {
            replacement = new TextNode(escapeString(node.getValue()));
        }

        @Override
        public void visit(ConcatNode node) {
            ConcatNode result = new ConcatNode();
            for (Node child : node.getSequence()) {
                child.acceptVisitor(this);
                result.getSequence().add(replacement);
            }
            replacement = node;
        }

        @Override
        public void visit(CharSetNode node) {
            OneOfNode oneOf = null;

            int last = 0;
            for (SetOfCharsIterator iter = node.getCharSet().iterate(); iter.hasPoint();) {
                if (!iter.isSet()) {
                    for (int i = last; i < iter.getIndex(); ++i) {
                        if (!isValid((char) i)) {
                            if (oneOf == null) {
                                oneOf = new OneOfNode();
                                replacement = oneOf;
                            }
                            String escaped = escapeString(String.valueOf((char) i));
                            oneOf.getElements().add(Node.text(escaped));
                        }
                    }
                }
                last = iter.getIndex();
                iter.next();
            }

            if (oneOf != null) {
                node.getCharSet().intersectWith(VALID_CHARS);
                oneOf.getElements().add(node);
            }

            replacement = oneOf != null ? oneOf : new CharSetNode(node.getCharSet());
        }

        @Override
        public void visit(EmptyNode node) {
            replacement = node;
        }

        @Override
        public void visit(RepeatNode node) {
            node.getRepeated().acceptVisitor(this);
            RepeatNode result = new RepeatNode(replacement, node.getMinimum(), node.getMaximum());
            replacement = result;
        }

        @Override
        public void visit(OneOfNode node) {
            OneOfNode result = new OneOfNode();
            for (Node child : node.getElements()) {
                child.acceptVisitor(this);
                result.getElements().add(replacement);
            }
            replacement = result;
        }
    }
}
