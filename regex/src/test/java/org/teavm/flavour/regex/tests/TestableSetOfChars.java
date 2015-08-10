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
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class TestableSetOfChars {
    private boolean[] array;
    private SetOfChars setOfChars;

    public TestableSetOfChars(int capacity, int... values) {
        setOfChars = new SetOfChars(values);
        array = new boolean[capacity];
        for (int value : values) {
            array[value] = true;
        }
    }

    public TestableSetOfChars set(int from, int to) {
        setOfChars.set(from, to);
        for (int i = from; i < to; ++i) {
            array[i] = true;
        }
        return this;
    }

    public TestableSetOfChars clear(int from, int to) {
        setOfChars.clear(from, to);
        for (int i = from; i < to; ++i) {
            array[i] = false;
        }
        return this;
    }

    public TestableSetOfChars invert(int from, int to) {
        setOfChars.invert(from, to);
        for (int i = from; i < to; ++i) {
            array[i] = !array[i];
        }
        return this;
    }

    public TestableSetOfChars intersectWith(TestableSetOfChars other) {
        for (int i = 0; i < array.length; ++i) {
            array[i] = array[i] & other.array[i];
        }
        setOfChars.intersectWith(other.setOfChars);
        return this;
    }

    public TestableSetOfChars uniteWith(TestableSetOfChars other) {
        for (int i = 0; i < array.length; ++i) {
            array[i] = array[i] | other.array[i];
        }
        setOfChars.uniteWith(other.setOfChars);
        return this;
    }

    public TestableSetOfChars verify() {
        for (int i = 0; i < array.length; ++i) {
            assertEquals("Element " + i + " does not match", array[i], setOfChars.has(i));
        }
        return this;
    }
}
