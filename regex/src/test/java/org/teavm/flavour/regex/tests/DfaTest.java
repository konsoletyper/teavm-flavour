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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.teavm.flavour.regex.ast.Node.atLeast;
import static org.teavm.flavour.regex.ast.Node.atMost;
import static org.teavm.flavour.regex.ast.Node.concat;
import static org.teavm.flavour.regex.ast.Node.oneOf;
import static org.teavm.flavour.regex.ast.Node.optional;
import static org.teavm.flavour.regex.ast.Node.range;
import static org.teavm.flavour.regex.ast.Node.text;
import static org.teavm.flavour.regex.ast.Node.unlimited;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.teavm.flavour.regex.Matcher;
import org.teavm.flavour.regex.automata.Ambiguity;
import org.teavm.flavour.regex.automata.AmbiguityFinder;
import org.teavm.flavour.regex.automata.Dfa;

/**
 *
 * @author Alexey Andreev
 */
public class DfaTest {
    @Test
    public void matchesString() {
        Dfa dfa = Dfa.fromNode(text("foo"));
        assertTrue(dfa.matches("foo"));
        assertFalse(dfa.matches("foo*"));
        assertFalse(dfa.matches("fo"));
    }

    @Test
    public void matchesOneOfStrings() {
        Dfa dfa = Dfa.fromNode(oneOf(text("bar"), text("baz")));
        assertTrue(dfa.matches("bar"));
        assertTrue(dfa.matches("baz"));
        assertFalse(dfa.matches("foo"));
        assertFalse(dfa.matches("ba"));
    }

    @Test
    public void matchesRepeat() {
        Dfa dfa = Dfa.fromNode(concat(text("b"), atLeast(1, text("a")), text("r")));
        assertTrue(dfa.matches("bar"));
        assertTrue(dfa.matches("baaaaaar"));
        assertFalse(dfa.matches("br"));
        assertFalse(dfa.matches("baaz"));
    }

    @Test
    public void matchesRepeat2() {
        Dfa dfa = Dfa.fromNode(concat(
                text("["),
                atLeast(1, oneOf(text("bar"), text("baz"))),
                text("]")));
        assertTrue(dfa.matches("[bar]"));
        assertTrue(dfa.matches("[barbarbar]"));
        assertTrue(dfa.matches("[bazbarbaz]"));
        assertTrue(dfa.matches("[baz]"));
        assertFalse(dfa.matches("[foo]"));
        assertFalse(dfa.matches("[]"));
        assertFalse(dfa.matches("[barba]"));
    }

    @Test
    public void findsDomain() {
        Dfa dfa = Dfa.fromNodes(text("foo"), text("bar"));
        assertThat(dfa.domains("foo"), is(new int[] { 0 }));
        assertThat(dfa.domains("bar"), is(new int[] { 1 }));
        assertThat(dfa.domains("baz"), is(new int[0]));
    }

    @Test
    public void matchesTimestamp() {
        Dfa dfa = Dfa.fromNodes(concat(
                atMost(4, range('0', '9')),
                text("-"),
                atMost(2, range('0', '9')),
                text("-"),
                atMost(2, range('0', '9')),
                optional(
                    text("T"),
                    atMost(2, range('0', '9')),
                    text(":"),
                    atMost(2, range('0', '9')),
                    text(":"),
                    atMost(2, range('0', '9')),
                    optional(text("."), atMost(3, range('0', '9'))))));
        assertTrue(dfa.matches("2015-08-20T10:53:23.123"));
        assertTrue(dfa.matches("2015-08-20T10:53:23"));
        assertTrue(dfa.matches("2015-08-20"));
        assertTrue(dfa.matches("15-8-20"));
        assertTrue(dfa.matches("15-8-20T1:05:00"));
        assertFalse(dfa.matches("2015-08-20T"));
        assertFalse(dfa.matches("2015-08-20T10:53"));
        assertFalse(dfa.matches("2015-08-20T10:53"));
    }

    @Test
    public void matchesCamelCase() {
        Dfa dfa = Dfa.fromNodes(concat(
                range('a', 'z'),
                unlimited(oneOf(range('a', 'z'), range('0', '9'))),
                unlimited(
                    range('A', 'Z'),
                    atLeast(1, oneOf(range('a', 'z'), range('0', '9')))
                )));
        assertTrue(dfa.matches("matchesCamelCase"));
        assertTrue(dfa.matches("ab2cDef"));
        assertFalse(dfa.matches("DfaTest"));
        assertFalse(dfa.matches("abcDEf"));
    }

    @Test
    public void findsAmbiguity() {
        Dfa dfa = Dfa.fromNodes(
                concat(text("foo/"), atLeast(1, range('a', 'z'))),
                concat(atLeast(1, range('a', 'z')), text("/bar")));
        List<Ambiguity> ambiguities = AmbiguityFinder.findAmbiguities(dfa);
        assertThat(ambiguities.size(), is(1));
        assertThat(ambiguities.get(0).getDomains().length, is(2));
        assertThat(dfa.domains(ambiguities.get(0).getExample()).length, is(2));
    }

    @Test
    public void compiles() {
        Matcher matcher = Dfa.fromNode(atLeast(1,
                text("["),
                oneOf(
                        concat(text("fo"), atLeast(1, text("o"))),
                        concat(text("b"), atLeast(1, text("a")), text("r")),
                        concat(text("b"), atLeast(1, text("a")), text("z"))),
                text("]")))
                .compile();
        assertTrue(matcher.matches("[foo]"));
        assertTrue(matcher.matches("[foo][baar]"));
        assertTrue(matcher.matches("[fooo][baar][foo]"));
        assertFalse(matcher.matches("[foq]"));
        assertFalse(matcher.matches("[foooo"));

        for (int i = 0; i < 100000; ++i) {
            matcher.matches("[foo][baar]");
            matcher.matches("[fooo][baar][foo]");
            matcher.matches("[foq]");
            matcher.matches("[foooo");
        }

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000; ++i) {
            matcher.matches("[fooooooooooooooooooooooooooooooooooooooooooo][baaaaaaaaaaaaaaaaaaaaaaaaaar]" +
                    "[foo][bar][baz][foo]");
            matcher.matches("[foo][baar]");
            matcher.matches("[fooo][baar][foo]");
            matcher.matches("[foq]");
            matcher.matches("[foooo");
        }
        System.out.println(System.currentTimeMillis() - start);

        Pattern pattern = Pattern.compile("(\\[fo(o*)\\]\\[b(a*)r\\]\\[b(a*)z\\])+");
        for (int i = 0; i < 100000; ++i) {
            pattern.matcher("[foo][baar]").matches();
            pattern.matcher("[fooo][baar][foo]").matches();
            pattern.matcher("[foq]").matches();
            pattern.matcher("[foooo").matches();
        }

        start = System.currentTimeMillis();
        for (int i = 0; i < 100000; ++i) {
            pattern.matcher("[fooooooooooooooooooooooooooooooooooooooooooo][baaaaaaaaaaaaaaaaaaaaaaaaaar]" +
                    "[foo][bar][baz][foo]").matches();
            pattern.matcher("[foo][baar]").matches();
            pattern.matcher("[fooo][baar][foo]").matches();
            pattern.matcher("[foq]").matches();
            pattern.matcher("[foooo").matches();
        }
        System.out.println(System.currentTimeMillis() - start);
    }
}
