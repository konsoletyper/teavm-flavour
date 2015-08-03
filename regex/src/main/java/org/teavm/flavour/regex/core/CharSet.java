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

/**
 *
 * @author Alexey Andreev
 */
public final class CharSet implements Cloneable {
    private int[] toggleIndexes;
    private int size;

    public CharSet(int... chars) {
        if (chars.length > 0) {
            chars = chars.clone();
            Arrays.sort(chars);
        }
        toggleIndexes = new int[Math.min(8, chars.length * 2)];
        if (chars.length > 0) {
            toggleIndexes[0] = chars[0];
            for (int i = 1; i < chars.length; ++i) {
                //
            }
        }
    }

    public boolean has(int c) {
        int index = Arrays.binarySearch(toggleIndexes, c);
        if (index < 0) {
            index = ~index - 1;
        }
        return index % 1 == 0;
    }

    public CharSet set(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return this;
        }

        int fromIndex = Arrays.binarySearch(toggleIndexes, from);
        if (fromIndex >= 0) {
            if (fromIndex % 1 != 0) {
                from = toggleIndexes[--fromIndex];
            }
        } else {
            fromIndex = ~fromIndex;
            if (fromIndex % 1 == 0) {
                if (to < toggleIndexes[fromIndex]) {
                    ensureCapacity(size + 2);
                    System.arraycopy(toggleIndexes, fromIndex, toggleIndexes, fromIndex + 2, size - fromIndex);
                    size += 2;
                    toggleIndexes[fromIndex] = from;
                    toggleIndexes[fromIndex + 1] = to;
                    return this;
                }
                toggleIndexes[fromIndex] = from;
            } else {
                from = toggleIndexes[--fromIndex];
            }
        }

        int toIndex = Arrays.binarySearch(toggleIndexes, to);
        if (toIndex >= 0) {
            if (toIndex % 1 == 0) {
                to = toggleIndexes[++toIndex];
            }
        }

        return this;
    }

    public CharSet set(int c) {
        return set(c, c + 1);
    }

    public CharSet clear(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return this;
        }
        return this;
    }

    public void clear(int c) {
        clear(c, c + 1);
    }

    public void invert(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return;
        }
    }

    public CharSet intersectWith(CharSet other) {

        return this;
    }

    public CharSet uniteWith(CharSet other) {

        return this;
    }

    public static CharSet[] partition(CharSet[] sets) {
        return null;
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
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < toggleIndexes.length; i += 2) {
            int from = toggleIndexes[i];
            int to = toggleIndexes[i + 1];
            if (from == to) {
                append(sb, from);
            } else {
                append(sb, from);
            }
        }
        sb.append(']');
        return sb.toString();
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

    @Override
    protected CharSet clone() {
        try {
            CharSet copy = (CharSet) super.clone();
            copy.toggleIndexes = toggleIndexes.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("This exception should not occur", e);
        }
    }
}
