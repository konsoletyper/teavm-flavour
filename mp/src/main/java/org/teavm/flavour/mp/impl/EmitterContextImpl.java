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
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.impl.reflect.ReflectClassImpl;
import org.teavm.flavour.mp.impl.reflect.ReflectContext;
import org.teavm.flavour.mp.impl.reflect.ReflectFieldImpl;
import org.teavm.flavour.mp.impl.reflect.ReflectMethodImpl;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.InstructionLocation;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class EmitterContextImpl implements EmitterContext {
    DependencyAgent agent;
    ReflectContext reflectContext;
    private Map<String, Integer> proxySuffixGenerators = new HashMap<>();
    SourceLocation location;
    private DiagnosticsImpl diagnostics;

    public EmitterContextImpl(DependencyAgent agent, ReflectContext reflectContext) {
        this.agent = agent;
        this.reflectContext = reflectContext;
        this.diagnostics = new DiagnosticsImpl();
    }

    public ReflectContext getReflectContext() {
        return reflectContext;
    }

    @Override
    public <S> S getService(Class<S> type) {
        return agent.getService(type);
    }

    @Override
    public EmitterDiagnostics getDiagnostics() {
        return diagnostics;
    }

    @Override
    public ClassLoader getClassLoader() {
        return agent.getClassLoader();
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void submitClass(ClassHolder cls) {
        agent.submitClass(cls);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ReflectClassImpl<T> findClass(Class<T> cls) {
        return (ReflectClassImpl<T>) reflectContext.getClass(ValueType.parse(cls));
    }

    @Override
    public ReflectClassImpl<?> findClass(String name) {
        ClassReaderSource classSource = reflectContext.getClassSource();
        if (classSource.get(name) == null) {
            return null;
        }
        return reflectContext.getClass(ValueType.object(name));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ReflectClassImpl<T[]> arrayClass(ReflectClass<T> componentType) {
        ReflectClassImpl<T> componentTypeImpl = (ReflectClassImpl<T>) componentType;
        return (ReflectClassImpl<T[]>) reflectContext.getClass(ValueType.arrayOf(componentTypeImpl.type));
    }

    @Override
    public ReflectClass<?> createClass(byte[] bytecode) {
        return findClass(agent.submitClassFile(bytecode).replace('/', '.'));
    }

    public String createProxyName(String className) {
        int suffix = proxySuffixGenerators.getOrDefault(className, 0);
        proxySuffixGenerators.put(className, suffix + 1);
        return className + "$proxy" + suffix;
    }

    SourceLocation convertLocation(CallLocation location) {
        return location.getSourceLocation() != null
                ? new SourceLocation(convertMethod(location.getMethod()), location.getSourceLocation().getFileName(),
                        location.getSourceLocation().getLine())
                : new SourceLocation(convertMethod(location.getMethod()));

    }

    ReflectMethod convertMethod(MethodReference method) {
        MethodReader methodReader = agent.getClassSource().resolve(method);
        if (methodReader == null) {
            return null;
        }
        ReflectClassImpl<?> cls = findClass(methodReader.getOwnerName());
        return cls.getDeclaredMethod(method.getDescriptor());
    }

    class DiagnosticsImpl implements EmitterDiagnostics {
        @Override
        public void error(SourceLocation location, String error, Object... params) {
            convertParams(params);
            agent.getDiagnostics().error(convertLocation(location), error, params);
        }

        @Override
        public void warning(SourceLocation location, String error, Object... params) {
            convertParams(params);
            agent.getDiagnostics().warning(convertLocation(location), error, params);
        }

        private void convertParams(Object[] params) {
            for (int i = 0; i < params.length; ++i) {
                if (params[i] instanceof ReflectMethodImpl) {
                    params[i] = ((ReflectMethodImpl) params[i]).method.getReference();
                } else if (params[i] instanceof ReflectClassImpl) {
                    params[i] = ((ReflectClassImpl<?>) params[i]).type;
                } else if (params[i] instanceof ReflectFieldImpl) {
                    params[i] = ((ReflectFieldImpl) params[i]).field.getReference();
                } else if (params[i] instanceof Class<?>) {
                    params[i] = ValueType.parse((Class<?>) params[i]);
                }
            }
        }

        private CallLocation convertLocation(SourceLocation location) {
            MethodReader method = ((ReflectMethodImpl) location.getMethod()).method;
            return location.getFileName() != null
                    ? new CallLocation(method.getReference(),
                            new InstructionLocation(location.getFileName(), location.getLineNumber()))
                    : new CallLocation(method.getReference());
        }
    }
}
