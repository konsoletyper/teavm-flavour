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

/**
 *
 * @author Alexey Andreev
 */
public abstract class ObjectNode extends Node {
    @JSIndexer
    public abstract Node get(String key);

    @JSIndexer
    public abstract void set(String key, Node value);

    @JSBody(params = {}, script = ""
            + "var array = [];"
            + "for (var key in this) {"
                + "array.push(key);"
            + "}"
            + "return array;")
    public final native String[] allKeys();

    @JSBody(params = "key", script = "return key in this;")
    public final native boolean has(String key);

    @JSBody(params = {}, script = "return {};")
    public static native ObjectNode create();
}
