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

import java.util.Arrays;
import java.util.Objects;

/**
 *
 * @author Alexey Andreev
 */
public class MapOfChars<T> {
    int[] toggleIndexes;
    int size;
    Object[] data;

    public MapOfChars() {
        toggleIndexes = new int[4];
        data = new Object[4];
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        index = Arrays.binarySearch(toggleIndexes, index);
        if (index < 0) {
            index = ~index - 1;
        }
        return index >= 0 && index < size ? (T) data[index] : null;
    }

    public MapOfChars<T> fill(int from, int to, T value) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return this;
        }
        if (value == null && size == 0) {
            return this;
        }

        int fromIndex = Arrays.binarySearch(toggleIndexes, 0, size, from);

        if (fromIndex >= 0) {
            --fromIndex;
            if (fromIndex >= 0 && Objects.equals(data[fromIndex], value)) {
                from = toggleIndexes[fromIndex];
            }
        } else {
            fromIndex = ~fromIndex;
            if (fromIndex >= size && value == null) {
                return this;
            }
            if (fromIndex < size && Objects.equals(data[fromIndex], value)) {
                if (to < toggleIndexes[fromIndex]) {
                    ensureCapacity(size + 2);
                    System.arraycopy(toggleIndexes, fromIndex, toggleIndexes, fromIndex + 2, size - fromIndex);
                    System.arraycopy(data, fromIndex, data, fromIndex + 2, size - fromIndex);
                    size += 2;
                    toggleIndexes[fromIndex] = from;
                    data[fromIndex] = value;
                    toggleIndexes[fromIndex + 1] = to;
                    data[fromIndex + 1] = fromIndex > 0 ? data[fromIndex - 1] : null;
                    return this;
                }
                toggleIndexes[fromIndex] = from;
            } else if (fromIndex > 0 && Objects.equals(data[fromIndex - 1], value)) {
                from = toggleIndexes[--fromIndex];
            } else if (fromIndex == 0 && value == null) {
                fromIndex = -1;
                if (to <= toggleIndexes[0]) {
                    return this;
                }
            } else {
                --fromIndex;
                if (value != null) {
                    if (fromIndex < 0) {
                        ensureCapacity(size + 1);
                        System.arraycopy(toggleIndexes, 0, toggleIndexes, 1, size);
                        System.arraycopy(data, 0, data, 1, size);
                        ++size;
                        ++fromIndex;
                    }
                    toggleIndexes[fromIndex] = from;
                    data[fromIndex] = value;
                }
            }
        }
        data[fromIndex] = value;

        int toIndex = Arrays.binarySearch(toggleIndexes, 0, size, to);

        if (toIndex >= 0) {
            if (Objects.equals(data[toIndex], value)) {
                to = toggleIndexes[++toIndex];
            }
        } else {
            toIndex = ~toIndex;
            if (Objects.equals(data[toIndex], value)) {
                ++toIndex;
            } else {
                toggleIndexes[toIndex] = to;
            }
        }

        System.arraycopy(toggleIndexes, toIndex, toggleIndexes, fromIndex + 1, size - toIndex);
        size -= toIndex - fromIndex - 1;

        return this;
    }

    private void ensureCapacity(int sz) {
        if (sz < toggleIndexes.length) {
            return;
        }
        int capacity = toggleIndexes.length;
        while (capacity < sz) {
            capacity *= 2;
        }
        toggleIndexes = Arrays.copyOf(toggleIndexes, capacity);
        data = Arrays.copyOf(data, capacity);
    }
}
