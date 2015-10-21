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

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.serializer.JsonSerializer;
import org.teavm.flavour.json.serializer.JsonSerializerContext;
import org.teavm.flavour.json.tree.Node;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.StringChooseEmitter;

/**
 *
 * @author Alexey Andreev
 */
class SerializerDependencyListener extends AbstractDependencyListener {
    private JsonSerializerEmitter emitter;

    public String getSerializer(String className) {
        if (emitter == null) {
            return null;
        }
        return emitter.getClassSerializer(className);
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method, CallLocation location) {
        if (method.getReference().getClassName().equals(JSON.class.getName())
                && method.getReference().getName().equals("getClassSerializer")) {
            emitter = new JsonSerializerEmitter(agent);

            MethodDependency hashCodeMethod = agent.linkMethod(new MethodReference(String.class,
                    "hashCode", int.class), location).propagate(0, String.class);
            hashCodeMethod.getThrown().connect(method.getThrown());
            hashCodeMethod.use();

            MethodDependency equalsMethod = agent.linkMethod(new MethodReference(String.class,
                    "equals", int.class), location).propagate(0, String.class);
            equalsMethod.getThrown().connect(method.getThrown());
            equalsMethod.use();

            MethodDependency serializeMethod = agent.linkMethod(new MethodReference(JSON.class,
                    "serialize", JsonSerializerContext.class, Object.class, Node.class), null);
            serializeMethod.getVariable(2).addConsumer(type -> {
                if (type.getName().startsWith("[")) {
                    return;
                }
                String serializer = emitter.addClassSerializer(type.getName());
                agent.linkClass(serializer, location);
                MethodDependency dep = agent.linkMethod(new MethodReference(serializer, "<init>", ValueType.VOID),
                        location).propagate(0, agent.getType(serializer));
                dep.getThrown().connect(method.getThrown());
                dep.use();
                method.getResult().propagate(agent.getType(serializer));
            });
        }
    }

    @Override
    public void completing(DependencyAgent agent) {
        if (emitter == null) {
            return;
        }
        MethodDependencyInfo serializeMethod = agent.getMethod(new MethodReference(JSON.class,
                "serialize", JsonSerializerContext.class, Object.class, Node.class));
        String[] types = serializeMethod.getVariable(2).getTypes();

        MethodReference ref = new MethodReference(JSON.class, "getClassSerializer", String.class,
                JsonSerializer.class);

        ProgramEmitter pe = ProgramEmitter.create(ref.getDescriptor(), agent.getClassSource());
        StringChooseEmitter choice = pe.stringChoice(pe.var(1, String.class));
        for (String className : types) {
            String serializer = getSerializer(className);
            if (serializer != null) {
                choice.option(className, () -> pe.construct(serializer).returnValue());
            }
        }
        choice.otherwise(() -> pe.constantNull(JsonSerializer.class).returnValue());

        agent.submitMethod(ref, pe.getProgram());
    }
}
