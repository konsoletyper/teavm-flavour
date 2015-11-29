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
import org.teavm.flavour.mp.ProxyGeneratorContext;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.parsing.ClassPathResourceProvider;
import org.teavm.flavour.templates.parsing.Parser;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.InstructionLocation;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;

/**
 *
 * @author Alexey Andreev
 */
public class TemplatingProxyGenerator /*implements ProxyGenerator*/ {
    private ProxyGeneratorContext context;

    //@Override
    public void setContext(ProxyGeneratorContext context) {
        this.context = context;
    }

    //@Override
    public void generate(String type, ProgramEmitter pe, CallLocation location) {
        TemplateEmitter emitter = new TemplateEmitter(context);
        TemplateInfo template = parseForModel(type, location);
        if (template != null) {
            String templateClass = emitter.emitTemplate(type, template.sourceFileName, template.body);
            pe.construct(templateClass, pe.var(1, ValueType.object(type))).returnValue();
        } else {
            pe.constantNull(ValueType.object(type)).returnValue();
        }
    }

    private TemplateInfo parseForModel(String typeName, CallLocation location) {
        ClassReader cls = /* context.getClassSource().get(typeName) */null;
        if (cls == null) {
            return null;
        }
        AnnotationReader annot = cls.getAnnotations().get(BindTemplate.class.getName());
        if (annot == null) {
            context.getDiagnostics().error(location, "Can't create template for {{c0}}: "
                    + "no BindTemplate annotation supplied", typeName);
            return null;
        }
        String path = annot.getValue("value").getString();
        ClassPathClassDescriberRepository classRepository = new ClassPathClassDescriberRepository(
                context.getClassLoader());
        ClassPathClassResolver classResolver = new ClassPathClassResolver(context.getClassLoader());
        ClassPathResourceProvider resourceProvider = new ClassPathResourceProvider(context.getClassLoader());
        Parser parser = new Parser(classRepository, classResolver, resourceProvider);
        List<TemplateNode> fragment;
        try (InputStream input = context.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }
             fragment = parser.parse(new InputStreamReader(input, "UTF-8"), typeName);
        } catch (IOException e) {
            context.getDiagnostics().error(location, "Can't create template for {{c0}}: "
                    + "template " + path + " was not found", typeName);
            return null;
        }
        if (!parser.getDiagnostics().isEmpty()) {
            OffsetToLineMapper mapper = new OffsetToLineMapper();
            try (Reader reader = new InputStreamReader(context.getClassLoader().getResourceAsStream(path), "UTF-8")) {
                mapper.prepare(reader);
            } catch (IOException e) {
                context.getDiagnostics().error(location, "Can't create template for {{c0}}: "
                        + "template " + path + " was not found", typeName);
                return null;
            }
            for (Diagnostic diagnostic : parser.getDiagnostics()) {
                InstructionLocation textualLocation = new InstructionLocation(path,
                        mapper.getLine(diagnostic.getStart()) + 1);
                CallLocation diagnosticLocation = new CallLocation(location.getMethod(), textualLocation);
                context.getDiagnostics().error(diagnosticLocation, diagnostic.getMessage());
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
