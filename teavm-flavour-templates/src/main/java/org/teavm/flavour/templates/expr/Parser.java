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
import org.parboiled.Parboiled;
import org.parboiled.errors.ParseError;
import org.parboiled.parserunners.RecoveringParseRunner;
import org.parboiled.parserunners.TracingParseRunner;
import org.parboiled.support.ParsingResult;

/**
 *
 * @author Alexey Andreev
 */
public class Parser {
    private ClassSet classes;
    private List<SyntaxError> syntaxErrors = new ArrayList<>();

    public Parser(ClassSet classes) {
        this.classes = classes;
    }

    public Expr<Void> parse(String text) {
        syntaxErrors.clear();
        ExprParser parser = Parboiled.createParser(ExprParser.class);
        parser.importedClasses = classes;
        TracingParseRunner<Expr<Void>> runner = new TracingParseRunner<>(parser.Expression());
        ParsingResult<Expr<Void>> result = runner.run(text);
        for (ParseError error : result.parseErrors) {
            syntaxErrors.add(new SyntaxError(error.getStartIndex(), error.getEndIndex(), error.getErrorMessage()));
        }
        return result.resultValue;
    }

    public List<SyntaxError> getSyntaxErrors() {
        return syntaxErrors;
    }
}
