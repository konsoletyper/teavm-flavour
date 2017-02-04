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
package org.teavm.flavour.routing.parsing;

import static org.teavm.metaprogramming.Metaprogramming.emit;
import static org.teavm.metaprogramming.Metaprogramming.exit;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import org.teavm.flavour.regex.Pattern;
import org.teavm.flavour.regex.automata.Dfa;
import org.teavm.flavour.regex.bytecode.MatcherClassBuilder;
import org.teavm.flavour.routing.parsing.PathParser.PathParserCase;
import org.teavm.flavour.routing.parsing.PathParser.PathParserElement;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class PathParserEmitter {
    private int dfaIndexGenerator;

    public Value<PathParser> emitWorker(PathParser pathParser) {
        int caseCount = pathParser.cases.length;
        Value<PathParserCase[]> casesCopy = emit(() -> new PathParserCase[caseCount]);
        for (int i = 0; i < pathParser.cases.length; ++i) {
            PathParserCase parserCase = pathParser.cases[i];

            int elementCount = parserCase.elements.length;
            Value<PathParserElement[]> elementsCopy = emit(() -> new PathParserElement[elementCount]);
            for (int j = 0; j < parserCase.elements.length; ++j) {
                PathParserElement element = parserCase.elements[j];
                int suffixLength = element.suffixLength;
                Value<Pattern> pattern = createPattern(element.patternDfa);
                int index = j;
                emit(() -> {
                    PathParserElement elementCopy = new PathParserElement();
                    elementCopy.suffixLength = suffixLength;
                    elementCopy.pattern = pattern.get();
                    elementsCopy.get()[index] = elementCopy;
                });
            }

            int prefixLength = parserCase.prefixLength;
            int index = i;
            emit(() -> {
                PathParserCase caseCopy = new PathParserCase();
                caseCopy.elements = elementsCopy.get();
                caseCopy.prefixLength = prefixLength;
                casesCopy.get()[index] = caseCopy;
            });
        }

        Value<Pattern> caseSelector = createPattern(pathParser.caseSelectorDfa);
        return emit(() -> new PathParser(casesCopy.get(), caseSelector.get()));
    }

    public Value<Pattern> createPattern(Dfa dfa) {
        MatcherClassBuilder classBuilder = new MatcherClassBuilder();
        byte[] bytecode = classBuilder.build("org.teavm.flavour.routing.internal.Matcher" + dfaIndexGenerator++, dfa);
        ReflectClass<?> matcherClass = Metaprogramming.createClass(bytecode);
        ReflectMethod constructor = matcherClass.getMethod("<init>");
        return proxy(Pattern.class, (instance, method, args) -> {
            exit(() -> constructor.construct());
        });
    }
}
