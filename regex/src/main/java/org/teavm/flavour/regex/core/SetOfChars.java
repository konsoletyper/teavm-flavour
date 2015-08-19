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
import java.util.PrimitiveIterator;

/**
 *
 * @author Alexey Andreev
 */
public final class SetOfChars implements Cloneable, Iterable<Integer> {
    int[] toggleIndexes;
    int size;

    public SetOfChars(int... chars) {
        if (chars.length > 0) {
            chars = chars.clone();
            Arrays.sort(chars);
        }
        toggleIndexes = new int[Math.max(8, chars.length * 2)];
        if (chars.length > 0) {
            int index = 0;
            toggleIndexes[index++] = chars[0];
            for (int i = 1; i < chars.length; ++i) {
                if (chars[i] - chars[i - 1] < 2) {
                    continue;
                }
                toggleIndexes[index++] = chars[i - 1] + 1;
                toggleIndexes[index++] = chars[i];
            }
            toggleIndexes[index++] = chars[chars.length - 1] + 1;
            size = index;
        }
    }

    public boolean has(int c) {
        int index = Arrays.binarySearch(toggleIndexes, 0, size, c);
        if (index < 0) {
            index = ~index - 1;
        }
        return index % 2 == 0;
    }

    public SetOfChars set(int from, int to) {
        return fill(from, to, true);
    }

    public SetOfChars set(int c) {
        return set(c, c + 1);
    }

    public SetOfChars clear(int from, int to) {
        return fill(from, to, false);
    }

    public SetOfChars clear(int c) {
        return clear(c, c + 1);
    }

