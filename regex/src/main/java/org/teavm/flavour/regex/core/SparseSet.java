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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class SparseSet implements Cloneable {
    private int[] data;
    private int size;

    public SparseSet() {
        data = new int[4];
    }

    public SparseSet(int... data) {
        this.data = Arrays.copyOf(data, data.length);
        size = data.length;
    }

    public boolean add(int c) {
        int index = Arrays.binarySearch(data, 0, size, c);
        if (index >= 0) {
            return false;
        } else {
            index = ~index;
            ensureCapacity(size + 1);
            System.arraycopy(data, index, data, index + 1, size - index);
            data[index] = c;
            ++size;
            return false;
        }
    }

    public boolean remove(int c) {
        int index = Arrays.binarySearch(data, 0, size, c);
        if (index < 0) {
            return false;
        } else {
            --size;
            System.arraycopy(data, index + 1, data, index, size - index);
            return true;
        }
    }

    public boolean has(int c) {
        return Arrays.binarySearch(data, 0, size, c) >= 0;
    }

    public void and(SparseSet other) {
        int[] newData = new int[Math.min(size, other.size)];
        int i = 0;
        int j = 0;
        int index = 0;
        while (true) {
            if (i >= size || j >= other.size) {
                break;
            } else {
                int a = data[i];
                int b = other.data[j];
                if (a < b) {
                    ++i;
                } else if (a > b) {
                    ++j;
                } else {
                    newData[index++] = a;
                    ++i;
                    ++j;
                }
            }
        }
        data = Arrays.copyOf(newData, index);
        size = index;
    }

    public void andNot(SparseSet other) {
        int[] newData = new int[size];
        int i = 0;
        int j = 0;
        int index = 0;
        while (true) {
            if (i >= size) {
                break;
            } else if (j >= other.size) {
                System.arraycopy(data, i, newData, index, size - i);
                break;
            } else {
                int a = data[i];
                int b = other.data[j];
                if (a < b) {
                    newData[index++] = a;
                    ++i;
                } else if (a > b) {
                    ++j;
                } else {
                    ++i;
                    ++j;
                }
            }
        }
        data = Arrays.copyOf(newData, index);
        size = index;
    }

    public void or(SparseSet other) {
        int[] newData = new int[size + other.size];
        int i = 0;
        int j = 0;
        int index = 0;
        while (true) {
            if (i >= size) {
                System.arraycopy(other.data, j, newData, index, other.size - j);
                break;
            } else if (j >= other.size) {
                System.arraycopy(data, i, newData, index, size - i);
                break;
            } else {
                int a = data[i];
                int b = other.data[j];
                if (a < b) {
                    newData[index++] = a;
                    ++i;
                } else if (a > b) {
                    newData[index++] = b;
                    ++j;
                } else {
                    newData[index++] = a;
                    ++i;
                    ++j;
                }
            }
        }
        data = Arrays.copyOf(newData, index);
        size = index;
    }

    public int[] all() {
        return Arrays.copyOf(data, size);
    }

    public static SparseSet[] split(SparseSet[] sets) {
        Map<Path, SplitMember> members = new HashMap<>();
        int[] indexes = new int[sets.length];
        while (true) {
            boolean[] pathData = new boolean[sets.length];

            int i;
            int min = 0;
            for (i = 0; i < sets.length; ++i) {
                if (indexes[i] < sets[i].size) {
                    min = sets[i].data[i];
                    break;
                }
            }
            if (i == sets.length) {
                break;
            }

            int start = i;
            for (; i < pathData.length; ++i) {
                if (indexes[i] < sets[i].size) {
                    int elem = sets[i].data[indexes[i]];
                    if (elem < min) {
                        start = i;
                        min = elem;
                    }
                }
            }
            Arrays.fill(pathData, 0, start, false);

            Path path = new Path(pathData);
            SplitMember member = members.get(path);
            if (member == null) {
                member = new SplitMember();
                members.put(path, member);
            }
            member.add(min);

            for (i = 0; i < pathData.length; ++i) {
                if (pathData[i]) {
                    ++indexes[i];
                }
            }
        }

        Collection<SplitMember> memberColl = members.values();
        SparseSet[] result = new SparseSet[memberColl.size()];
        int index = 0;
        for (SplitMember member : memberColl) {
            result[index++] = new SparseSet(Arrays.copyOf(member.data, member.size));
        }
        return result;
    }

    static class Path {
        private boolean[] data;
        int hash;

        public Path(boolean[] data) {
            this.data = data;
            int hash = 0;
            for (int i = 0; i < data.length; ++i) {
                hash = (hash >>> 31) | (hash << 1) ^ (data[i] ? 1 : 0);
            }
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            Path other = (Path)obj;
            for (int i = 0; i < data.length; ++i) {
                if (data[i] != other.data[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    static class SplitMember {
        int[] data = new int[4];
        int size;

        public void add(int e) {
            if (size > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = e;
        }
    }

    private void ensureCapacity(int capacity) {
        if (capacity <= data.length) {
            return;
        }
        int newLength = data.length * 2;
        if (newLength < capacity) {
            newLength = capacity * 3 / 2;
        }
        data = Arrays.copyOf(data, Math.min(4, newLength));
    }

    @Override
    protected SparseSet clone() {
        try {
            SparseSet copy = (SparseSet)super.clone();
            copy.data = Arrays.copyOf(data, data.length);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("This exception should not be thrown", e);
        }
    }
}
