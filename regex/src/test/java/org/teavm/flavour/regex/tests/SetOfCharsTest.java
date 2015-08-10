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
public class SetOfCharsTest {
    @Test
    public void setsRange() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .verify();
    }

    @Test
    public void clearsRange() {
        new TestableSetOfChars(20)
                .set(0, 20)
                .clear(5, 10)
                .verify();
    }

    @Test
    public void clearsSubSet() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .clear(2, 12)
                .verify();
    }

    @Test
    public void clearsIntersected() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .clear(7, 12)
                .verify();
    }

    @Test
    public void repeatedSetHasNoEffect() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .set(5, 10)
                .verify();
    }

    @Test
    public void repeatedClearHasNoEffect() {
        new TestableSetOfChars(20)
                .set(0, 20)
                .clear(5, 10)
                .clear(5, 10)
                .verify();
    }

    @Test
    public void setInsertsRangeBefore() {
        new TestableSetOfChars(20)
                .set(10, 15)
                .set(0, 5)
                .verify();
    }

    @Test
    public void clearInsertsRangeBefore() {
        new TestableSetOfChars(20)
                .set(0, 20)
                .clear(10, 15)
                .clear(0, 5)
                .verify();
    }

    @Test
    public void setInsertRangeAfter() {
        new TestableSetOfChars(20)
                .set(0, 5)
                .set(10, 15)
                .verify();
    }

    @Test
    public void setInsertRangeBetween() {
        new TestableSetOfChars(20)
                .set(0, 5)
                .set(15, 20)
                .set(8, 12)
                .verify();
    }

    @Test
    public void setJoinsRanges() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .set(14, 18)
                .set(7, 16)
                .verify();
    }

    @Test
    public void setJoinsRangeAtEdges() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .set(14, 18)
                .set(10, 14)
                .verify();
    }

    @Test
    public void setGrowsRange() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .set(4, 8)
                .verify();
    }

    @Test
    public void setGrowsRangeAtEdge() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .set(4, 5)
                .verify();
    }

    @Test
    public void setMergesAndGrows() {
        new TestableSetOfChars(20)
                .set(5, 8)
                .set(11, 16)
                .set(3, 18)
                .verify();
    }

    @Test
    public void setInnerMergesAndGrows() {
        new TestableSetOfChars(20)
                .set(5, 8)
                .set(11, 16)
                .set(6, 18)
                .verify();
    }

    @Test
    public void initsWithSingleChar() {
        new TestableSetOfChars(20, 10)
                .verify();
    }

    @Test
    public void initsWithRepeatedSingleChar() {
        new TestableSetOfChars(20, 10, 10)
                .verify();
    }

    @Test
    public void initsWithSingleRange() {
        new TestableSetOfChars(20, 5, 6, 7, 7, 8, 9, 10, 10)
                .verify();
    }

    @Test
    public void initsWithMultipleRanges() {
        new TestableSetOfChars(20, 5, 6, 9, 12, 13, 15)
                .verify();
    }

    @Test
    public void invertsWholeRange() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .invert(5, 10)
                .verify();
    }

    @Test
    public void invertsSuperRange() {
        new TestableSetOfChars(20)
                .set(5, 10)
                .invert(2, 15)
                .verify();
    }

    @Test
    public void invertsSubRange() {
        new TestableSetOfChars(20)
                .set(5, 15)
                .invert(7, 12)
                .verify();
    }

    @Test
    public void invertsTwoRanges() {
        new TestableSetOfChars(20)
                .set(5, 8)
                .set(11, 16)
                .invert(2, 19)
                .verify();
    }

    @Test
    public void invertsBetweenTwoRanges() {
        new TestableSetOfChars(20)
                .set(5, 8)
                .set(11, 16)
                .invert(6, 19)
                .verify();
    }

    @Test
    public void intersects() {
        new TestableSetOfChars(20).set(1, 5).set(8, 11).set(14, 18)
                .intersectWith(new TestableSetOfChars(20).set(3, 9).set(11, 19))
                .verify();
    }

    @Test
    public void intersectsWithLarge() {
        new TestableSetOfChars(100).set(10, 30).set(40, 70).set(80, 95)
                .intersectWith(new TestableSetOfChars(100).set(3, 9).set(11, 19).set(21, 29).set(30, 34)
                        .set(40, 45).set(50, 60).set(62, 68).set(80, 82).set(86, 90).set(94, 98))
                .verify();
    }

    @Test
    public void intersectsLargeSets() {
        new TestableSetOfChars(100).set(1, 4).set(7, 9).set(11, 21).set(25, 32).set(38, 45).set(50, 59).set(75, 80)
                .intersectWith(new TestableSetOfChars(100).set(3, 9).set(11, 19).set(21, 29).set(30, 34)
                        .set(40, 45).set(50, 60).set(62, 68).set(80, 82).set(86, 90).set(94, 98))
                .verify();
    }

    @Test
    public void unites() {
        new TestableSetOfChars(20).set(1, 5).set(8, 11).set(14, 18)
                .uniteWith(new TestableSetOfChars(20).set(3, 9).set(11, 19))
                .verify();
    }

    @Test
    public void unitesWithLarge() {
        new TestableSetOfChars(100).set(10, 30).set(40, 70).set(80, 95)
                .uniteWith(new TestableSetOfChars(100).set(3, 9).set(11, 19).set(21, 29).set(30, 34)
                        .set(40, 45).set(50, 60).set(62, 68).set(80, 82).set(86, 90).set(94, 98))
                .verify();
    }

    @Test
    public void unitesLargeSets() {
        new TestableSetOfChars(100).set(1, 4).set(7, 9).set(11, 21).set(25, 32).set(38, 45).set(50, 59).set(75, 80)
                .uniteWith(new TestableSetOfChars(100).set(3, 9).set(11, 19).set(21, 29).set(30, 34)
                        .set(40, 45).set(50, 60).set(62, 68).set(80, 82).set(86, 90).set(94, 98))
                .verify();
    }
}
