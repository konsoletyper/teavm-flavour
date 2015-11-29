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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.dependency.DependencyNode;
import org.teavm.model.CallLocation;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyUsage {
    private CallLocation location;
    private List<Object> constants;
    private List<DependencyNode> nodes;
    private List<Set<String>> actualTypes;

    ProxyUsage(CallLocation location, List<Object> constants, List<DependencyNode> nodes) {
        this.location = location;
        this.constants = Collections.unmodifiableList(new ArrayList<>(constants));
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        List<Set<String>> actualTypes = new ArrayList<>(nodes.size());
        for (int i = 0; i < nodes.size(); ++i) {
            if (nodes.get(i) != null) {
                actualTypes.add(new HashSet<String>());
            }
        }
        this.actualTypes = Collections.unmodifiableList(actualTypes);
    }

    public CallLocation getLocation() {
        return location;
    }

    public List<Object> getConstants() {
        return constants;
    }

    public List<DependencyNode> getNodes() {
        return nodes;
    }

    public List<Set<String>> getActualTypes() {
        return actualTypes;
    }
}
