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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.Proxy;
import org.teavm.flavour.mp.ProxyGenerator;
import org.teavm.flavour.mp.ProxyGeneratorContext;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;
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
    private int suffixGenerator;
    private Set<MethodReference> installedProxyGenerators = new HashSet<>();
    private Map<MethodReference, ProxyTypeInfo> proxyTypeMap = new HashMap<>();
    private ProxyUsageFinder usageFinder;
    private ProxyDescriber describer;

    @Override
    public void started(DependencyAgent agent) {
        describer = new ProxyDescriber(agent);
        usageFinder = new ProxyUsageFinder(describer, agent.getDiagnostics());
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency methodDep, CallLocation location) {
        MethodReader method = methodDep.getMethod();
        usageFinder.findUsages(methodDep);
        AnnotationReader instanceProxyAnnot = method.getAnnotations().get(Proxy.class.getName());
        if (instanceProxyAnnot != null) {
            installInstanceProxyEmitter(agent, methodDep, instanceProxyAnnot, location);
        }
    }

    private void installInstanceProxyEmitter(DependencyAgent agent, MethodDependency methodDep,
            AnnotationReader annot, CallLocation location) {
        if (!installedProxyGenerators.add(methodDep.getReference())) {
            return;
        }

        Diagnostics diagnostics = agent.getDiagnostics();
        String generatorClassName = ((ValueType.Object) annot.getValue("value").getJavaClass()).getClassName();
        Class<?> generatorClass;
        try {
            generatorClass = Class.forName(generatorClassName, true, agent.getClassLoader());
        } catch (ClassNotFoundException e) {
            diagnostics.error(location, "Could not find proxy generator class {{c0}}", generatorClassName);
            return;
        }
        Constructor<? extends ProxyGenerator> ctor;
        try {
            ctor = generatorClass.asSubclass(ProxyGenerator.class).getConstructor();
        } catch (NoSuchMethodException | SecurityException e) {
            diagnostics.error(location, "Zero-argument constructor not found in proxy generator class {{c0}}",
                    generatorClassName);
            return;
        }

        ProxyGenerator generator;
        try {
            generator = ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            diagnostics.error(location, "Could not instantiate proxy generator class {{c0}}", generatorClassName);
            return;
        }

        ProxyTypeInfo proxyInfo = new ProxyTypeInfo();
        proxyInfo.baseType = methodDep.getMethod().getResultType();
        proxyInfo.paramType = ((ValueType.Object) methodDep.getMethod().parameterType(0)).getClassName();
        proxyTypeMap.put(methodDep.getReference(), proxyInfo);

        MethodDependency getClassDep = agent.linkMethod(new MethodReference(Object.class, "getClass", Class.class),
                location);
        getClassDep.getThrown().connect(methodDep.getThrown());

        MethodReader factoryMethod = methodDep.getMethod();
        ProxyGeneratorContextImpl<Object> context = new ProxyGeneratorContextImpl<>(agent);
        generator.setContext(context);
        methodDep.getVariable(1).addConsumer(type -> {
            MethodReference ref = new MethodReference(factoryMethod.getOwnerName(),
                    factoryMethod.getName() + "$subtype$" + suffixGenerator++,
                    ValueType.object(type.getName()), factoryMethod.getResultType());
            ProgramEmitter pe = ProgramEmitter.create(ref.getDescriptor(), agent.getClassSource());
            generator.generate(type.getName(), pe, location);
            agent.submitMethod(ref, pe.getProgram());

            MethodDependency singleFactoryMethod = agent.linkMethod(ref, location);
            singleFactoryMethod.getVariable(1).propagate(type);
            singleFactoryMethod.getResult().connect(methodDep.getResult());
            singleFactoryMethod.getThrown().connect(methodDep.getThrown());
            singleFactoryMethod.use();

            getClassDep.propagate(0, type);
            proxyInfo.typeHandlers.put(type.getName(), ref);
        });

        MethodDependency nameDep = agent.linkMethod(new MethodReference(Class.class, "getName", String.class),
                location);
        getClassDep.getResult().connect(nameDep.getVariable(0));
        nameDep.getThrown().connect(methodDep.getThrown());
        nameDep.use();

        MethodDependency equalsDep = agent.linkMethod(new MethodReference(String.class, "equals", Object.class,
                boolean.class), location);
        nameDep.getResult().connect(equalsDep.getVariable(0));
        equalsDep.getVariable(1).propagate(agent.getType("java.lang.String"));
        equalsDep.getThrown().connect(methodDep.getThrown());
        equalsDep.use();

        MethodDependency hashCodeDep = agent.linkMethod(new MethodReference(String.class, "hashCode", int.class),
                location);
        nameDep.getResult().connect(hashCodeDep.getVariable(0));
        hashCodeDep.getThrown().connect(methodDep.getThrown());
        hashCodeDep.use();
    }

    @Override
    public void completing(DependencyAgent agent) {
        for (Map.Entry<MethodReference, ProxyTypeInfo> entry : proxyTypeMap.entrySet()) {
            MethodReference method = entry.getKey();
            ProxyTypeInfo typeInfo = entry.getValue();
            ProgramEmitter pe = ProgramEmitter.create(method.getDescriptor(), agent.getClassSource());

            ValueEmitter paramVar = pe.var(1, ValueType.object(typeInfo.paramType));
            ValueEmitter typeNameVar = paramVar
                    .invokeVirtual("getClass", Class.class)
                    .invokeVirtual("getName", String.class);
            StringChooseEmitter choice = pe.stringChoice(typeNameVar);
            for (Map.Entry<String, MethodReference> subtypeEntry : typeInfo.typeHandlers.entrySet()) {
                String subtype = subtypeEntry.getKey();
                MethodReference subtypeHandler = subtypeEntry.getValue();
                choice.option(subtype, () -> {
                    ValueEmitter subtypeParamVar = paramVar.cast(ValueType.object(subtype));
                    pe.invoke(subtypeHandler, subtypeParamVar).returnValue();
                });
            }
            choice.otherwise(() -> pe.constantNull(typeInfo.baseType).returnValue());

            agent.submitMethod(method, pe.getProgram());
        }
    }

    static class ProxyTypeInfo {
        ValueType baseType;
        String paramType;
        Map<String, MethodReference> typeHandlers = new HashMap<>();
    }

    static class ProxyGeneratorContextImpl<T> implements ProxyGeneratorContext<T> {
        private DependencyAgent agent;

        public ProxyGeneratorContextImpl(DependencyAgent agent) {
            this.agent = agent;
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
        public ClassReaderSource getClassSource() {
            return agent.getClassSource();
        }

        @Override
        public ClassLoader getClassLoader() {
            return agent.getClassLoader();
        }

        @Override
        public void submitClass(ClassHolder cls) {
            agent.submitClass(cls);
        }

        @Override
        public Emitter<T> getEmitter() {
            return null;
        }

        @Override
        public <S> ReflectClass<S> findClass(Class<S> cls) {
            return null;
        }

        @Override
        public ReflectClass<?> findClass(String name) {
            return null;
        }
    }
}
