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
import java.util.List;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.parsing.RegexParser;

public class PathBuilder {
    private RegexParser regexParser;
    List<PathElement> elements = new ArrayList<>();

    PathBuilder(RegexParser regexParser) {
        this.regexParser = regexParser;
    }

    public PathBuilder text(String text) {
        elements.add(new PathElement(text));
        return this;
    }

    public PathBuilder regex(Node regex) {
        elements.add(new PathElement(regex));
        return this;
    }

    public PathBuilder regex(String regex) {
        return regex(regexParser.parse(regex));
    }

    public PathBuilder escapedRegex(Node regex) {
        return regex(RegexTransformer.escape(regex));
    }

    public PathBuilder escapedRegex(String regex) {
        return escapedRegex(regexParser.parse(regex));
    }
}
