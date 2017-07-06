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

public class InvalidExpressionException extends RuntimeException {
    private static final long serialVersionUID = -1385228947330502798L;
    private final List<Diagnostic> diagnostics;

    public InvalidExpressionException(List<Diagnostic> diagnostics) {
        super(createMessage(diagnostics));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    private static String createMessage(List<Diagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Errors occurred compiling expression:");
        for (Diagnostic diagnostic : diagnostics) {
            sb.append("\n  at (" + diagnostic.getStart() + "; " + diagnostic.getEnd() + "): ")
                    .append(diagnostic.getMessage());
        }
        return sb.toString();
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }
}
