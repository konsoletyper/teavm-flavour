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
import org.teavm.jso.JSObject;

/**
 *
 * @author Alexey Andreev
 */
public abstract class Node implements JSObject {
    @JSBody(params = { "node" }, script = "return typeof node == 'object' && node instanceof Array;")
    static native boolean isArray(Node node);

    @JSBody(params = { "node" }, script = "return typeof node == 'object' && !(node instanceof Array);")
    static native boolean isObject(Node node);

    @JSBody(params = { "node" }, script = "return typeof node == 'string';")
    static native boolean isString(Node node);

    @JSBody(params = { "node" }, script = "return node === null;")
    static native boolean isNull(Node node);

    @JSBody(params = { "node" }, script = "return typeof node == 'number';")
    static native boolean isNumber(Node node);

    @JSBody(params = { "node" }, script = "return typeof node == 'boolean';")
    static native boolean isBoolean(Node node);

    public final boolean isArray() {
        return isArray(this);
    }

    public final boolean isObject() {
        return isObject(this);
    }

    public final boolean isString() {
        return isString(this);
    }

    public final boolean isNull() {
        return isNull(this);
    }

    public final boolean isNumber() {
        return isNumber(this);
    }

    public final boolean isBoolean() {
        return isBoolean(this);
    }

    public final String stringify() {
        return stringify(this);
    }

    @JSBody(params = { "node" }, script = "return JSON.stringify(node);")
    static native String stringify(Node node);

    @JSBody(params = { "text" }, script = "return JSON.parse(text);")
    public static native Node parse(String text);
}
