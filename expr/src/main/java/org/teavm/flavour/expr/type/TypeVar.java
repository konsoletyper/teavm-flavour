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

/**
 *
 * @author Alexey Andreev
 */
public class TypeVar {
    private GenericType lowerBound;
    private GenericType upperBound;
    private String name;

    public TypeVar() {
        this(null);
    }

    public TypeVar(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public GenericType getLowerBound() {
        return lowerBound;
    }

    public GenericType getUpperBound() {
        return upperBound;
    }

    public void withLowerBound(GenericType lowerBound) {
        this.lowerBound = lowerBound;
        this.upperBound = null;
    }

    public void withUpperBound(GenericType upperBound) {
        this.upperBound = upperBound;
        this.lowerBound = null;
    }
}
