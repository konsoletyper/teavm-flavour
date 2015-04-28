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
package org.teavm.flavour.templates.tree;

import org.teavm.flavour.expr.TypedPlan;

/**
 *
 * @author Alexey Andreev
 */
public class DirectiveComputationBinding extends DirectivePropertyBinding {
    private TypedPlan computationPlan;

    public DirectiveComputationBinding(String methodOwner, String methodName, TypedPlan computationPlan) {
        super(methodOwner, methodName);
        this.computationPlan = computationPlan;
    }

    public TypedPlan getComputationPlan() {
        return computationPlan;
    }

    public void setComputationPlan(TypedPlan computationPlan) {
        this.computationPlan = computationPlan;
    }
}
