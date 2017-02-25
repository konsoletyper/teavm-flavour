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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.teavm.flavour.expr.ast.AssignmentExpr;
import org.teavm.flavour.expr.ast.BoundVariable;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.ExprCopier;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.ast.PropertyExpr;
import org.teavm.flavour.expr.ast.VariableExpr;
import org.teavm.flavour.expr.type.*;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;

/**
 * <p>Compiles AST into execution plan, performing type checks.</p>
 *
 * @author Alexey Andreev
 */
public class Compiler {
    private ClassResolver classResolver;
    private Scope scope;
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private List<Diagnostic> safeDiagnostics = Collections.unmodifiableList(diagnostics);
    private GenericTypeNavigator typeNavigator;

    public Compiler(ClassDescriberRepository classRepository, ClassResolver classResolver, Scope scope) {
        this.classResolver = classResolver;
        this.scope = scope;
        this.typeNavigator = new GenericTypeNavigator(classRepository);
    }

    /**
     * <p>Compiles AST of expression into execution plan. Equivalent to <code>compile(expr, null)</code>.
     *
     * @param expr expression AST to compile.
     * @return evaluation plan and its type.
     */
    public TypedPlan compile(Expr<?> expr) {
        return compile(expr, null);
    }

    /**
     * <p>Compiles AST of expression to execution plan. If during compilation there were type errors,
     * {@link #wasSuccessful()} method will return <code>false</code> and {@link #getDiagnostics()} will
     * return the full list of found errors. Tries to implicitly cast expression result to the given type,
     * if specified. If this cast is unsuccessful, compiler reports type error.</p>
     *
     * @param expr expression AST to compile.
     * @param type if not null, compiler will try to cast expression result to this type.
     * @return evaluation plan and its type.
     */
    public TypedPlan compile(Expr<?> expr, ValueType type) {
        diagnostics.clear();
        ExprCopier<TypedPlan> copier = new ExprCopier<>();
        expr.acceptVisitor(copier);
        Expr<TypedPlan> attributedExpr = copier.getResult();
        CompilerVisitor visitor = new CompilerVisitor(typeNavigator, classResolver, scope);
        visitor.expectedType = type;
        attributedExpr.acceptVisitor(visitor);
        if (type != null) {
            visitor.convert(attributedExpr, type);
        }
        diagnostics.addAll(visitor.getDiagnostics());
        return attributedExpr.getAttribute();
    }

    public TypedPlan compileLambda(Expr<?> expr, GenericClass cls) {
        if (!(expr instanceof LambdaExpr<?>)) {
            GenericMethod sam = typeNavigator.findSingleAbstractMethod(cls);
            if (sam.getActualReturnType() == null && sam.getActualArgumentTypes().length == 1
                    && (expr instanceof VariableExpr || expr instanceof PropertyExpr)) {
                BoundVariable var = new BoundVariable("$value$", sam.getActualArgumentTypes()[0]);
                AssignmentExpr<?> assignment = new AssignmentExpr<>(expr, new VariableExpr<>("$value$"));
                assignment.setStart(expr.getStart());
                assignment.setEnd(expr.getEnd());
                LambdaExpr<?> lambda = new LambdaExpr<>(assignment, Arrays.asList(var));
                lambda.setStart(expr.getStart());
                lambda.setEnd(expr.getEnd());
                expr = lambda;
            } else {
                List<BoundVariable> boundVars = new ArrayList<>();
                if (sam.getActualArgumentTypes().length == 1) {
                    boundVars.add(new BoundVariable("it", sam.getActualArgumentTypes()[0]));
                } else {
                    for (ValueType arg : sam.getActualArgumentTypes()) {
                        boundVars.add(new BoundVariable("", arg));
                    }
                }
                LambdaExpr<?> lambda = new LambdaExpr<>(expr, boundVars);
                lambda.setStart(expr.getStart());
                lambda.setEnd(expr.getEnd());
                expr = lambda;
            }
        }
        return compile(expr, cls);
    }

    public List<Diagnostic> getDiagnostics() {
        return safeDiagnostics;
    }

    public boolean wasSuccessful() {
        return diagnostics.isEmpty();
    }
}
