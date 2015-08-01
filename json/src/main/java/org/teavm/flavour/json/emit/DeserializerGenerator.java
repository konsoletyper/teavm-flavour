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
package org.teavm.flavour.json.emit;

import java.io.IOException;
import org.teavm.codegen.SourceWriter;
import org.teavm.javascript.Renderer;
import org.teavm.javascript.spi.Generator;
import org.teavm.javascript.spi.GeneratorContext;
import org.teavm.model.ListableClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class DeserializerGenerator implements Generator {
    private DeserializerDependencyListener dependencyListener;

    public DeserializerGenerator(DeserializerDependencyListener dependencyListener) {
        this.dependencyListener = dependencyListener;
    }

    @Override
    public void generate(GeneratorContext context, SourceWriter writer, MethodReference methodRef) throws IOException {
        writer.append("switch ($rt_ustr(").append(context.getParameterName(1)).append(")) {").indent().softNewLine();

        ListableClassReaderSource classSource = context.getClassSource();
        for (String className : classSource.getClassNames()) {
            String deserializer = dependencyListener.getDeserializer(className);
            if (deserializer != null) {
                MethodReference ctor = new MethodReference(deserializer, "<init>", ValueType.VOID);
                writer.append("case \"").append(Renderer.escapeString(className)).append("\":")
                        .indent().softNewLine();
                writer.append("return ").append(writer.getNaming().getNameForInit(ctor)).append("();").softNewLine();
                writer.outdent();
            }
        }

        writer.append("default:").indent().softNewLine();
        writer.append("return null;").softNewLine().outdent();

        writer.outdent().append("}").softNewLine();
    }
}
