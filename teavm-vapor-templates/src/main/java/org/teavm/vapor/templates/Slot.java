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
package org.teavm.vapor.templates;

import org.teavm.dom.core.Node;

/**
 *
 * @author Alexey Andreev
 */
public abstract class Slot {
    Slot parent;

    public void append(Slot slot) {
        insert(slot, size());
    }

    public abstract void insert(Slot slot, int index);

    public abstract void delete();

    public abstract Slot getChild(int index);

    public abstract int size();

    public abstract int getIndex();

    public abstract Slot getAttributeSlot(String name);

    Slot() {
    }

    public static Slot create() {
        return null;
    }

    public static Slot wrap(Node domNode) {
        return null;
    }
}
