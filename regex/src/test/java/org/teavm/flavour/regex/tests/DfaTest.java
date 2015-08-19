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
package org.teavm.flavour.regex.tests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;
import org.teavm.flavour.regex.ast.ConcatNode;
import org.teavm.flavour.regex.ast.OneOfNode;
import org.teavm.flavour.regex.ast.RepeatNode;
import org.teavm.flavour.regex.ast.TextNode;
import org.teavm.flavour.regex.automata.Dfa;

/**
 *
 * @author Alexey Andreev
 */
public class DfaTest {
    @Test
    public void matchesString() {
        Dfa dfa = Dfa.fromNode(new TextNode("foo"));
        assertTrue(dfa.matches("foo"));
        assertFalse(dfa.matches("foo*"));
        assertFalse(dfa.matches("fo"));
    }

    @Test
    public void matchesOneOfStrings() {
        Dfa dfa = Dfa.fromNode(new OneOfNode(new TextNode("bar"), new TextNode("baz")));
        assertTrue(dfa.matches("bar"));
        assertTrue(dfa.matches("baz"));
        assertFalse(dfa.matches("foo"));
        assertFalse(dfa.matches("ba"));
    }

    @Test
    public void matchesRepeat() {
        Dfa dfa = Dfa.fromNode(new ConcatNode(new TextNode("b"), new RepeatNode(new TextNode("a"), 1, 0),
                new TextNode("r")));
        assertTrue(dfa.matches("bar"));
        assertTrue(dfa.matches("baaaaaar"));
        assertFalse(dfa.matches("br"));
        assertFalse(dfa.matches("baaz"));
    }

    @Test
    public void matchesRepeat2() {
        Dfa dfa = Dfa.fromNode(new ConcatNode(
                new TextNode("["),
                new RepeatNode(new OneOfNode(new TextNode("bar"), new TextNode("baz")), 1, 0),
                new TextNode("]")));
        assertTrue(dfa.matches("[bar]"));
        assertTrue(dfa.matches("[barbarbar]"));
        assertTrue(dfa.matches("[bazbarbaz]"));
        assertTrue(dfa.matches("[baz]"));
        assertFalse(dfa.matches("[foo]"));
        assertFalse(dfa.matches("[]"));
        assertFalse(dfa.matches("[barba]"));
    }
}
