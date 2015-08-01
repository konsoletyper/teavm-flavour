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
package org.teavm.flavour.json.emit;

import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class PropertyInformation implements Cloneable {
    String name;
    String outputName;
    MethodDescriptor getter;
    MethodDescriptor setter;
    String fieldName;
    String className;
    ValueType type;
    boolean ignored;
    Integer creatorParameterIndex;

    @Override
    public PropertyInformation clone() {
        try {
            return (PropertyInformation) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Unexpected exception caught", e);
        }
    }

    public ValueType getType() {
        if (getter != null) {
            return getter.getResultType();
        }
        return type;
    }
}
