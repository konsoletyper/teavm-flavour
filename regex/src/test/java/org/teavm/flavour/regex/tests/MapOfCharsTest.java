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

import java.util.Random;
import org.junit.Test;

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
                .fill(12, 15, "bar")
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

    @Test
    public void replacingRangeStartHasNoEffect() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(8, 10, "bar")
                .verify();
    }

    @Test
    public void replacingRangeEndHasNoEffect() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "baz")
                .fill(10, 12, "bar")
                .verify();
    }

    @Test
    public void mergesRanges() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "foo")
                .fill(6, 14, "foo")
                .verify();
    }

    @Test
    public void mergesRangesWithExactBounds() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "foo")
                .fill(4, 16, "foo")
                .verify();
    }

    @Test
    public void mergesRangesWithExactLowerBound() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "foo")
                .fill(4, 14, "foo")
                .verify();
    }

    @Test
    public void mergesRangesWithExactUpperBound() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "foo")
                .fill(6, 16, "foo")
                .verify();
    }

    @Test
    public void coversRange() {
        new TestableMapOfChars<String>(20)
                .fill(4, 8, "foo")
                .fill(8, 12, "bar")
                .fill(12, 16, "foo")
                .fill(2, 18, "foo")
                .verify();
    }

    //@Test
    public void randomTest() {
        Random random = new Random();
        String[] strings = { null, "foo", "bar", "baz", "***", "qwe", "123" };
        for (int i = 0; i < 100; ++i) {
            int iterations = 2 + random.nextInt(100);
            int stringsToUse = 3 + random.nextInt(strings.length - 3);
            TestableMapOfChars<String> map = new TestableMapOfChars<>(40);
            System.out.println("Beginning iteration");
            for (int j = 0; j < iterations; ++j) {
                int a = random.nextInt(40);
                int b = random.nextInt(40);
                int lower = Math.min(a, b);
                int upper = Math.max(a, b);
                String str = strings[random.nextInt(stringsToUse)];
                System.out.println("  " + map.getMap());
                System.out.println("  [" + lower + "; " + upper + ") <- " + str);
                try {
                    map.fill(lower, upper, str).verify();
                } catch (AssertionError e) {
                    System.out.println("  ERROR: "  + map.getMap());
                    throw e;
                }
            }
        }
    }

    @Test
    public void appendsRangeWithGap() {
        new TestableMapOfChars<String>(20)
                .fill(2, 7, "foo")
                .fill(7, 12, "bar")
                .fill(13, 18, "***")
                .verify();
    }

    @Test
    public void prependsRangeWithGap() {
        new TestableMapOfChars<String>(20)
                .fill(10, 15, "foo")
                .fill(2, 7, "foo")
                .verify();
    }

    @Test
    public void erasesEnd() {
        new TestableMapOfChars<String>(20)
                .fill(1, 10, "foo")
                .fill(10, 15, "bar")
                .fill(5, 15, null)
                .verify();
    }

    @Test
    public void erasesStart() {
        new TestableMapOfChars<String>(20)
                .fill(1, 10, "foo")
                .fill(10, 15, "bar")
                .fill(1, 12, null)
                .verify();
    }

    @Test
    public void appendsNullProperly() {
        new TestableMapOfChars<String>(20)
                .fill(1, 10, "foo")
                .fill(10, 15, null)
                .verify();
    }

    @Test
    public void resizesRangeToRight() {
        new TestableMapOfChars<String>(20)
                .fill(5, 10, "foo")
                .fill(10, 15, "bar")
                .fill(10, 12, "foo")
                .verify();
    }

    @Test
    public void resizesRangeToLeft() {
        new TestableMapOfChars<String>(20)
                .fill(5, 10, "foo")
                .fill(10, 15, "bar")
                .fill(7, 10, "bar")
                .verify();
    }

    @Test
    public void deletesInnerRange() {
        new TestableMapOfChars<String>(20)
                .fill(1, 4, "foo")
                .fill(4, 7, null)
                .fill(7, 10, "bar")
                .fill(10, 13, null)
                .fill(15, 18, "baz")
                .fill(5, 12, null)
                .verify();
    }

    @Test
    public void extendsNullRangeToLeft() {
        new TestableMapOfChars<String>(20)
                .fill(1, 4, "foo")
                .fill(4, 7, "bar")
                .fill(7, 10, null)
                .fill(10, 13, "baz")
                .fill(3, 7, null)
                .verify();
    }
}
