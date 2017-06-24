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

public class GenericMethod {
    private MethodDescriber describer;
    private GenericClass actualOwner;
    private ValueType[] actualParameterTypes;
    private ValueType actualReturnType;

    public GenericMethod(MethodDescriber describer, GenericClass actualOwner,
            ValueType[] actualParameterTypes, ValueType actualReturnType) {
        if (describer.getParameterTypes().length != actualParameterTypes.length) {
            throw new IllegalArgumentException();
        }
        if ((actualReturnType == null) != (describer.getReturnType() == null)) {
            throw new IllegalArgumentException();
        }
        this.describer = describer;
        this.actualOwner = actualOwner;
        this.actualParameterTypes = actualParameterTypes;
        this.actualReturnType = actualReturnType;
    }

    public GenericClass getActualOwner() {
        return actualOwner;
    }

    public MethodDescriber getDescriber() {
        return describer;
    }

    public ValueType[] getActualParameterTypes() {
        return actualParameterTypes.clone();
    }

    public ValueType getActualReturnType() {
        return actualReturnType;
    }

    public GenericMethod substitute(Substitutions substitutions) {
        GenericClass actualOwner = this.actualOwner.substitute(substitutions);
        ValueType[] actualParameterTypes = this.actualParameterTypes.clone();
        for (int i = 0; i < actualParameterTypes.length; ++i) {
            if (actualParameterTypes[i] instanceof GenericType) {
                actualParameterTypes[i] = ((GenericType) actualParameterTypes[i]).substitute(substitutions);
            }
        }
        ValueType actualReturnType = this.actualReturnType;
        if (actualReturnType instanceof GenericType) {
            actualReturnType = ((GenericType) actualReturnType).substitute(substitutions);
        }
        return new GenericMethod(describer, actualOwner, actualParameterTypes, actualReturnType);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(describer.getName()).append('(');
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        if (actualParameterTypes.length > 0) {
            formatter.format(actualParameterTypes[0], sb);
            for (int i = 1; i < actualParameterTypes.length; ++i) {
                sb.append(", ");
                formatter.format(actualParameterTypes[i], sb);
            }
        }
        sb.append(')');
        return sb.toString();
    }
}
