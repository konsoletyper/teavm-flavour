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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyType;
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
import org.teavm.model.CallLocation;
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
    private ProxyDescriber describer;
    private Set<ProxyModel> installedProxies = new HashSet<>();

    @Override
    public void started(DependencyAgent agent) {
        describer = new ProxyDescriber(agent.getDiagnostics(), agent.getClassSource());
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency methodDep, CallLocation location) {
        ProxyModel proxy = describer.getProxy(methodDep.getReference());
        if (proxy != null && installedProxies.add(proxy)) {
            installProxyEmitter(agent, proxy, methodDep, location);
        }
    }

    private void installProxyEmitter(DependencyAgent agent, ProxyModel model, MethodDependency methodDep,
            CallLocation location) {
        Diagnostics diagnostics = agent.getDiagnostics();

        MethodDependency getClassDep = agent.linkMethod(new MethodReference(Object.class, "getClass", Class.class),
                location);
        getClassDep.getThrown().connect(methodDep.getThrown());

        ProxyGeneratorContextImpl<Object> context = new ProxyGeneratorContextImpl<>(agent);
        Method proxyMethod;
        try {
            proxyMethod = getJavaMethod(agent.getClassLoader(), model.getProxyMethod());
            proxyMethod.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            diagnostics.error(location, "Error accessing proxy method {{m0}}", model.getProxyMethod());
            return;
        }

        for (ProxyParameter param : model.getParameters()) {
            methodDep.getVariable(param.getIndex() + 1).addConsumer(type -> {
                String[][] variants = new String[model.getParameters().size()][];
                int[] indexes = new int[variants.length];
                for (ProxyParameter otherParam : model.getParameters()) {
                    if (otherParam == param || otherParam.getKind() != ParameterKind.REFLECT_VALUE) {
                        continue;
                    }
                    String[] types = methodDep.getVariable(otherParam.getIndex()).getTypes();
                    if (types.length == 0) {
                        return;
                    }
                    variants[otherParam.getIndex()] = types;
                }

                int i;
                do {
                    StringBuilder sb = new StringBuilder();
                    ValueType[] signature = new ValueType[model.getParameters().size() + 1];
                    Object[] proxyArgs = new Object[model.getParameters().size() + 1];
                    for (i = 0; i < variants.length; ++i) {
                        if (variants[i] != null) {
                            String variant = variants[i][indexes[i]];
                            sb.append(variant);
                            signature[i] = ValueType.object(variant);
                        } else {
                            signature[i] = model.getParameters().get(i).getType();
                        }
                    }
                    signature[i] = model.getMethod().getReturnType();

                    MethodReference implRef = new MethodReference(model.getMethod().getClassName(),
                            model.getMethod().getName() + "$proxy" + suffixGenerator++, signature);
                    model.getUsages().put(sb.toString(), implRef);

                    proxyArgs[0] = context;
                    try {
                        proxyMethod.invoke(null, proxyArgs);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        diagnostics.error(location, "Error calling proxy method {{m0}}", model.getProxyMethod());
                    }
                    agent.submitMethod(implRef, context.generator.getProgram());

                    MethodDependency implMethod = agent.linkMethod(implRef, location);
                    for (i = 0; i < variants.length; ++i) {
                        if (variants[i] != null) {
                            DependencyType variant = agent.getType(variants[i][indexes[i]]);
                            implMethod.getVariable(i + 1).propagate(variant);
                        }
                    }
                    implMethod.getResult().connect(methodDep.getResult());
                    implMethod.getThrown().connect(methodDep.getThrown());
                    implMethod.use();

                    for (i = 0; i < variants.length; ++i) {
                        if (variants[i] != null) {
                            if (++indexes[i] < variants[i].length) {
                                break;
                            }
                            indexes[i] = 0;
                        }
                    }
                } while (i < variants.length);
            });
        }

        MethodDependency sbInitDep = agent.linkMethod(new MethodReference(StringBuilder.class, "<init>", void.class),
                location);
        sbInitDep.getThrown().connect(methodDep.getThrown());
        sbInitDep.getVariable(0).propagate(agent.getType(StringBuilder.class.getName()));
        sbInitDep.use();

        MethodDependency sbAppendDep = agent.linkMethod(new MethodReference(StringBuilder.class, "append",
                String.class, StringBuilder.class), location);
        sbAppendDep.getThrown().connect(methodDep.getThrown());
        sbAppendDep.getVariable(0).propagate(agent.getType(StringBuilder.class.getName()));
        sbAppendDep.getVariable(1).propagate(agent.getType(String.class.getName()));
        sbAppendDep.use();

        MethodDependency sbToStringDep = agent.linkMethod(new MethodReference(StringBuilder.class, "toString",
                String.class), location);
        sbToStringDep.getThrown().connect(methodDep.getThrown());
        sbToStringDep.getVariable(0).propagate(agent.getType(StringBuilder.class.getName()));
        sbToStringDep.use();

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

    private Method getJavaMethod(ClassLoader classLoader, MethodReference ref) throws ReflectiveOperationException {
        Class<?> cls = Class.forName(ref.getClassName(), true, classLoader);
        Class<?>[] parameterTypes = new Class<?>[ref.parameterCount()];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = getJavaType(classLoader, ref.parameterType(i));
        }
        return cls.getMethod(ref.getName(), parameterTypes);
    }

    private Class<?> getJavaType(ClassLoader classLoader, ValueType type) throws ReflectiveOperationException {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return boolean.class;
                case BYTE:
                    return byte.class;
                case SHORT:
                    return short.class;
                case CHARACTER:
                    return char.class;
                case INTEGER:
                    return int.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                case DOUBLE:
                    return double.class;
            }
        } else if (type instanceof ValueType.Array) {
            Class<?> componentType = getJavaType(classLoader, ((ValueType.Array) type).getItemType());
            return Array.newInstance(componentType, 0).getClass();
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            return Class.forName(className, true, classLoader);
        } else if (type instanceof ValueType.Void) {
            return void.class;
        }
        throw new AssertionError("Don't know how to map type: " + type);
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
                paramVars[param.getIndex()] = pe.var(param.getIndex(), param.getType());
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
                MethodReference implMethod = usageEntry.getValue();
                ValueEmitter[] castParamVars = new ValueEmitter[paramVars.length];
                for (int i = 0; i < castParamVars.length; ++i) {
                    castParamVars[i] = paramVars[i].cast(implMethod.parameterType(i));
                }
                choice.option(usageEntry.getKey(), () -> {
                    ValueEmitter result = pe.invoke(implMethod, castParamVars);
                    if (implMethod.getReturnType() == ValueType.VOID) {
                        pe.exit();
                    } else {
                        result.returnValue();
                    }
                });
            }

            agent.submitMethod(proxy.getMethod(), pe.getProgram());
        }
    }

    static class ProxyGeneratorContextImpl<T> implements ProxyGeneratorContext<T> {
        private DependencyAgent agent;
        private EmitterImpl<T> emitter;
        CompoundMethodGenerator generator;

        public ProxyGeneratorContextImpl(DependencyAgent agent) {
            this.agent = agent;
            generator = new CompoundMethodGenerator();
            emitter = new EmitterImpl<>(agent.getClassSource(), generator);
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
