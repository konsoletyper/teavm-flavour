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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.flavour.json.JSON;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class DeserializerDependencyListener extends AbstractDependencyListener {
    private JsonDeserializerEmitter emitter;
    private boolean generated;

    @Override
    public void methodReached(final DependencyAgent agent, final MethodDependency method,
            final CallLocation location) {
        if (method.getReference().getClassName().equals(JSON.class.getName()) &&
                method.getReference().getName().equals("findClassDeserializer")) {
            emitter = new JsonDeserializerEmitter(agent);
            generateDeserializers(agent, method, location);
        }
    }

    private void generateDeserializers(DependencyAgent agent, MethodDependency caller, CallLocation location) {
        if (generated) {
            return;
        }
        generated = true;

        try {
            Enumeration<URL> resources = agent.getClassLoader().getResources("META-INF/flavour/deserializable");
            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.openStream()))) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        ClassReader cls = agent.getClassSource().get(line);
                        if (cls == null) {
                            agent.getDiagnostics().warning(location, "Can't find class {{c0}} declared by " +
                                    res.toString(), line);
                        }

                        String deserializerName = emitter.addClassDeserializer(line);
                        agent.linkMethod(new MethodReference(deserializerName, "<init>", ValueType.VOID), location)
                                .propagate(0, deserializerName)
                                .use();
                        caller.getResult().propagate(agent.getType(deserializerName));
                    }
                }
            }
        } catch (IOException e) {
            agent.getDiagnostics().error(location, "IO error occured getting deserializer list");
        }
    }

    public String getDeserializer(String className) {
        if (emitter == null) {
            return null;
        }
        return emitter.getClassDeserializer(className);
    }
}
