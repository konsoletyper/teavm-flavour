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
package org.teavm.flavour.templates.expr.type;

import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class GenericVariableBuilder extends GenericTypeBuilder {
    private GenericTypeBuilder upperBound;
    private GenericTypeBuilder lowerBound;

    GenericVariableBuilder(GenericTypeBuilder upperBound, GenericTypeBuilder lowerBound) {
        this.upperBound = upperBound;
        this.lowerBound = lowerBound;
    }

    public GenericTypeBuilder getUpperBound() {
        return upperBound;
    }

    public GenericTypeBuilder getLowerBound() {
        return lowerBound;
    }

    public static GenericVariableBuilder unbounded() {
        return new GenericVariableBuilder(null, null);
    }

    public static GenericVariableBuilder covariant(GenericTypeBuilder upperBound) {
        return new GenericVariableBuilder(upperBound, null);
    }

    public static GenericVariableBuilder contrvariant(GenericTypeBuilder lowerBound) {
        return new GenericVariableBuilder(null, lowerBound);
    }

    @Override
    GenericType buildCacheMiss(Map<GenericTypeBuilder, GenericType> cache) {
        GenericVariable var = new GenericVariable();
        cache.put(this, var);
        var.lowerBound = lowerBound != null ? lowerBound.build(cache) : null;
        var.upperBound = upperBound != null ? upperBound.build(cache) : null;
        return var;
    }
}
