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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.teavm.flavour.expr.ClassPathClassResolver;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.parsing.ClassPathResourceProvider;
import org.teavm.flavour.templates.parsing.Parser;
import org.teavm.flavour.templates.tree.TemplateNode;

/**
 *
 * @author Alexey Andreev
 */
public class TemplatingProxyGenerator {

    public void generate(Emitter<Fragment> em, ReflectValue<Object> model) {
        TemplateEmitter emitter = new TemplateEmitter(em);
        TemplateInfo template = parseForModel(em, model.getReflectClass(), em.getContext().getLocation());
        if (template != null) {
            Value<Fragment> fragment = emitter.emitTemplate(model, template.sourceFileName, template.body);
            em.returnValue(fragment);
        } else {
            em.returnValue(() -> null);
        }
    }

    private TemplateInfo parseForModel(Emitter<Fragment> em, ReflectClass<Object> cls, SourceLocation location) {
        EmitterDiagnostics diagnostics = em.getContext().getDiagnostics();
        BindTemplate annot = cls.getAnnotation(BindTemplate.class);
        if (annot == null) {
            diagnostics.error(location, "Can't create template for {{c0}}: "
                    + "no BindTemplate annotation supplied", cls.getName());
            return null;
        }

        String path = annot.value();
        ClassLoader classLoader = em.getContext().getClassLoader();
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

        if (!parser.getDiagnostics().isEmpty()) {
            OffsetToLineMapper mapper = new OffsetToLineMapper();
            try (Reader reader = new InputStreamReader(classLoader.getResourceAsStream(path), "UTF-8")) {
                mapper.prepare(reader);
            } catch (IOException e) {
                diagnostics.error(location, "Can't create template for {{c0}}: template " + path
                        + " was not found", cls.getName());
                return null;
            }
            for (Diagnostic diagnostic : parser.getDiagnostics()) {
                SourceLocation diagnosticLocation = new SourceLocation(location.getMethod(), path,
                        mapper.getLine(diagnostic.getStart()) + 1);
                diagnostics.error(diagnosticLocation, diagnostic.getMessage());
            }
        }
        TemplateInfo info = new TemplateInfo();
        info.sourceFileName = path;
        info.body = fragment;
        return info;
    }

    static class TemplateInfo {
        String sourceFileName;
        List<TemplateNode> body;
    }
}
