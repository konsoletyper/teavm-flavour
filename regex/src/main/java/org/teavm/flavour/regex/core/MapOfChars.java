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
public class MapOfChars<T> implements Cloneable {
    int[] toggleIndexes;
    int size;
    Object[] data;

    public MapOfChars() {
        toggleIndexes = new int[4];
        data = new Object[4];
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        index = Arrays.binarySearch(toggleIndexes, 0, size, index);
        if (index < 0) {
            index = ~index - 1;
        }
        return index >= 0 && index < size ? (T) data[index] : null;
    }

    public int[] getToggleIndexes() {
        return Arrays.copyOf(toggleIndexes, size);
    }

    public MapOfChars<T> fill(int from, int to, T value) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return this;
        }
        if (size == 0) {
            if (value != null) {
                ensureCapacity(2);
                toggleIndexes[0] = from;
                data[0] = value;
                toggleIndexes[1] = to;
                data[1] = null;
                size = 2;
            }
            return this;
        }

        int fromIndex = Arrays.binarySearch(toggleIndexes, 0, size, from);
        if (fromIndex >= 0) {
            if (fromIndex > 0 && Objects.equals(data[fromIndex - 1], value)) {
                from = toggleIndexes[--fromIndex];
            }
        } else {
            fromIndex = ~fromIndex;
            if (fromIndex > 0 && Objects.equals(data[fromIndex - 1], value)) {
                from = toggleIndexes[--fromIndex];
            } else if (fromIndex < size && Objects.equals(data[fromIndex], value)
                    && to >= toggleIndexes[fromIndex]) {
                toggleIndexes[fromIndex] = from;
            }
        }

        int toIndex = Arrays.binarySearch(toggleIndexes, 0, size, to);
        if (toIndex >= 0) {
            if (toIndex < size - 1 && Objects.equals(data[toIndex], value)) {
                to = toggleIndexes[++toIndex];
            }
            ++toIndex;
        } else {
            toIndex = ~toIndex;
            if (toIndex > 0 && toIndex < size && Objects.equals(data[toIndex - 1], value)) {
                to = toggleIndexes[toIndex++];
            } else if (toIndex > 1 && Objects.equals(data[toIndex - 2], value)
                    && from < toggleIndexes[toIndex - 1]) {
                toggleIndexes[toIndex - 1] = to;
            }
        }
        T valueAfter = get(to);

        if (toIndex == 0) {
            if (value != null) {
                if (from != toggleIndexes[0]) {
                    ensureCapacity(size + 2);
                    System.arraycopy(toggleIndexes, 0, toggleIndexes, 2, size);
                    System.arraycopy(data, 0, data, 2, size);
                    toggleIndexes[0] = from;
                    data[0] = value;
                    toggleIndexes[1] = to;
                    data[1] = null;
                    size += 2;
                } else {
                    ensureCapacity(size + 1);
                    System.arraycopy(toggleIndexes, 0, toggleIndexes, 1, size);
                    System.arraycopy(data, 0, data, 1, size);
                    toggleIndexes[0] = from;
                    data[0] = value;
                    size += 1;
                }
            }
            return this;
        } else if (fromIndex == size) {
            if (value != null) {
                ensureCapacity(size + 2);
                toggleIndexes[size] = from;
                data[size] = value;
                toggleIndexes[size + 1] = to;
                data[size + 1] = null;
                size += 2;
            }
            return this;
        } else if (fromIndex == size - 1 && from == toggleIndexes[size - 1]) {
            if (value != null) {
                ensureCapacity(size + 1);
                data[size - 1] = value;
                toggleIndexes[size] = to;
                data[size] = null;
                size += 1;
            }
            return this;
        } else if (fromIndex == 0 && toIndex == size) {
            if (value == null) {
                size = 0;
            } else {
                ensureCapacity(2);
                toggleIndexes[0] = from;
                data[0] = value;
                toggleIndexes[1] = to;
                data[1] = null;
                size = 2;
            }
            return this;
        } else if (fromIndex == 0) {
            if (toggleIndexes[toIndex] != to) {
                --toIndex;
                toggleIndexes[toIndex] = to;
                data[toIndex] = valueAfter;
            }
            if (value != null) {
                ++fromIndex;
            }
            if (fromIndex != toIndex) {
                ensureCapacity(size + fromIndex - toIndex);
                System.arraycopy(toggleIndexes, toIndex, toggleIndexes, fromIndex, size - toIndex);
                System.arraycopy(data, toIndex, data, fromIndex, size - toIndex);
            }
            if (value != null) {
                toggleIndexes[0] = from;
                data[0] = value;
            }
            size -= toIndex - fromIndex;
            return this;
        } else if (toIndex >= size) {
            toggleIndexes[fromIndex] = from;
            data[fromIndex] = value;
            ++fromIndex;
            if (value != null) {
                ensureCapacity(fromIndex + 1);
                toggleIndexes[fromIndex] = to;
                data[fromIndex] = null;
                ++fromIndex;
            }
            size = fromIndex;
            return this;
        }

        --toIndex;

        int desiredToIndex = fromIndex + 1;
        if (toIndex < desiredToIndex) {
            int extraSpace = desiredToIndex - toIndex;
            ensureCapacity(size + extraSpace);
            System.arraycopy(toggleIndexes, fromIndex, toggleIndexes, fromIndex + extraSpace, size - fromIndex);
            System.arraycopy(data, fromIndex, data, fromIndex + extraSpace, size - fromIndex);
            size += extraSpace;
        } else if (toIndex > desiredToIndex) {
            int wastedSpace = toIndex - desiredToIndex;
            System.arraycopy(toggleIndexes, toIndex, toggleIndexes, toIndex - wastedSpace, size - toIndex);
            System.arraycopy(data, toIndex, data, toIndex - wastedSpace, size - toIndex);
            size -= wastedSpace;
        }

        toggleIndexes[fromIndex] = from;
        data[fromIndex] = value;
        toggleIndexes[desiredToIndex] = to;
        data[desiredToIndex] = valueAfter;

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

    public MapOfCharsIterator<T> iterate() {
        return new MapOfCharsIterator<>(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < size - 1; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            if (toggleIndexes[i] + 1 == toggleIndexes[i + 1]) {
                append(sb, toggleIndexes[i]);
            } else {
                sb.append('[');
                append(sb, toggleIndexes[i]);
                append(sb, toggleIndexes[i + 1] - 1);
                sb.append(']');
            }
            sb.append(" => " + data[i]);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public MapOfChars<T> clone() {
        try {
            @SuppressWarnings("unchecked")
            MapOfChars<T> copy = (MapOfChars<T>) super.clone();
            copy.data = data.clone();
            copy.toggleIndexes = toggleIndexes.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    private static void append(StringBuilder sb, int c) {
        if (c >= 32) {
            switch ((char) c) {
                case '-':
                    sb.append("\\-").append(c);
                    break;
                default:
                    sb.append((char) c);
                    break;
            }
        } else if (c >= 0) {
            sb.append("\\u00").append(Character.forDigit(c / 16, 16)).append(Character.forDigit(c % 16, 16));
        } else {
            sb.append("EOF");
        }
    }
}
