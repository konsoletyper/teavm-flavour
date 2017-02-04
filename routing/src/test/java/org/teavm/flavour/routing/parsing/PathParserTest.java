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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class PathParserTest {
    @Test
    public void parsesPath() {
        PathParserBuilder builder = new PathParserBuilder();
        builder.path().text("/users");
        builder.path().text("/users/").escapedRegex("[0-9]+");
        builder.path().text("/users/").escapedRegex("[0-9]+").text("/edit");
        PathParser parser = builder.build();

        assertThat(parser.parse("/users").getCaseIndex(), is(0));

        PathParserResult result = parser.parse("/users/23");
        assertThat(result.getCaseIndex(), is(1));
        assertThat(result.start(0), is(7));
        assertThat(result.end(0), is(9));

        result = parser.parse("/users/23/edit");
        assertThat(result.getCaseIndex(), is(2));
        assertThat(result.start(0), is(7));
        assertThat(result.end(0), is(9));
    }

    @Test
    public void transformsRegex() {
        PathParserBuilder builder = new PathParserBuilder();
        builder.path().escapedRegex("foo|bar-[0-9/]*|baz-///|чай");
        PathParser parser = builder.build();

        assertThat(parser.parse("foo").getCaseIndex(), is(0));
        assertThat(parser.parse("bar-23").getCaseIndex(), is(0));
        assertThat(parser.parse("bar-2/3").getCaseIndex(), is(-1));
        assertThat(parser.parse("bar-2%2F3").getCaseIndex(), is(0));
        assertThat(parser.parse("bar-2%2f3").getCaseIndex(), is(0));
        assertThat(parser.parse("baz-//").getCaseIndex(), is(-1));
        assertThat(parser.parse("baz-%2F%2F").getCaseIndex(), is(-1));
        assertThat(parser.parse("%D1%87%D0%B0%D0%B9").getCaseIndex(), is(0));
        assertThat(parser.parse("%d1%87%D0%B0%D0%b9").getCaseIndex(), is(0));
    }
}
