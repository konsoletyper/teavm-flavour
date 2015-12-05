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
package org.teavm.flavour.mp.impl.meta;

import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyParameter {
    private int index;
    private int callIndex;
    private ValueType type;
    private ParameterKind kind;
    private Object value;

    ProxyParameter(int index, int callIndex, ValueType type, ParameterKind kind, Object value) {
        this.index = index;
        this.callIndex = callIndex;
        this.type = type;
        this.kind = kind;
        this.value = value;
    }

    public int getIndex() {
        return index;
    }

    public int getCallIndex() {
        return callIndex;
    }

    public ValueType getType() {
        return type;
    }

    public ParameterKind getKind() {
        return kind;
    }

    public Object getValue() {
        return value;
    }
}
