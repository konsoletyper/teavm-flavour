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

import java.util.HashMap;
import java.util.Map;
import org.teavm.dependency.DependencyAgent;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.impl.reflect.ReflectClassImpl;
import org.teavm.flavour.mp.impl.reflect.ReflectContext;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class EmitterContextImpl implements EmitterContext {
    private DependencyAgent agent;
    ReflectContext reflectContext;
    private Map<String, Integer> proxySuffixGenerators = new HashMap<>();
    CallLocation location;

    public EmitterContextImpl(DependencyAgent agent, ReflectContext reflectContext) {
        this.agent = agent;
        this.reflectContext = reflectContext;
    }

    public ReflectContext getReflectContext() {
        return reflectContext;
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
    public CallLocation getLocation() {
        return location;
    }

    public void submitClass(ClassHolder cls) {
        agent.submitClass(cls);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ReflectClass<T> findClass(Class<T> cls) {
        return (ReflectClass<T>) reflectContext.getClass(ValueType.parse(cls));
    }

    @Override
    public ReflectClass<?> findClass(String name) {
        ClassReaderSource classSource = reflectContext.getClassSource();
        if (classSource.get(name) == null) {
            return null;
        }
        return reflectContext.getClass(ValueType.object(name));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ReflectClass<T[]> arrayClass(ReflectClass<T> componentType) {
        ReflectClassImpl<T> componentTypeImpl = (ReflectClassImpl<T>) componentType;
        return (ReflectClass<T[]>) reflectContext.getClass(ValueType.arrayOf(componentTypeImpl.type));
    }

    public String createProxyName(String className) {
        int suffix = proxySuffixGenerators.getOrDefault(className, 0);
        proxySuffixGenerators.put(className, suffix + 1);
        return className + "$proxy" + className;
    }
}
