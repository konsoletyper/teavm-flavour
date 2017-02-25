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
package org.teavm.flavour.expr.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConstructionPlan extends Plan {
    private String className;
    private String methodDesc;
    private List<Plan> arguments = new ArrayList<>();

    public ConstructionPlan(String className, String methodDesc, Plan... arguments) {
        this(className, methodDesc, Arrays.asList(arguments));
    }

    public ConstructionPlan(String className, String methodDesc, List<Plan> arguments) {
        this.className = className;
        this.methodDesc = methodDesc;
        this.arguments.addAll(arguments);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public void setMethodDesc(String methodDesc) {
        this.methodDesc = methodDesc;
    }

    public List<Plan> getArguments() {
        return arguments;
    }

    @Override
    public void acceptVisitor(PlanVisitor visitor) {
        visitor.visit(this);
    }
}
