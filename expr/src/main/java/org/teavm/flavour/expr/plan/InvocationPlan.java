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

public class InvocationPlan extends Plan {
    private String className;
    private String methodName;
    private String methodDesc;
    private Plan instance;
    private List<Plan> arguments = new ArrayList<>();

    public InvocationPlan(String className, String methodName, String methodDesc, Plan instance, Plan... arguments) {
        this(className, methodName, methodDesc, instance, Arrays.asList(arguments));
    }

    public InvocationPlan(String className, String methodName, String methodDesc, Plan instance,
            List<Plan> arguments) {
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.instance = instance;
        this.arguments.addAll(arguments);
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public void setMethodDesc(String methodDesc) {
        this.methodDesc = methodDesc;
    }

    public Plan getInstance() {
        return instance;
    }

    public void setInstance(Plan instance) {
        this.instance = instance;
    }

    public List<Plan> getArguments() {
        return arguments;
    }

    @Override
    public void acceptVisitor(PlanVisitor visitor) {
        visitor.visit(this);
    }
}
