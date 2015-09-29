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
package org.teavm.flavour.regex.ast;

import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public abstract class Node {
    public abstract void acceptVisitor(NodeVisitor visitor);

    public static Node text(String value) {
        return new TextNode(value);
    }

    public static Node unlimited(Node... nodes) {
        return new RepeatNode(nodes.length == 1 ? nodes[0] : concat(nodes), 0, 0);
    }

    public static Node atLeast(int number, Node... nodes) {
        return new RepeatNode(nodes.length == 1 ? nodes[0] : concat(nodes), number, 0);
    }

    public static Node atMost(int number, Node... nodes) {
        return new RepeatNode(nodes.length == 1 ? nodes[0] : concat(nodes), 0, number);
    }

    public static Node repeat(int atLeast, int atMost, Node... nodes) {
        return new RepeatNode(nodes.length == 1 ? nodes[0] : concat(nodes), atLeast, atMost);
    }

    public static Node concat(Node... nodes) {
        return new ConcatNode(nodes);
    }

    public static Node oneOf(Node... nodes) {
        return new OneOfNode(nodes);
    }

    public static Node range(SetOfChars chars) {
        return new CharSetNode(chars);
    }

    public static Node range(char from, char to) {
        return new CharSetNode(new SetOfChars().set(from, to + 1));
    }

    public static Node character(char c) {
        return new CharSetNode(new SetOfChars().set(c, c + 1));
    }

    public static Node optional(Node... nodes) {
        return new RepeatNode(nodes.length == 1 ? nodes[0] : concat(nodes), 0, 1);
    }
}
