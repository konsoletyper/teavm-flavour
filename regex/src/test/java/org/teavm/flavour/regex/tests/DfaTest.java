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
import java.util.List;
import org.junit.Test;
import org.teavm.flavour.regex.Pattern;
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
        Pattern pattern = parse("foo");
        assertTrue(pattern.matches("foo"));
        assertFalse(pattern.matches("foo*"));
        assertFalse(pattern.matches("fo"));
    }

    @Test
    public void matchesOneOfStrings() {
        Pattern pattern = parse("bar|baz");
        assertTrue(pattern.matches("bar"));
        assertTrue(pattern.matches("baz"));
        assertFalse(pattern.matches("foo"));
        assertFalse(pattern.matches("ba"));
    }

    @Test
    public void matchesRepeat() {
        Pattern pattern = parse("ba+r");
        assertTrue(pattern.matches("bar"));
        assertTrue(pattern.matches("baaaaaar"));
        assertFalse(pattern.matches("br"));
        assertFalse(pattern.matches("baaz"));
    }

    @Test
    public void matchesRepeat2() {
        Pattern pattern = parse("\\[(bar|baz)+\\]");
        assertTrue(pattern.matches("[bar]"));
        assertTrue(pattern.matches("[barbarbar]"));
        assertTrue(pattern.matches("[bazbarbaz]"));
        assertTrue(pattern.matches("[baz]"));
        assertFalse(pattern.matches("[foo]"));
        assertFalse(pattern.matches("[]"));
        assertFalse(pattern.matches("[barba]"));
    }

    @Test
    public void findsDomain() {
        Pattern pattern = parse("foo", "bar");
        assertThat(pattern.which("foo"), is(0));
        assertThat(pattern.which("bar"), is(1));
        assertThat(pattern.which("baz"), is(-1));
    }

    @Test
    public void matchesTimestamp() {
        Pattern pattern = parse("[0-9]{0,4}-[0-9]{0,2}-[0-9]{0,2}(T\\d{0,2}:\\d{0,2}:\\d{0,2}(\\.\\d{0,3})?)?");
        assertTrue(pattern.matches("2015-08-20T10:53:23.123"));
        assertTrue(pattern.matches("2015-08-20T10:53:23"));
        assertTrue(pattern.matches("2015-08-20"));
        assertTrue(pattern.matches("15-8-20"));
        assertTrue(pattern.matches("15-8-20T1:05:00"));
        assertFalse(pattern.matches("2015-08-20T"));
        assertFalse(pattern.matches("2015-08-20T10:53"));
        assertFalse(pattern.matches("2015-08-20T10:53"));
    }

    @Test
    public void matchesCamelCase() {
        Pattern pattern = parse("[a-z][a-z0-9]*([A-Z][a-z0-9]+)*");
        assertTrue(pattern.matches("matchesCamelCase"));
        assertTrue(pattern.matches("ab2cDef"));
        assertFalse(pattern.matches("DfaTest"));
        assertFalse(pattern.matches("abcDEf"));
    }

    @Test
    public void recognizesEscapeSequences() {
        Pattern pattern = parse("\\t\\n\\r\\b\\f\\e\\(\\)\\+\\*\\[\\]\\\\\\-\\^\\$\\.");
        assertTrue(pattern.matches("\t\n\r\b\f\u001F()+*[]\\-^$."));
    }

    @Test
    public void recognizesNumClasses() {
        Pattern pattern = parse("\\d\\D");
        assertTrue(pattern.matches("0!"));
        assertTrue(pattern.matches("3X"));
        assertFalse(pattern.matches("*6"));
        assertFalse(pattern.matches("**"));
        assertFalse(pattern.matches("24"));
    }

    @Test
    public void recognizesAlphaNumClasses() {
        Pattern pattern = parse("\\w\\W");
        assertTrue(pattern.matches("0!"));
        assertTrue(pattern.matches("Q%"));
        assertTrue(pattern.matches("3^"));
        assertFalse(pattern.matches("*6"));
        assertFalse(pattern.matches("**"));
        assertFalse(pattern.matches("24"));
        assertFalse(pattern.matches("AA"));
    }

    @Test
    public void recognizedSpaceClasses() {
        Pattern pattern = parse("\\s*");
        assertTrue(pattern.matches(" \t\n\r\f\u000B"));
        assertFalse(pattern.matches("A"));

        pattern = parse("\\S");
        assertTrue(pattern.matches("A"));
        assertFalse(pattern.matches(" "));
        assertFalse(pattern.matches("\t"));
        assertFalse(pattern.matches("\n"));
        assertFalse(pattern.matches("\f"));
        assertFalse(pattern.matches("\u000B"));
    }

    @Test
    public void allowsEsapesInRanges() {
        Pattern pattern = parse("[\\[-\\]]*");
        assertTrue(pattern.matches("[]"));
        assertFalse(pattern.matches("123"));
    }

    @Test
    public void parsesInvertedRange() {
        Pattern pattern = parse("[^3-5]*");
        assertTrue(pattern.matches("12678"));
        assertFalse(pattern.matches("3"));
    }

    @Test
    public void parsesCustomQuantifier() {
        Pattern pattern = parse(".{2}");
        assertTrue(pattern.matches("12"));
        assertTrue(pattern.matches("qw"));
        assertFalse(pattern.matches("q"));
        assertFalse(pattern.matches("qwe"));

        pattern = parse(".{2,}");
        assertTrue(pattern.matches("12"));
        assertTrue(pattern.matches("123"));
        assertTrue(pattern.matches("1234"));
        assertFalse(pattern.matches("1"));

        pattern = parse(".{2,4}");
        assertTrue(pattern.matches("12"));
        assertTrue(pattern.matches("123"));
        assertTrue(pattern.matches("1234"));
        assertFalse(pattern.matches("1"));
        assertFalse(pattern.matches("12345"));
    }

    @Test
    public void findsAmbiguity() {
        Dfa dfa = Dfa.parse("foo/[a-z]+", "[a-z]+/bar");
        List<Ambiguity> ambiguities = AmbiguityFinder.findAmbiguities(dfa);
        assertThat(ambiguities.size(), is(1));
        assertThat(ambiguities.get(0).getDomains().length, is(2));
        assertThat(dfa.domains(ambiguities.get(0).getExample()).length, is(2));
    }

    private Pattern parse(String... clauses) {
        return Dfa.parse(clauses).compile();
    }
}
