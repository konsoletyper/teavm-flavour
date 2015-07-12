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
package org.teavm.flavour.templates.emitting;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.teavm.codegen.SourceWriter;
import org.teavm.javascript.Renderer;
import org.teavm.javascript.spi.Generator;
import org.teavm.javascript.spi.GeneratorContext;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class TemplatingRenderer implements Generator {
    Map<String, String> templateMapping = new HashMap<>();

    public TemplatingRenderer(Map<String, String> templateMapping) {
        this.templateMapping = templateMapping;
    }

    @Override
    public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) throws IOException {
        String typeVar = context.getParameterName(2);
        String modelVar = context.getParameterName(1);
        writer.append("switch ($rt_ustr(" + typeVar + ")) {").indent().softNewLine();
        for (Map.Entry<String, String> entry : templateMapping.entrySet()) {
            writer.append("case \"").append(Renderer.escapeString(entry.getKey())).append("\":")
                    .indent().softNewLine();
            writer.append("return ").append(writer.getNaming().getNameForInit(
                    new MethodReference(entry.getValue(), "<init>", ValueType.object(entry.getKey()),
                            ValueType.VOID)));
            writer.append("(" + modelVar + ");").softNewLine().outdent();
        }
        writer.outdent().append("}").softNewLine();
    }
}
