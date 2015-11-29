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
import org.teavm.flavour.mp.ProxyGeneratorContext;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;
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
            ValueType.DOUBLE, ValueType.parse(String.class), ValueType.parse(Class.class)));
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
        if (method.parameterCount() > 0) {
            diagnostics.error(location, "Proxy method shoud take at least one parameter");
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
        ClassReader cls = classSource.get(method.getOwnerName());
        nextMethod: for (MethodReader proxy : cls.getMethods()) {
            if (proxy == method
                    || !proxy.hasModifier(ElementModifier.STATIC)
                    || !proxy.getName().equals(method.getName())
                    || proxy.getResultType() != ValueType.VOID
                    || proxy.parameterCount() != method.parameterCount() + 1
                    || !proxy.parameterType(0).isObject(ProxyGeneratorContext.class)) {
                continue;
            }

            List<ProxyParameter> parameters = new ArrayList<>();
            for (int i = 0; i < method.parameterCount(); ++i) {
                ValueType proxyParam = proxy.parameterType(i + 1);
                ValueType param = proxy.parameterType(i);
                if (proxyParam.isObject(Value.class)) {
                    parameters.add(new ProxyParameter(i, param, ParameterKind.VALUE));
                } else if (proxyParam.isObject(ReflectValue.class)) {
                    parameters.add(new ProxyParameter(i, param, ParameterKind.REFLECT_VALUE));
                } else if (validConstantTypes.contains(proxyParam) && proxyParam.equals(param)) {
                    parameters.add(new ProxyParameter(i, param, ParameterKind.CONSTANT));
                } else {
                    continue nextMethod;
                }
            }

            return new ProxyModel(method.getReference(), proxy.getReference(), parameters);
        }
        return null;
    }
}
