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
public class SetOfCharsIterator {
    private SetOfChars set;
    private int index;

    SetOfCharsIterator(SetOfChars set) {
        this.set = set;
    }

    public boolean hasPoint() {
        return index < set.size;
    }

    public boolean isSet() {
        return index % 2 == 0;
    }

    public int getIndex() {
        return set.toggleIndexes[index];
    }

    public void next() {
        if (index >= set.size) {
            throw new IllegalStateException("There are no more toggle points");
        }
        ++index;
    }
}
