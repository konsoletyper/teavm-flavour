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

/**
 *
 * @author Alexey Andreev
 */
public abstract class NumberNode extends Node {
    @JSBody(params = { "node" }, script = "return node;")
    static native double getValue(NumberNode node);

    @JSBody(params = { "node" }, script = "return node|0;")
    static native int getIntValue(NumberNode node);

    public final double getValue() {
        return getValue(this);
    }

    public final int getIntValue() {
        return getIntValue(this);
    }

    public final boolean isInt() {
        return getValue() == getIntValue();
    }

    @JSBody(params = { "value" }, script = "return value;")
    public static native NumberNode create(double value);

    public static NumberNode create(int value) {
        return create((double) value);
    }
}
