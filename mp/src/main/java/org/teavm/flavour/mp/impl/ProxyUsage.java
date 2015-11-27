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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.dependency.DependencyNode;
import org.teavm.model.CallLocation;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyUsage {
    private CallLocation location;
    private ProxyCase proxyCase;
    private List<DependencyNode> nodes;

    ProxyUsage(CallLocation location, ProxyCase proxyCase, List<DependencyNode> nodes) {
        this.location = location;
        this.proxyCase = proxyCase;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    public CallLocation getLocation() {
        return location;
    }

    public ProxyCase getProxyCase() {
        return proxyCase;
    }

    public List<DependencyNode> getNodes() {
        return nodes;
    }
}
