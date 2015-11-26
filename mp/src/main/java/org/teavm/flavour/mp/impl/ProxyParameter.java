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
package org.teavm.flavour.mp.impl;

import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyParameter {
    private int index;
    private ValueType type;
    private boolean dynamic;
    private String delegationMarker;

    ProxyParameter(int index, ValueType type, boolean dynamic) {
        this.index = index;
        this.type = type;
        this.dynamic = dynamic;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public String getDelegationMarker() {
        return delegationMarker;
    }

    public void setDelegationMarker(String delegationMarker) {
        this.delegationMarker = delegationMarker;
    }

    public int getIndex() {
        return index;
    }

    public ValueType getType() {
        return type;
    }
}