    public SetOfChars fill(int from, int to, boolean value) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return this;
        }

        int twoRem = value ? 0 : 1;
        int fromIndex = Arrays.binarySearch(toggleIndexes, 0, size, from);
        if (fromIndex >= 0) {
            if (Math.abs(fromIndex % 2) != twoRem) {
                if (--fromIndex >= 0) {
                    from = toggleIndexes[fromIndex];
                }
            }
        } else {
            fromIndex = ~fromIndex;
            if (Math.abs(fromIndex % 2) == twoRem) {
                if (fromIndex >= size || to < toggleIndexes[fromIndex]) {
                    ensureCapacity(size + 2);
                    System.arraycopy(toggleIndexes, fromIndex, toggleIndexes, fromIndex + 2, size - fromIndex);
                    size += 2;
                    toggleIndexes[fromIndex] = from;
                    toggleIndexes[fromIndex + 1] = to;
                    return this;
                }
                toggleIndexes[fromIndex] = from;
            } else {
                if (--fromIndex >= 0) {
                    from = toggleIndexes[fromIndex];
                }
            }
        }

        int toIndex = Arrays.binarySearch(toggleIndexes, 0, size, to);
        if (toIndex >= 0) {
            if (Math.abs(toIndex % 2) == twoRem) {
                if (++toIndex < size) {
                    to = toggleIndexes[toIndex];
                }
            }
        } else {
            toIndex = ~toIndex;
            if (Math.abs(toIndex % 2) == twoRem) {
                if (--toIndex >= 0) {
                    toggleIndexes[toIndex] = to;
                }
            } else {
                to = toggleIndexes[toIndex];
            }
        }

        System.arraycopy(toggleIndexes, toIndex, toggleIndexes, fromIndex + 1, size - toIndex);
        size -= toIndex - fromIndex - 1;

        return this;
    }

    public SetOfChars invert(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("Range start " + from + " is greater than range end " + to);
        }
        if (from == to) {
            return this;
        }

        int fromIndex = Arrays.binarySearch(toggleIndexes, 0, size, from);
        if (fromIndex >= 0) {
            System.arraycopy(toggleIndexes, fromIndex + 1, toggleIndexes, fromIndex, size - fromIndex - 1);
            --size;
        } else {
            fromIndex = ~fromIndex;
            ensureCapacity(size + 1);
            System.arraycopy(toggleIndexes, fromIndex, toggleIndexes, fromIndex + 1, size - fromIndex);
            toggleIndexes[fromIndex] = from;
            ++size;
        }

        int toIndex = Arrays.binarySearch(toggleIndexes, 0, size, to);
        if (toIndex >= 0) {
            System.arraycopy(toggleIndexes, toIndex + 1, toggleIndexes, toIndex, size - toIndex - 1);
            --size;
        } else {
            toIndex = ~toIndex;
            ensureCapacity(size + 1);
            System.arraycopy(toggleIndexes, toIndex, toggleIndexes, toIndex + 1, size - toIndex);
            toggleIndexes[toIndex] = to;
            ++size;
        }

        return this;
    }

    public SetOfChars intersectWith(SetOfChars other) {
        if (size == 0) {
            return this;
        }
        if (other.size == 0) {
            size = 0;
            return this;
        }

        int[] otherToggleIndexes;
        int otherSize;
        if (other.size < 10) {
            otherToggleIndexes = other.toggleIndexes;
            otherSize = other.size;
        } else if (size < 10) {
            otherSize = size;
            otherToggleIndexes = toggleIndexes;
            size = other.size;
            toggleIndexes = other.toggleIndexes.clone();
        } else {
            int[] firstToggle = toggleIndexes;
            int firstSize = size;
            int[] secondToggle = other.toggleIndexes;
            int secondSize = other.size;

            toggleIndexes = new int[firstSize + secondSize];
            int i = 0;
            int j = 0;
            int index = 0;
            while (i < firstSize && j < secondSize) {
                int point;
                if (firstToggle[i] < secondToggle[j]) {
                    point = firstToggle[i++];
                } else if (secondToggle[j] < firstToggle[i]) {
                    point = secondToggle[j++];
                } else {
                    point = firstToggle[i++];
                    ++j;
                }
                if (i % 2 == 1 && j % 2 == 1) {
                    toggleIndexes[index++] = point;
                } else {
                    if (index % 2 != 0) {
                        toggleIndexes[index++] = point;
                    }
                }
            }
            size = index;
            return this;
        }

        int last = Math.min(toggleIndexes[0], otherToggleIndexes[0]);
        for (int i = 0; i < otherSize; i += 2) {
            clear(last, otherToggleIndexes[i]);
            last = otherToggleIndexes[i + 1];
        }
        clear(last, Math.max(toggleIndexes[size - 1], otherToggleIndexes[otherSize - 1]));
        return this;
    }

    public SetOfChars uniteWith(SetOfChars other) {
        if (other.size == 0) {
            return this;
        }
        if (size == 0) {
            toggleIndexes = other.toggleIndexes.clone();
            size = other.size;
            return this;
        }

        int[] otherToggleIndexes;
        int otherSize;
        if (other.size < 10) {
            otherToggleIndexes = other.toggleIndexes;
            otherSize = other.size;
        } else if (size < 10) {
            otherSize = size;
            otherToggleIndexes = toggleIndexes;
            size = other.size;
            toggleIndexes = other.toggleIndexes.clone();
        } else {
            int[] firstToggle = toggleIndexes;
            int firstSize = size;
            int[] secondToggle = other.toggleIndexes;
            int secondSize = other.size;

            toggleIndexes = new int[firstSize + secondSize];
            int i = 0;
            int j = 0;
            int index = 0;
            while (true) {
                if (i >= firstSize) {
                    while (j < secondSize) {
                        toggleIndexes[index++] = secondToggle[j++];
                    }
                    break;
                } else if (j >= secondSize) {
                    while (i < firstSize) {
                        toggleIndexes[index++] = firstToggle[i++];
                    }
                    break;
                } else {
                    int point;
                    if (firstToggle[i] < secondToggle[j]) {
                        point = firstToggle[i++];
                    } else if (secondToggle[j] < firstToggle[i]) {
                        point = secondToggle[j++];
                    } else {
                        point = firstToggle[i++];
                        ++j;
                    }
                    if (i % 2 == 0 && j % 2 == 0) {
                        toggleIndexes[index++] = point;
                    } else {
                        if (index % 2 == 0) {
                            toggleIndexes[index++] = point;
                        }
                    }
                }
            }
            size = index;
            return this;
        }

        for (int i = 0; i < otherSize; i += 2) {
            set(otherToggleIndexes[i], otherToggleIndexes[i + 1]);
        }
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
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < size; i += 2) {
            int from = toggleIndexes[i];
            int to = toggleIndexes[i + 1];
            if (from + 1 == to) {
                append(sb, from);
            } else {
                append(sb, from);
                sb.append('-');
                append(sb, to - 1);
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
    protected SetOfChars clone() {
        try {
            SetOfChars copy = (SetOfChars) super.clone();
            copy.toggleIndexes = toggleIndexes.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("This exception should not occur", e);
        }
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new PrimitiveIterator.OfInt() {
            int index;
            @Override public boolean hasNext() {
                return index < size;
            }
            @Override public int nextInt() {
                return toggleIndexes[index++];
            }
        };
    }

    public SetOfCharsIterator iterate() {
        return new SetOfCharsIterator(this);
    }

    public int[] getToggleIndexes() {
        return Arrays.copyOf(toggleIndexes, size);
    }
}
