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

import java.util.Objects;
import org.junit.Assert;
import org.teavm.flavour.regex.core.MapOfChars;
import org.teavm.flavour.regex.core.MapOfCharsIterator;

/**
 *
 * @author Alexey Andreev
 */
public class TestableMapOfChars<T> {
    private MapOfChars<T> map = new MapOfChars<>();
    private Object[] data;

    public TestableMapOfChars(int capacity) {
        data = new Object[capacity];
    }

    public TestableMapOfChars<T> fill(int from, int to, T value) {
        map.fill(from, to, value);
        for (int i = from; i < to; ++i) {
            data[i] = value;
        }
        return this;
    }

    public TestableMapOfChars<T> verify() {
        T previous = null;
        for (MapOfCharsIterator<T> iter = map.iterate(); iter.hasValue(); iter.next()) {
            if (Objects.equals(iter.getValue(), previous)) {
                Assert.fail("Two ranges with same value found at " + iter.getStart());
            }
            previous = iter.getValue();
        }
        for (int i = 0; i < data.length; ++i) {
            Assert.assertEquals("Error at index " + i, data[i], map.get(i));
        }
        return this;
    }

    public MapOfChars<T> getMap() {
        return map;
    }
}
