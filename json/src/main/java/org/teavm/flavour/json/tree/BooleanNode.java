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
public abstract class BooleanNode extends Node {
    public static final BooleanNode TRUE = get(true);
    public static final BooleanNode FALSE = get(false);

    @JSBody(params = "value", script = "return !!value;")
    public static native BooleanNode get(boolean value);

    @JSBody(params = "node", script = "return !!node;")
    private static native boolean getValue(BooleanNode node);

    public final boolean getValue() {
        return getValue(this);
    }
}
