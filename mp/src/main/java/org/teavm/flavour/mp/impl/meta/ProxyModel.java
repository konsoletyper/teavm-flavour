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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.model.MethodReference;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyModel {
    private MethodReference method;
    private MethodReference proxyMethod;
    private List<ProxyParameter> parameters;
    private List<ProxyParameter> callParameters;
    private Map<Object, MethodReference> usages = new HashMap<>();

    ProxyModel(MethodReference method, MethodReference proxyMethod, List<ProxyParameter> parameters,
            List<ProxyParameter> callParameters) {
        this.method = method;
        this.proxyMethod = proxyMethod;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.callParameters = Collections.unmodifiableList(new ArrayList<>(callParameters));
    }

    public MethodReference getMethod() {
        return method;
    }

    public MethodReference getProxyMethod() {
        return proxyMethod;
    }

    public List<ProxyParameter> getParameters() {
        return parameters;
    }

    public List<ProxyParameter> getCallParameters() {
        return callParameters;
    }

    public Map<Object, MethodReference> getUsages() {
        return usages;
    }
}
