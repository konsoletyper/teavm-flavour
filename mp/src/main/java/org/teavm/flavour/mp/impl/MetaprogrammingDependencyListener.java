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
package org.teavm.flavour.mp.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ProxyGeneratorContext;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.impl.meta.ParameterKind;
import org.teavm.flavour.mp.impl.meta.ProxyDescriber;
import org.teavm.flavour.mp.impl.meta.ProxyModel;
import org.teavm.flavour.mp.impl.meta.ProxyParameter;
import org.teavm.flavour.mp.impl.reflect.ReflectContext;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.StringChooseEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class MetaprogrammingDependencyListener extends AbstractDependencyListener {
    private ProxyDescriber describer;
    private Set<ProxyModel> installedProxies = new HashSet<>();
    private ReflectContext reflectContext;

    @Override
    public void started(DependencyAgent agent) {
        describer = new ProxyDescriber(agent.getDiagnostics(), agent.getClassSource());
        reflectContext = new ReflectContext(agent.getClassSource(), agent.getClassLoader());
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency methodDep, CallLocation location) {
        ProxyModel proxy = describer.getProxy(methodDep.getReference());
        if (proxy != null && installedProxies.add(proxy)) {
            new PermutationGenerator(agent, proxy, methodDep, location, reflectContext).installProxyEmitter();
        }
    }

    @Override
    public void completing(DependencyAgent agent) {
        proxy: for (ProxyModel proxy : describer.getKnownProxies()) {
            ProgramEmitter pe = ProgramEmitter.create(proxy.getMethod().getDescriptor(), agent.getClassSource());
            MethodDependencyInfo methodDep = agent.getMethod(proxy.getMethod());

            String[][] typeVariants = new String[proxy.getParameters().size()][];
            ValueEmitter sb = pe.construct(StringBuilder.class);
            ValueEmitter[] paramVars = new ValueEmitter[proxy.getParameters().size()];
            for (ProxyParameter param : proxy.getParameters()) {
                paramVars[param.getIndex()] = pe.var(param.getIndex() + 1, param.getType());
                if (param.getKind() == ParameterKind.REFLECT_VALUE) {
                    ValueEmitter paramVar = paramVars[param.getIndex()];
                    ValueEmitter typeNameVar = paramVar
                            .invokeVirtual("getClass", Class.class)
                            .invokeVirtual("getName", String.class);
                    sb = sb.invokeVirtual("append", StringBuilder.class, typeNameVar);
                    typeVariants[param.getIndex()] = methodDep.getVariable(param.getIndex() + 1).getTypes();
                    if (typeVariants[param.getIndex()].length == 0) {
                        continue proxy;
                    }
                }
            }

            StringChooseEmitter choice = pe.stringChoice(sb.invokeVirtual("toString", String.class));
            for (Map.Entry<String, MethodReference> usageEntry : proxy.getUsages().entrySet()) {
                choice.option(usageEntry.getKey(), () -> {
                    MethodReference implMethod = usageEntry.getValue();
                    ValueEmitter[] castParamVars = new ValueEmitter[paramVars.length];
                    for (int i = 0; i < castParamVars.length; ++i) {
                        castParamVars[i] = paramVars[i].cast(implMethod.parameterType(i));
                    }
                    ValueEmitter result = pe.invoke(implMethod, castParamVars);
                    if (implMethod.getReturnType() == ValueType.VOID) {
                        pe.exit();
                    } else {
                        result.returnValue();
                    }
                });
            }

            choice.otherwise(() -> {
                if (methodDep.getReference().getReturnType() == ValueType.VOID) {
                    pe.exit();
                } else {
                    pe.constantNull(Object.class).returnValue();
                }
            });

            agent.submitMethod(proxy.getMethod(), pe.getProgram());
        }
    }

    static class ProxyGeneratorContextImpl<T> implements ProxyGeneratorContext<T> {
        private DependencyAgent agent;
        EmitterImpl<T> emitter;
        CompositeMethodGenerator generator;
        ReflectContext reflectContext;

        public ProxyGeneratorContextImpl(DependencyAgent agent, ReflectContext reflectContext,
                MethodReference templateMethod, ValueType returnType) {
            this.agent = agent;
            generator = new CompositeMethodGenerator(agent.getDiagnostics());
            emitter = new EmitterImpl<>(agent.getClassSource(), generator, templateMethod, returnType);
            this.reflectContext = reflectContext;
        }

        @Override
        public <S> S getService(Class<S> type) {
            return agent.getService(type);
        }

        @Override
        public Diagnostics getDiagnostics() {
            return agent.getDiagnostics();
        }

        @Override
        public ClassLoader getClassLoader() {
            return agent.getClassLoader();
        }

        @Override
        public Emitter<T> getEmitter() {
            return emitter;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S> ReflectClass<S> findClass(Class<S> cls) {
            return (ReflectClass<S>) findClass(cls.getName());
        }

        @Override
        public ReflectClass<?> findClass(String name) {
            ClassReaderSource classSource = reflectContext.getClassSource();
            if (classSource.get(name) == null) {
                return null;
            }
            return reflectContext.getClass(ValueType.object(name));
        }
    }
}
