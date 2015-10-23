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
package org.teavm.flavour.routing.metadata;

import org.teavm.model.MethodDescriptor;

/**
 *
 * @author Alexey Andreev
 */
public class RouteDescriptor {
    private MethodDescriptor method;
    private String[] pathParts;
    private ParameterDescriptor[] parameters;

    public RouteDescriptor(MethodDescriptor method, String[] pathParts, ParameterDescriptor[] parameters) {
        this.method = method;
        this.pathParts = pathParts;
        this.parameters = parameters;
    }

    public MethodDescriptor getMethod() {
        return method;
    }

    public int pathPartCount() {
        return pathParts.length;
    }

    public String pathPart(int index) {
        return pathParts[index];
    }

    public ParameterDescriptor parameter(int index) {
        return parameters[index];
    }
}
