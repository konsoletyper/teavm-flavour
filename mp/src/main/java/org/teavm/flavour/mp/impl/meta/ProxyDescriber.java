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
package org.teavm.flavour.mp.impl.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.impl.ProxyMethod;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyDescriber {
    private static Set<ValueType> validConstantTypes = new HashSet<>(Arrays.asList(ValueType.BOOLEAN, ValueType.BYTE,
            ValueType.SHORT, ValueType.CHARACTER, ValueType.INTEGER, ValueType.LONG, ValueType.FLOAT,
            ValueType.DOUBLE, ValueType.parse(String.class)));
    private Diagnostics diagnostics;
    private ClassReaderSource classSource;
    private Map<MethodReference, ProxyModel> cache = new HashMap<>();

    public ProxyDescriber(Diagnostics diagnostics, ClassReaderSource classSource) {
        this.diagnostics = diagnostics;
        this.classSource = classSource;
    }

    public ProxyModel getProxy(MethodReference method) {
        return cache.computeIfAbsent(method, key -> describeProxy(key));
    }

    public ProxyModel getKnownProxy(MethodReference method) {
        return cache.get(method);
    }

    public Iterable<ProxyModel> getKnownProxies() {
        return cache.values();
    }

    private ProxyModel describeProxy(MethodReference methodRef) {
        MethodReader method = classSource.resolve(methodRef);
        if (method == null) {
            return null;
        }
        if (method.getAnnotations().get(Reflected.class.getName()) == null) {
            return null;
        }
        CallLocation location = new CallLocation(methodRef);

        boolean valid = true;
        if (!method.hasModifier(ElementModifier.STATIC)) {
            diagnostics.error(location, "Proxy method should be static");
            valid = false;
        }
        if (!valid) {
            return null;
        }

        ProxyModel proxyMethod = findProxyMethod(method);
        if (proxyMethod == null) {
            diagnostics.error(location, "Corresponding proxy executor was not found");
            return null;
        }

        return proxyMethod;
    }

    private ProxyModel findProxyMethod(MethodReader method) {
        AnnotationReader proxyAnnot = method.getAnnotations().get(ProxyMethod.class.getName());
        if (proxyAnnot != null) {
            return getExplicitProxyModel(method, proxyAnnot);
        }

        ClassReader cls = classSource.get(method.getOwnerName());
        nextMethod: for (MethodReader proxy : cls.getMethods()) {
            if (proxy == method
                    || !proxy.hasModifier(ElementModifier.STATIC)
                    || !proxy.getName().equals(method.getName())
                    || proxy.getResultType() != ValueType.VOID
                    || proxy.parameterCount() != method.parameterCount() + 1) {
                continue;
            }

            if (!proxy.parameterType(0).isObject(Emitter.class)) {
                continue;
            }

            List<ProxyParameter> parameters = new ArrayList<>();
            for (int i = 0; i < method.parameterCount(); ++i) {
                ValueType proxyParam = proxy.parameterType(i + 1);
                ValueType param = method.parameterType(i);
                if (proxyParam.isObject(Value.class)) {
                    parameters.add(new ProxyParameter(i, i, param, ParameterKind.VALUE, null));
                } else if (proxyParam.isObject(ReflectValue.class)) {
                    parameters.add(new ProxyParameter(i, i, param, ParameterKind.REFLECT_VALUE, null));
                } else if (validConstantTypes.contains(proxyParam) && proxyParam.equals(param)) {
                    parameters.add(new ProxyParameter(i, i, param, ParameterKind.CONSTANT, null));
                } else if (proxyParam.isObject(ReflectClass.class) && param.isObject(Class.class)) {
                    parameters.add(new ProxyParameter(i, i, param, ParameterKind.CONSTANT, null));
                } else {
                    continue nextMethod;
                }
            }

            return new ProxyModel(method.getReference(), proxy.getReference(), parameters, parameters);
        }
        return null;
    }

    private ProxyModel getExplicitProxyModel(MethodReader method, AnnotationReader proxyAnnot) {
        MethodReference proxyRef = MethodReference.parse(proxyAnnot.getValue("value").getString());
        List<ProxyParameter> parameters = new ArrayList<>();
        List<ProxyParameter> callParameters = new ArrayList<>();
        String[] parameterValues = proxyAnnot.getValue("arguments").getList().stream()
                .map(item -> item.getString())
                .toArray(sz -> new String[sz]);

        for (int i = 1; i < proxyRef.parameterCount(); ++i) {
            String valueString = parameterValues[i - 1];
            ValueType paramType = proxyRef.parameterType(i);
            if (paramType.isObject(Value.class)) {
                int delegateParamIndex = Integer.parseInt(valueString);
                ProxyParameter parameter = new ProxyParameter(parameters.size(), callParameters.size(),
                        method.parameterType(delegateParamIndex), ParameterKind.VALUE, null);
                parameters.add(parameter);
                callParameters.add(parameter);
            } else if (paramType.isObject(ReflectValue.class)) {
                int delegateParamIndex = Integer.parseInt(valueString);
                ProxyParameter parameter = new ProxyParameter(parameters.size(), callParameters.size(),
                        method.parameterType(delegateParamIndex), ParameterKind.REFLECT_VALUE, null);
                parameters.add(parameter);
                callParameters.add(parameter);
            } else {
                Object value;
                boolean computed = false;
                if (valueString.equals("null")) {
                    value = null;
                    computed = true;
                } else {
                    valueString = valueString.substring(1);
                    if (paramType.isObject(Boolean.class) || paramType == ValueType.BOOLEAN) {
                        value = Boolean.parseBoolean(valueString);
                        computed = true;
                    } else if (paramType.isObject(Byte.class) || paramType == ValueType.BYTE) {
                        value = Byte.parseByte(valueString);
                        computed = true;
                    } else if (paramType.isObject(Short.class) || paramType == ValueType.SHORT) {
                        value = Short.parseShort(valueString);
                        computed = true;
                    } else if (paramType.isObject(Character.class) || paramType == ValueType.CHARACTER) {
                        value = valueString.charAt(0);
                        computed = true;
                    } else if (paramType.isObject(Integer.class) || paramType == ValueType.INTEGER) {
                        value = Integer.parseInt(valueString);
                        computed = true;
                    } else if (paramType.isObject(Long.class) || paramType == ValueType.LONG) {
                        value = Long.parseLong(valueString);
                        computed = true;
                    } else if (paramType.isObject(Float.class) || paramType == ValueType.FLOAT) {
                        value = Float.parseFloat(valueString);
                        computed = true;
                    } else if (paramType.isObject(Double.class) || paramType == ValueType.DOUBLE) {
                        value = Double.parseDouble(valueString);
                        computed = true;
                    } else if (paramType.isObject(String.class)) {
                        value = valueString;
                        computed = true;
                    } else if (paramType.isObject(ReflectClass.class)) {
                        value = ValueType.parse(valueString);
                        computed = true;
                    } else {
                        value = null;
                    }
                }

                if (computed) {
                    callParameters.add(new ProxyParameter(-1, callParameters.size(), paramType,
                            ParameterKind.CONSTANT, value));
                }
            }
        }

        return new ProxyModel(method.getReference(), proxyRef, parameters, callParameters);
    }
}
