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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.MethodDependency;
import org.teavm.dependency.MethodDependencyInfo;
import org.teavm.flavour.mp.impl.meta.ParameterKind;
import org.teavm.flavour.mp.impl.meta.ProxyDescriber;
import org.teavm.flavour.mp.impl.meta.ProxyModel;
import org.teavm.flavour.mp.impl.meta.ProxyParameter;
import org.teavm.flavour.mp.impl.reflect.ReflectContext;
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
    private ProxyDescriber describer;
    private Set<ProxyModel> installedProxies = new HashSet<>();
    private ReflectContext reflectContext;
    private EmitterContextImpl emitterContext;
    private Map<Object, MethodReference> usageMap = new HashMap<>();
    private MetaprogrammingTransformer transformer;
    private ProxyClassLoader proxyClassLoader;

    MetaprogrammingDependencyListener(MetaprogrammingTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public void started(DependencyAgent agent) {
        proxyClassLoader = new ProxyClassLoader(agent.getClassLoader());
        describer = new ProxyDescriber(agent.getDiagnostics(), agent.getClassSource());
        reflectContext = new ReflectContext(agent.getClassSource(), agent.getClassLoader());
        emitterContext = new EmitterContextImpl(agent, reflectContext);
        reflectContext.setEmitterContext(emitterContext);
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency methodDep, CallLocation location) {
        transformer.getDeferredErrors(methodDep.getReference()).forEach(err -> err.run());

        ProxyModel proxy = describer.getProxy(methodDep.getReference());
        if (proxy != null && installedProxies.add(proxy)) {
            new PermutationGenerator(agent, proxy, methodDep, location, emitterContext, usageMap, proxyClassLoader)
                    .installProxyEmitter();
        }
    }

    @Override
    public void completing(DependencyAgent agent) {
        proxy: for (ProxyModel proxy : describer.getKnownProxies()) {
            if (proxy.getUsages().isEmpty()) {
                continue;
            }

            boolean variated = proxy.getCallParameters().stream()
                    .anyMatch(param -> param.getKind() == ParameterKind.REFLECT_VALUE);
            ProgramEmitter pe = ProgramEmitter.create(proxy.getMethod().getDescriptor(), agent.getClassSource());

            if (!variated) {
                ValueEmitter[] paramVars = new ValueEmitter[proxy.getParameters().size()];
                for (ProxyParameter param : proxy.getParameters()) {
                    paramVars[param.getIndex()] = pe.var(param.getIndex() + 1, param.getType());
                }
                MethodReference implMethod = proxy.getUsages().values().iterator().next();
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
                agent.submitMethod(proxy.getMethod(), pe.getProgram());
                continue;
            }

            MethodDependencyInfo methodDep = agent.getMethod(proxy.getMethod());

            String[][] typeVariants = new String[proxy.getParameters().size()][];
            ValueEmitter[] paramVars = new ValueEmitter[proxy.getParameters().size()];

            List<ProxyParameter> reflectParameters = proxy.getParameters().stream()
                    .filter(param -> param.getKind() == ParameterKind.REFLECT_VALUE)
                    .collect(Collectors.toList());

            for (ProxyParameter param : proxy.getParameters()) {
                paramVars[param.getIndex()] = pe.var(param.getIndex() + 1, param.getType());
            }

            ValueEmitter tag;
            if (reflectParameters.size() > 1) {
                ValueEmitter sb = pe.construct(StringBuilder.class);
                boolean first = true;
                for (ProxyParameter param : reflectParameters) {
                    if (!first) {
                        sb = sb.invokeVirtual("append", StringBuilder.class, pe.constant("|"));
                    }
                    first = false;
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
                tag = sb.invokeVirtual("toString", String.class);
            } else {
                ProxyParameter param = reflectParameters.get(0);
                ValueEmitter paramVar = paramVars[param.getIndex()];
                tag = paramVar.invokeVirtual("getClass", Class.class).invokeVirtual("getName", String.class);
            }

            StringChooseEmitter choice = pe.stringChoice(tag);
            for (Map.Entry<Object, MethodReference> usageEntry : proxy.getUsages().entrySet()) {
                @SuppressWarnings("unchecked")
                List<Object> key = (List<Object>) usageEntry.getKey();
                StringBuilder stringKey = new StringBuilder();
                boolean first = true;
                for (int i = 0; i < proxy.getCallParameters().size(); ++i) {
                    if (proxy.getCallParameters().get(i).getKind() == ParameterKind.REFLECT_VALUE) {
                        if (!first) {
                            stringKey.append("|");
                        }
                        first = false;
                        stringKey.append(getTypeName((ValueType) key.get(i + 1)));
                    }
                }

                choice.option(stringKey.toString(), () -> {
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

    private String getTypeName(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return "boolean";
                case BYTE:
                    return "byte";
                case SHORT:
                    return "short";
                case CHARACTER:
                    return "char";
                case INTEGER:
                    return "int";
                case LONG:
                    return "long";
                case FLOAT:
                    return "float";
                case DOUBLE:
                    return "double";
            }
        } else if (type instanceof ValueType.Object) {
            return ((ValueType.Object) type).getClassName();
        } else if (type instanceof ValueType.Array) {
            return type.toString().replace('/', '.');
        } else if (type == ValueType.VOID) {
            return "void";
        }
        throw new AssertionError("Unsupported type: " + type);
    }
}
