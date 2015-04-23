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
package org.teavm.flavour.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.type.ClassDescriberRepository;
import org.teavm.flavour.expr.type.GenericTypeNavigator;

/**
 *
 * @author Alexey Andreev
 */
public class Compiler {
    private ClassDescriberRepository classRepository;
    private Scope scope;
    private List<CompilerDiagnostic> diagnostics = new ArrayList<>();
    private List<CompilerDiagnostic> safeDiagnostics = Collections.unmodifiableList(diagnostics);

    public Compiler(ClassDescriberRepository classRepository, Scope scope) {
        this.classRepository = classRepository;
        this.scope = scope;
    }

    public TypedPlan compile(Expr<?> expr) {
        diagnostics.clear();
        ExprCopier<TypedPlan> copier = new ExprCopier<>();
        expr.acceptVisitor(copier);
        Expr<TypedPlan> attributedExpr = copier.getResult();
        CompilerVisitor visitor = new CompilerVisitor(new GenericTypeNavigator(classRepository), scope);
        attributedExpr.acceptVisitor(visitor);
        diagnostics.addAll(visitor.getDiagnostics());
        return attributedExpr.getAttribute();
    }

    public List<CompilerDiagnostic> getDiagnostics() {
        return safeDiagnostics;
    }

    public boolean wasSuccessful() {
        return diagnostics.isEmpty();
    }
}
