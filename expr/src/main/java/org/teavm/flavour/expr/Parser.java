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
import java.util.List;
import org.parboiled.Parboiled;
import org.parboiled.errors.ParseError;
import org.parboiled.parserunners.RecoveringParseRunner;
import org.parboiled.support.ParsingResult;
import org.teavm.flavour.expr.ExprParser.Holder;
import org.teavm.flavour.expr.ast.ConstantExpr;
import org.teavm.flavour.expr.ast.Expr;

/**
 *
 * @author Alexey Andreev
 */
public class Parser {
    private ClassResolver classes;
    private List<Diagnostic> diagnostics = new ArrayList<>();

    public Parser(ClassResolver classes) {
        this.classes = classes;
    }

    public Expr<Void> parse(String text) {
        diagnostics.clear();
        ExprParser parser = Parboiled.createParser(ExprParser.class);
        parser.classResolver = classes;
        RecoveringParseRunner<Holder> runner = new RecoveringParseRunner<>(parser.Root());
        ParsingResult<Holder> result = runner.run(text);
        for (ParseError error : result.parseErrors) {
            diagnostics.add(new Diagnostic(error.getStartIndex(), error.getEndIndex(),
                    error.getErrorMessage() != null ? error.getErrorMessage() : ""));
        }
        if (!diagnostics.isEmpty() && result.resultValue == null) {
            return new ConstantExpr<>(null);
        }
        return result.resultValue.expr;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }
}
