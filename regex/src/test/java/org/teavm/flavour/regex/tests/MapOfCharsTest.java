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

import org.junit.Test;

/**
 *
 * @author Alexey Andreev
 */
public class MapOfCharsTest {
    @Test
    public void setsRange() {
        new TestableMapOfChars<String>(20)
                .fill(5, 10, "foo")
                .verify();
    }

    @Test
    public void setsRangeWithOverlapping() {
        new TestableMapOfChars<String>(20)
                .fill(5, 12, "foo")
                .fill(10, 15, "bar")
                .verify();
    }

    @Test
    public void setsRangeWithStartOverlapping() {
        new TestableMapOfChars<String>(20)
                .fill(5, 12, "foo")
                .fill(2, 10, "bar")
                .verify();
    }

    @Test
    public void appendsRange() {
        new TestableMapOfChars<String>(20)
                .fill(5, 8, "foo")
                .fill(8, 12, "bar")
                .verify()
                .fill(12, 16, "baz")
                .verify();
    }

    @Test
    public void prependsRange() {
        new TestableMapOfChars<String>(20)
                .fill(12, 16, "baz")
                .fill(8, 12, "bar")
                .fill(5, 8, "foo")
                .verify();
    }

    @Test
    public void setsRangeWithStartReplacing() {
        new TestableMapOfChars<String>(20)
                .fill(5, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(8, 14, "***")
                .verify();
    }

    @Test
    public void setsRangeWithEndReplacing() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(6, 12, "***")
                .verify();
    }

    @Test
    public void clearsAll() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(1, 18, null)
                .verify();
    }

    @Test
    public void clearsAllByExactBound() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(4, 16, null)
                .verify();
    }

    @Test
    public void clearsStart() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(1, 14, null)
                .verify();
    }

    @Test
    public void clearsStartByExactBound() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(1, 12, null)
                .verify();
    }

    @Test
    public void clearsEnd() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(6, 18, null)
                .verify();
    }

    @Test
    public void clearsEndByExactBound() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(8, 18, null)
                .verify();
    }

    @Test
    public void insertsRange() {
        new TestableMapOfChars<String>(20)
                .fill(5, 15, "foo")
                .fill(7, 12, "bar")
                .verify();
    }

    @Test
    public void splitsRangePair() {
        new TestableMapOfChars<String>(20)
                .fill(5, 10, "foo")
                .fill(10, 15, "bar")
                .fill(7, 12, "baz")
                .verify();
    }

    @Test
    public void replacesRange() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(8, 12, "***")
                .verify();
    }

    @Test
    public void mergesRangeAtExactStart() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(8, 14, "bar")
                .verify();
    }

    @Test
    public void replacingRangeHasNoEffect() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(8, 12, "bar")
                .verify();
    }

    @Test
    public void replacingRangePartHasNoEffect() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(9, 11, "bar")
                .verify();
    }
}
