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
package org.teavm.flavour.json.tree;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSIndexer;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSProperty;

/**
 *
 * @author Alexey Andreev
 */
public abstract class ArrayNode extends Node {
    @JSProperty
    abstract int getLength();

    public final int size() {
        return getLength();
    }

    @JSIndexer
    public abstract Node get(int index);

    @JSIndexer
    public abstract void set(int index, Node value);

    @JSMethod("push")
    public abstract void add(Node value);

    abstract boolean splice(int start, int deleteCount);

    abstract boolean splice(int start, int deleteCount, Node value);

    public final void insert(int index, Node value) {
        splice(index, 0, value);
    }

    public final void remove(int index) {
        splice(index, 1);
    }

    @JSBody(params = {}, script = "return [];")
    public static native ArrayNode create();
}
