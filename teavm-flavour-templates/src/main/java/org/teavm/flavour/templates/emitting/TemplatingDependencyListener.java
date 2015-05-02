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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyConsumer;
import org.teavm.dependency.DependencyListener;
import org.teavm.dependency.DependencyType;
import org.teavm.dependency.FieldDependency;
import org.teavm.dependency.MethodDependency;
import org.teavm.flavour.expr.ClassPathClassResolver;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Templates;
import org.teavm.flavour.templates.parsing.ClassPathResourceProvider;
import org.teavm.flavour.templates.parsing.Parser;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class TemplatingDependencyListener implements DependencyListener {
    Map<String, String> templateMapping = new HashMap<>();

    @Override
    public void started(DependencyAgent agent) {
    }

    @Override
    public void classAchieved(DependencyAgent agent, String className, CallLocation location) {
    }

    @Override
    public void methodAchieved(final DependencyAgent agent, final MethodDependency method,
            final CallLocation location) {
        if (method.getReference().getClassName().equals(Templates.class.getName()) &&
                method.getReference().getName().equals("createImpl")) {
            method.getVariable(1).addConsumer(new DependencyConsumer() {
                @Override public void consume(DependencyType type) {
                    TemplateEmitter emitter = new TemplateEmitter(agent);
                    List<TemplateNode> fragment = parseForModel(agent, type.getName(), location);
                    if (fragment != null) {
                        String templateClass = emitter.emitTemplate(type.getName(), fragment);
                        agent.linkClass(templateClass, location);
                        MethodDependency ctor = agent.linkMethod(new MethodReference(templateClass, "<init>",
                                ValueType.object(type.getName()), ValueType.VOID), location);
                        ctor.getVariable(0).propagate(agent.getType(templateClass));
                        ctor.getVariable(1).propagate(type);
                        ctor.use();
                        method.getResult().propagate(agent.getType(templateClass));
                        templateMapping.put(type.getName(), templateClass);
                    }
                }
            });
        }
    }

    private List<TemplateNode> parseForModel(DependencyAgent agent, String typeName, CallLocation location) {
        ClassReader cls = agent.getClassSource().get(typeName);
        if (cls == null) {
            return null;
        }
        AnnotationReader annot = cls.getAnnotations().get(BindTemplate.class.getName());
        if (annot == null) {
            agent.getDiagnostics().error(location, "Can't create template for {{c0}}: " +
                    "no BindTemplate annotation supplied", typeName);
            return null;
        }
        String path = annot.getValue("value").getString();
        try (InputStream input = agent.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }
            ClassPathClassDescriberRepository classRepository = new ClassPathClassDescriberRepository(
                    agent.getClassLoader());
            ClassPathClassResolver classResolver = new ClassPathClassResolver(agent.getClassLoader());
            ClassPathResourceProvider resourceProvider = new ClassPathResourceProvider(agent.getClassLoader());
            Parser parser = new Parser(classRepository, classResolver, resourceProvider);
            List<TemplateNode> fragment = parser.parse(new InputStreamReader(input, "UTF-8"), typeName);
            parser.getDiagnostics();
            return fragment;
        } catch (IOException e) {
            agent.getDiagnostics().error(location, "Can't create template for {{c0}}: " +
                    "template " + path + " was not found", typeName);
            return null;
        }
    }

    @Override
    public void fieldAchieved(DependencyAgent agent, FieldDependency field, CallLocation location) {
    }
}
