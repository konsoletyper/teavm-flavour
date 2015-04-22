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
package org.teavm.flavour.templates.expr.ast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public final class ExprType implements Cloneable {
    private String className;
    private List<ExprType> genericArguments;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<ExprType> getGenericArguments() {
        return genericArguments;
    }

    @Override
    protected ExprType clone() {
        return copy(new HashMap<ExprType, ExprType>());
    }

    protected ExprType copy(Map<ExprType, ExprType> knownCopies) {
        ExprType copy = knownCopies.get(this);
        if (copy == null) {
            copy = new ExprType();
            copy.className = className;
            knownCopies.put(this, copy);
            for (ExprType arg : genericArguments) {
                copy.genericArguments.add(arg.copy(knownCopies));
            }
        }
        return copy;
    }
}
