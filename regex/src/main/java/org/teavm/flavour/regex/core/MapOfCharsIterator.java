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
package org.teavm.flavour.regex.core;

/**
 *
 * @author Alexey Andreev
 */
public class MapOfCharsIterator<T> {
    private int index;
    private MapOfChars<T> map;

    MapOfCharsIterator(MapOfChars<T> map) {
        this.map = map;
    }

    public boolean hasValue() {
        return index < map.size - 1;
    }

    public int getStart() {
        return map.toggleIndexes[index];
    }

    public int getEnd() {
        return map.toggleIndexes[index + 1];
    }

    @SuppressWarnings("unchecked")
    public T getValue() {
        return (T) map.data[index];
    }

    public void next() {
        ++index;
    }
}
