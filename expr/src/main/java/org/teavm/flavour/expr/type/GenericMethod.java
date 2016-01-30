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

import org.teavm.flavour.expr.type.meta.MethodDescriber;

/**
 *
 * @author Alexey Andreev
 */
public class GenericMethod {
    private MethodDescriber describer;
    private GenericClass actualOwner;
    private ValueType[] actualArgumentTypes;
    private ValueType actualReturnType;

    public GenericMethod(MethodDescriber describer, GenericClass actualOwner,
            ValueType[] actualArgumentTypes, ValueType actualReturnType) {
        if (describer.getArgumentTypes().length != actualArgumentTypes.length) {
            throw new IllegalArgumentException();
        }
        if ((actualReturnType == null) != (describer.getReturnType() == null)) {
            throw new IllegalArgumentException();
        }
        this.describer = describer;
        this.actualOwner = actualOwner;
        this.actualArgumentTypes = actualArgumentTypes;
        this.actualReturnType = actualReturnType;
    }

    public GenericClass getActualOwner() {
        return actualOwner;
    }

    public MethodDescriber getDescriber() {
        return describer;
    }

    public ValueType[] getActualArgumentTypes() {
        return actualArgumentTypes.clone();
    }

    public ValueType getActualReturnType() {
        return actualReturnType;
    }

    public GenericMethod substitute(Substitutions substitutions) {
        GenericClass actualOwner = this.actualOwner.substitute(substitutions);
        ValueType[] actualArgumentTypes = this.actualArgumentTypes.clone();
        for (int i = 0; i < actualArgumentTypes.length; ++i) {
            actualArgumentTypes[i] = actualArgumentTypes[i].substitute(substitutions);
        }
        ValueType actualReturnType = this.actualReturnType != null
                ? this.actualReturnType.substitute(substitutions)
                : null;
        return new GenericMethod(describer, actualOwner, actualArgumentTypes, actualReturnType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(describer.getName()).append('(');
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        if (actualArgumentTypes.length > 0) {
            formatter.format(actualArgumentTypes[0], sb);
            for (int i = 1; i < actualArgumentTypes.length; ++i) {
                sb.append(", ");
                formatter.format(actualArgumentTypes[i], sb);
            }
        }
        sb.append(')');
        return sb.toString();
    }
}
