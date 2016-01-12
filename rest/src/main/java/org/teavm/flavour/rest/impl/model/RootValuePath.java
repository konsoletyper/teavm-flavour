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
package org.teavm.flavour.rest.impl.model;

import org.teavm.flavour.mp.ReflectClass;

/**
 *
 * @author Alexey Andreev
 */
public class RootValuePath extends ValuePath {
    private ParameterModel parameter;

    RootValuePath(ParameterModel parameter) {
        this.parameter = parameter;
    }

    @Override
    public Usage getUsage() {
        return parameter.usage;
    }

    @Override
    public String getName() {
        return parameter.name;
    }

    @Override
    public ValuePath getParent() {
        return null;
    }

    public ParameterModel getParameter() {
        return parameter;
    }

    @Override
    public ReflectClass<?> getType() {
        return parameter.type;
    }

    @Override
    public String toString() {
        return "$" + parameter.index;
    }

    @Override
    public void acceptVisitor(ValuePathVisitor visitor) {
        visitor.visit(this);
    }
}
