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

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class SetOfCharsTest {
    private boolean[] booleanArray = new boolean[20];
    private SetOfChars charSet = new SetOfChars();

    @Test
    public void setsRange() {
        set(5, 10);
        match();
    }

    @Test
    public void clearsRange() {
        set(0, 20);
        clear(5, 10);
        match();
    }

    @Test
    public void clearsSubSet() {
        set(5, 10);
        clear(2, 12);
        match();
    }

    @Test
    public void clearsIntersected() {
        set(5, 10);
        clear(7, 12);
        match();
    }

    @Test
    public void repeatedSetHasNoEffect() {
        set(5, 10);
        set(5, 10);
        match();
    }

    @Test
    public void repeatedClearHasNoEffect() {
        set(0, 20);
        clear(5, 10);
        clear(5, 10);
        match();
    }

    @Test
    public void setInsertsRangeBefore() {
        set(10, 15);
        set(0, 5);
        match();
    }

    @Test
    public void clearInsertsRangeBefore() {
        set(0, 20);
        clear(10, 15);
        clear(0, 5);
        match();
    }

    @Test
    public void setInsertRangeAfter() {
        set(0, 5);
        set(10, 15);
        match();
    }

    @Test
    public void setInsertRangeBetween() {
        set(0, 5);
        set(15, 20);
        set(8, 12);
        match();
    }

    @Test
    public void setJoinsRanges() {
        set(5, 10);
        set(14, 18);
        set(7, 16);
        match();
    }

    @Test
    public void setJoinsRangeAtEdges() {
        set(5, 10);
        set(14, 18);
        set(10, 14);
        match();
    }

    @Test
    public void setGrowsRange() {
        set(5, 10);
        set(4, 8);
        match();
    }

    @Test
    public void setGrowsRangeAtEdge() {
        set(5, 10);
        set(4, 5);
        match();
    }

    @Test
    public void setMergesAndGrows() {
        set(5, 8);
        set(11, 16);
        set(3, 18);
        match();
    }

    @Test
    public void setInnerMergesAndGrows() {
        set(5, 8);
        set(11, 16);
        set(6, 18);
        match();
    }

    @Test
    public void initsWithSingleChar() {
        init(10);
        match();
    }

    @Test
    public void initsWithRepeatedSingleChar() {
        init(10, 10, 10);
        match();
    }

    @Test
    public void initsWithSingleRange() {
        init(5, 6, 7, 7, 8, 9, 10, 10);
        match();
    }

    @Test
    public void initsWithMultipleRanges() {
        init(5, 6, 9, 12, 13, 15);
        match();
    }

    @Test
    public void invertsWholeRange() {
        set(5, 10);
        invert(5, 10);
        match();
    }

    @Test
    public void invertsSuperRange() {
        set(5, 10);
        invert(2, 15);
        match();
    }

    @Test
    public void invertsSubRange() {
        set(5, 15);
        invert(7, 12);
        match();
    }

    @Test
    public void invertsTwoRanges() {
        set(5, 8);
        set(11, 16);
        invert(2, 19);
        match();
    }

    @Test
    public void invertsBetweenTwoRanges() {
        set(5, 8);
        set(11, 16);
        invert(6, 19);
        match();
    }

    private void init(int... values) {
        charSet = new SetOfChars(values);
        for (int i = 0; i < booleanArray.length; ++i) {
            booleanArray[i] = false;
        }
        for (int value : values) {
            booleanArray[value] = true;
        }
    }

    private void set(int from, int to) {
        charSet.set(from, to);
        for (int i = from; i < to; ++i) {
            booleanArray[i] = true;
        }
    }

    private void clear(int from, int to) {
        charSet.clear(from, to);
        for (int i = from; i < to; ++i) {
            booleanArray[i] = false;
        }
    }

    private void invert(int from, int to) {
        charSet.invert(from, to);
        for (int i = from; i < to; ++i) {
            booleanArray[i] = !booleanArray[i];
        }
    }

    private void match() {
        for (int i = 0; i < booleanArray.length; ++i) {
            assertEquals("Element " + i + " does not match", booleanArray[i], charSet.has(i));
        }
    }
}
