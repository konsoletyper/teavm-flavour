/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.flavour.expr;

import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.expr.ast.BoundVariable;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.ValueType;

public class TypeEstimator {
    private ClassResolver classResolver;
    private GenericTypeNavigator navigator;
    private Scope scope;

    public TypeEstimator(ClassResolver classResolver, GenericTypeNavigator navigator, Scope scope) {
        this.classResolver = classResolver;
        this.navigator = navigator;
        this.scope = scope;
    }

    public ValueType estimate(Expr<?> expr) {
        TypeEstimatorVisitor visitor = new TypeEstimatorVisitor(classResolver, navigator, scope);
        expr.acceptVisitor(visitor);
        return visitor.result;
    }

    public ValueType estimateLambda(LambdaExpr<?> expr, GenericMethod method) {
        BoundVarsScope innerScope = new BoundVarsScope();
        ValueType[] argTypes = method.getActualArgumentTypes();
        TypeInference inference = new TypeInference(navigator);
        for (int i = 0; i < expr.getBoundVariables().size(); ++i) {
            BoundVariable boundVar = expr.getBoundVariables().get(i);
            ValueType type = argTypes[i];
            if (boundVar.getType() != null) {
                if (!TypeUtil.subtype(boundVar.getType(), type, inference)) {
                    return null;
                }
            }
        }

        for (int i = 0; i < expr.getBoundVariables().size(); ++i) {
            BoundVariable boundVar = expr.getBoundVariables().get(i);
            ValueType type = argTypes[i].substitute(inference.getSubstitutions());
            innerScope.boundVars.put(boundVar.getName(), type);
        }

        TypeEstimatorVisitor visitor = new TypeEstimatorVisitor(classResolver, navigator, innerScope);
        expr.getBody().acceptVisitor(visitor);
        if (visitor.result == null) {
            return null;
        }
        if (!TypeUtil.subtype(method.getActualReturnType(), visitor.result, inference)) {
            return null;
        }

        return method.getActualReturnType().substitute(inference.getSubstitutions());
    }

    class BoundVarsScope implements Scope {
        Map<String, ValueType> boundVars = new HashMap<>();

        @Override
        public ValueType variableType(String variableName) {
            return boundVars.containsKey(variableName) ? boundVars.get(variableName)
                    : scope.variableType(variableName);
        }
    }
}
