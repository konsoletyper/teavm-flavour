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

import org.teavm.flavour.expr.plan.LambdaPlan;

public class DirectiveFunctionBinding extends DirectivePropertyBinding {
    private LambdaPlan plan;
    private String lambdaType;

    public DirectiveFunctionBinding(String methodOwner, String methodName, LambdaPlan plan, String lambdaType) {
        super(methodOwner, methodName);
        this.plan = plan;
        this.lambdaType = lambdaType;
    }

    public LambdaPlan getPlan() {
        return plan;
    }

    public void setPlan(LambdaPlan plan) {
        this.plan = plan;
    }

    public String getLambdaType() {
        return lambdaType;
    }

    public void setLambdaType(String lambdaType) {
        this.lambdaType = lambdaType;
    }
}
