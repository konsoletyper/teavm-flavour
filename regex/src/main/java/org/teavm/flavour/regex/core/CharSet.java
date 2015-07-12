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
public final class CharSet implements Cloneable {
    private SparseSet set;
    private boolean inverted;

    public CharSet(int... chars) {
        set = new SparseSet(chars);
    }

    public boolean has(int c) {
        return inverted ^ set.has(c);
    }

    public void set(int c) {
        if (!inverted) {
            set.add(c);
        } else {
            set.remove(c);
        }
    }

    public void clear(int c) {
        if (!inverted) {
            set.remove(c);
        } else {
            set.add(c);
        }
    }

    public CharSet invert() {
        inverted = !inverted;
        return this;
    }

    public boolean isInverted() {
        return inverted;
    }

    public int[] getExceptionalMembers() {
        return set.all();
    }

    public CharSet intersectWith(CharSet other) {
        if (!inverted) {
            if (!other.inverted) {
                set.and(other.set);
            } else {
                set.andNot(other.set);
            }
        } else {
            if (other.inverted) {
                set.or(other.set);
            } else {
                SparseSet newSet = other.set.clone();
                newSet.andNot(set);
                set = newSet;
                inverted = false;
            }
        }
        return this;
    }

    public CharSet uniteWith(CharSet other) {
        if (!inverted) {
            if (!other.inverted) {
                set.or(other.set);
            } else {
                SparseSet newSet = other.set.clone();
                newSet.andNot(set);
                set = newSet;
                inverted = true;
            }
        } else {
            if (other.inverted) {
                set.and(other.set);
            } else {
                set.andNot(other.set);
            }
        }
        return this;
    }

    public static SparseSet[] split(CharSet[] sets) {
        SparseSet[] sparseSets = new SparseSet[sets.length];
        for (int i = 0; i < sets.length; ++i) {
            sparseSets[i] = sets[i].set;
        }
        return SparseSet.split(sparseSets);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        if (inverted) {
            sb.append('^');
        }
        for (int c : getExceptionalMembers()) {
            if (c >= 32) {
                sb.append((char)c);
            } else if (c >= 0) {
                sb.append("\\u00").append(Character.forDigit(c / 16, 16)).append(Character.forDigit(c % 16, 16));
            }
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    protected CharSet clone() {
        try {
            CharSet copy = (CharSet)super.clone();
            copy.set = set.clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("This exception should not occur", e);
        }
    }
}
