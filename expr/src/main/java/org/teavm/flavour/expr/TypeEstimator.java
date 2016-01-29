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

import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class TypeEstimator {
    private GenericTypeNavigator navigator;
    private Scope scope;

    public TypeEstimator(GenericTypeNavigator navigator, Scope scope) {
        this.navigator = navigator;
        this.scope = scope;
    }

    public ValueType estimate(Expr<?> expr) {
        TypeEstimatorVisitor visitor = new TypeEstimatorVisitor(navigator, scope);
        expr.acceptVisitor(visitor);
        return visitor.result;
    }
}
