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
package org.teavm.flavour.expr.type;

import java.util.Objects;

/**
 *
 * @author Alexey Andreev
 */
public final class GenericArray extends GenericType {
    ValueType elementType;

    public GenericArray(ValueType elementType) {
        Objects.requireNonNull(elementType);
        this.elementType = elementType;
    }

    public ValueType getElementType() {
        return elementType;
    }

    @Override
    public GenericArray substitute(Substitutions substitutions) {
        if (elementType instanceof GenericType) {
            GenericType genericElem = (GenericType) elementType;
            GenericType substElement = genericElem.substitute(substitutions);
            return substElement != elementType ? new GenericArray(substElement) : this;
        } else {
            return this;
        }
    }

    @Override
    public int hashCode() {
        return 31 * elementType.hashCode() + 13;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GenericArray)) {
            return false;
        }
        GenericArray other = (GenericArray) obj;
        return elementType.equals(other.elementType);
    }

    @Override
    public GenericType erasure() {
        return elementType instanceof GenericType ? new GenericArray(((GenericType) elementType).erasure()) : this;
    }
}
