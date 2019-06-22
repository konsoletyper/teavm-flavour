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

import static org.teavm.metaprogramming.Metaprogramming.exit;
import static org.teavm.metaprogramming.Metaprogramming.getClassLoader;
import static org.teavm.metaprogramming.Metaprogramming.getDiagnostics;
import static org.teavm.metaprogramming.Metaprogramming.unsupportedCase;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.teavm.flavour.expr.ClassPathClassResolver;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.parsing.ClassPathResourceProvider;
import org.teavm.flavour.templates.parsing.Parser;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.metaprogramming.Diagnostics;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.SourceLocation;
import org.teavm.metaprogramming.Value;

public class TemplatingProxyGenerator {
    public void generate(Value<Object> model, ReflectClass<Object> modelType) {
        TemplateInfo template = parseForModel(modelType, Metaprogramming.getLocation());
        if (template != null) {
            TemplateEmitter emitter = new TemplateEmitter(template.locationMapper);
            Value<Fragment> fragment = emitter.emitTemplate(model, template.sourceFileName, template.body);
            exit(() -> fragment.get());
        } else {
            unsupportedCase();
        }
    }

    private TemplateInfo parseForModel(ReflectClass<Object> cls, SourceLocation location) {
        Diagnostics diagnostics = getDiagnostics();
        BindTemplate annot = cls.getAnnotation(BindTemplate.class);
        if (annot == null) {
            return null;
        }

        String path = annot.value();
        ClassLoader classLoader = getClassLoader();
        ClassPathClassDescriberRepository classRepository = new ClassPathClassDescriberRepository(classLoader);
        ClassPathClassResolver classResolver = new ClassPathClassResolver(classLoader);
        ClassPathResourceProvider resourceProvider = new ClassPathResourceProvider(classLoader);
        Parser parser = new Parser(classRepository, classResolver, resourceProvider);
        List<TemplateNode> fragment;
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                diagnostics.error(location, "Can't create template for {{c0}}: " + "template " + path
                        + " was not found", cls.getName());
                return null;
            }
            fragment = parser.parse(new InputStreamReader(input, "UTF-8"), cls.getName());
        } catch (IOException e) {
            diagnostics.error(location, "Can't create template for {{c0}}: " + "template " + path
                    + " was not found", cls.getName());
            return null;
        }

        OffsetToLineMapper mapper = new OffsetToLineMapper();
        try (Reader reader = new InputStreamReader(classLoader.getResourceAsStream(path), "UTF-8")) {
            mapper.prepare(reader);
        } catch (IOException e) {
            diagnostics.error(location, "Can't create template for {{c0}}: template " + path
                    + " was not found", cls.getName());
            return null;
        }

        if (!parser.getDiagnostics().isEmpty()) {
            for (Diagnostic diagnostic : parser.getDiagnostics()) {
                SourceLocation diagnosticLocation = location != null ? new SourceLocation(location.getMethod(), path,
                        mapper.getLine(diagnostic.getStart()) + 1) : null;
                diagnostics.error(diagnosticLocation, diagnostic.getMessage());
            }
        }

        TemplateInfo info = new TemplateInfo();
        info.sourceFileName = path;
        info.body = fragment;
        info.locationMapper = mapper;
        return info;
    }

    static class TemplateInfo {
        String sourceFileName;
        OffsetToLineMapper locationMapper;
        List<TemplateNode> body;
    }
}
