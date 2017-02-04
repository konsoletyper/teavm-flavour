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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.teavm.flavour.regex.Matcher;
import org.teavm.flavour.regex.Pattern;
import org.teavm.flavour.regex.ast.ConcatNode;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.automata.Dfa;

public class PathParser {
    PathParserCase[] cases;
    Dfa caseSelectorDfa;
    Pattern caseSelector;
    boolean prepared;

    PathParser(PathParserCase[] cases, Pattern caseSelector) {
        this.cases = cases;
        this.caseSelector = caseSelector;
        prepared = true;
    }

    PathParser(List<PathBuilder> pathBuilders) {
        List<PathParserCase> cases = new ArrayList<>();

        ConcatNode[] caseNodes = new ConcatNode[pathBuilders.size()];
        Arrays.setAll(caseNodes, i -> new ConcatNode());
        for (int i = 0; i < pathBuilders.size(); ++i) {
            PathBuilder pathBuilder = pathBuilders.get(i);
            ConcatNode caseNode = caseNodes[i];
            StringBuilder sb = new StringBuilder();
            List<ConcatNode> parserCaseNodes = new ArrayList<>();
            List<PathParserElement> elements = new ArrayList<>();
            int prefixLength = 0;
            for (int j = 0; j <= pathBuilder.elements.size(); ++j) {
                PathElement builderElem = j < pathBuilder.elements.size() ? pathBuilder.elements.get(j)
                        : new PathElement((Node) null);

                if (builderElem.text != null) {
                    sb.append(builderElem.text);
                } else {
                    if (sb.length() > 0) {
                        caseNode.getSequence().add(Node.text(sb.toString()));
                        if (elements.isEmpty()) {
                            prefixLength = sb.length();
                        } else {
                            parserCaseNodes.get(parserCaseNodes.size() - 1).getSequence()
                                    .add(Node.text(sb.toString()));
                            elements.get(elements.size() - 1).suffixLength = sb.length();
                        }
                        sb.setLength(0);
                    }
                    if (builderElem.regex != null) {
                        caseNode.getSequence().add(builderElem.regex);
                        parserCaseNodes.add(new ConcatNode(builderElem.regex));
                        elements.add(new PathParserElement());
                    } else {
                        caseNode.getSequence().add(Node.eof());
                    }
                }
            }

            for (int j = 0; j < elements.size(); ++j) {
                elements.get(j).patternDfa = Dfa.fromNode(parserCaseNodes.get(j));
            }

            PathParserCase parserCase = new PathParserCase();
            parserCase.prefixLength = prefixLength;
            parserCase.elements = elements.toArray(new PathParserElement[elements.size()]);
            cases.add(parserCase);
        }
        caseSelectorDfa = Dfa.fromNodes(caseNodes);

        this.cases = cases.toArray(new PathParserCase[cases.size()]);
    }

    public PathParser prepare() {
        if (prepared) {
            return this;
        }
        prepared = true;
        caseSelector = caseSelectorDfa.compile();
        for (PathParserCase parserCase : cases) {
            for (PathParserElement elem : parserCase.elements) {
                elem.pattern = elem.patternDfa.compile();
            }
        }
        return this;
    }

    public PathParserResult parse(String path) {
        Matcher matcher = caseSelector.matcher();
        matcher.feed(path).end();
        if (matcher.getDomain() < 0) {
            return new PathParserResult(-1, new int[0], new int[0]);
        }

        PathParserCase parserCase = cases[matcher.getDomain()];
        PathParserElement[] elements = parserCase.elements;
        int[] startIndexes = new int[elements.length];
        int[] endIndexes = new int[startIndexes.length];
        if (elements.length > 0) {
            Matcher[] matchers = new Matcher[elements.length];
            for (int j = 0; j < matchers.length; ++j) {
                matchers[j] = elements[j].pattern.matcher();
            }
            startIndexes[0] = parserCase.prefixLength;
            endIndexes[0] = parserCase.prefixLength;
            int i = 0;
            while (i < elements.length) {
                int next = matchers[i].eat(path, endIndexes[i]);
                if (next == -1) {
                    matchers[i--].restart();
                } else  {
                    endIndexes[i] = next;
                    if (i + 1 < elements.length) {
                        startIndexes[++i] = next;
                        endIndexes[i] = next;
                    } else if (next == path.length()) {
                        break;
                    }
                }
            }

            for (i = 0; i < endIndexes.length; ++i) {
                endIndexes[i] -= elements[i].suffixLength;
            }
        }

        return new PathParserResult(matcher.getDomain(), startIndexes, endIndexes);
    }

    static class PathParserCase {
        int prefixLength;
        PathParserElement[] elements;
    }

    static class PathParserElement {
        Dfa patternDfa;
        Pattern pattern;
        int suffixLength;
    }
}
