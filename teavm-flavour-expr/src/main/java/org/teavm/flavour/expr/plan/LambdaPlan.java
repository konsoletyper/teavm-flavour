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
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class LambdaPlan extends Plan {
    private List<String> boundVars = new ArrayList<>();
    private Plan body;
    private String className;
    private String methodName;
    private String methodDesc;

    public LambdaPlan(Plan body, String className, String methodName, String methodDesc, List<String> boundVars) {
        this.body = body;
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.boundVars.addAll(boundVars);
    }

    public Plan getBody() {
        return body;
    }

    public void setBody(Plan body) {
        this.body = body;
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

    public List<String> getBoundVars() {
        return boundVars;
    }

    @Override
    public void acceptVisitor(PlanVisitor visitor) {
        visitor.visit(this);
    }
}
