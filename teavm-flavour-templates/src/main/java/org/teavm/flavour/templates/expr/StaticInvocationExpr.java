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
package org.teavm.flavour.templates.expr;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class StaticInvocationExpr<T> extends Expr<T> {
    private String className;
    private String methodName;
    private List<Expr<T>> arguments = new ArrayList<>();

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

    public List<Expr<T>> getArguments() {
        return arguments;
    }

    @Override
    public void acceptVisitor(ExprVisitor<? super T> visitor) {
        visitor.visit(this);
    }
}
