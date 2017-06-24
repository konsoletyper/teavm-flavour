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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.expr.ast.BoundVariable;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeInferenceStatePoint;
import org.teavm.flavour.expr.type.ValueType;

public class TypeEstimator {
    private TypeInference inference;
    private ClassResolver classResolver;
    private GenericTypeNavigator navigator;
    private Scope scope;

    public TypeEstimator(TypeInference inference, ClassResolver classResolver, GenericTypeNavigator navigator,
            Scope scope) {
        this.inference = inference;
        this.classResolver = classResolver;
        this.navigator = navigator;
        this.scope = scope;
    }

    public ValueType estimate(Expr expr, ValueType expectedType) {
        TypeEstimatorVisitor visitor = new TypeEstimatorVisitor(inference, classResolver, navigator, scope);
        visitor.expectedType = expectedType;
        return expr.acceptVisitor(visitor);
    }

    public ValueType estimateLambda(LambdaExpr expr, GenericMethod method) {
        try (TypeInferenceStatePoint ignored = inference.createStatePoint()) {
            BoundVarsScope innerScope = new BoundVarsScope();
            ValueType[] paramTypes = method.getActualParameterTypes();

            if (!inference.addVariables(Arrays.asList(method.getDescriber().getTypeVariables()))) {
                return null;
            }

            for (int i = 0; i < expr.getBoundVariables().size(); ++i) {
                BoundVariable boundVar = expr.getBoundVariables().get(i);
                ValueType type = paramTypes[i];
                if (boundVar.getType() != null) {
                    if (!inference.subtypeConstraint(boundVar.getType(), type)) {
                        return null;
                    }
                }
            }

            if (!inference.resolve()) {
                return null;
            }

            for (int i = 0; i < expr.getBoundVariables().size(); ++i) {
                BoundVariable boundVar = expr.getBoundVariables().get(i);
                ValueType paramType = paramTypes[i];
                if (paramType instanceof GenericType) {
                    paramType = ((GenericType) paramType).substitute(inference.getSubstitutions());
                }
                innerScope.boundVars.put(boundVar.getName(), paramType);
            }

            TypeEstimatorVisitor visitor = new TypeEstimatorVisitor(inference, classResolver, navigator, innerScope);
            ValueType result = expr.getBody().acceptVisitor(visitor);
            if (result == null) {
                return null;
            }
            if (!CompilerCommons.isLooselyCompatibleType(result, method.getActualReturnType(), navigator)) {
                return null;
            }
        }

        return method.getActualReturnType();
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
