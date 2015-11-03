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
package org.teavm.flavour.rest.impl.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.teavm.flavour.rest.processor.HttpMethod;
import org.teavm.model.MethodDescriptor;

/**
 *
 * @author Alexey Andreev
 */
public class MethodModel implements Cloneable {
    MethodDescriptor method;
    boolean inherited;
    HttpMethod httpMethod;
    String path = "";
    List<ParameterModel> parameters;
    private List<ParameterModel> readonlyParameters = Collections.unmodifiableList(parameters);
    Map<String, ValuePath> pathParameters = new HashMap<>();
    private Map<String, ValuePath> readonlyPathParameters = Collections.unmodifiableMap(pathParameters);
    Map<String, ValuePath> queryParameters = new HashMap<>();
    private Map<String, ValuePath> readonlyQueryParameters = Collections.unmodifiableMap(queryParameters);
    Map<String, ValuePath> headerParameters = new HashMap<>();
    private Map<String, ValuePath> readonlyHeaderParameters = Collections.unmodifiableMap(headerParameters);
    ValuePath body;

    MethodModel(MethodDescriptor method) {
        this.method = method;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public MethodDescriptor getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public ValuePath getBody() {
        return body;
    }

    public List<ParameterModel> getParameters() {
        return readonlyParameters;
    }

    public Map<String, ValuePath> getPathParameters() {
        return readonlyPathParameters;
    }

    public Map<String, ValuePath> getQueryParameters() {
        return readonlyQueryParameters;
    }

    public Map<String, ValuePath> getHeaderParameters() {
        return readonlyHeaderParameters;
    }

    @Override
    public MethodModel clone() {
        try {
            MethodModel copy = (MethodModel) super.clone();
            copy.parameters = new ArrayList<>(parameters);
            copy.readonlyParameters = Collections.unmodifiableList(copy.parameters);
            copy.pathParameters = pathParameters.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getKey(), entry -> entry.getValue()));
            copy.readonlyPathParameters = Collections.unmodifiableMap(pathParameters);
            copy.queryParameters = queryParameters.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getKey(), entry -> entry.getValue()));
            copy.readonlyQueryParameters = Collections.unmodifiableMap(queryParameters);
            copy.headerParameters = headerParameters.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getKey(), entry -> entry.getValue()));
            copy.readonlyHeaderParameters = Collections.unmodifiableMap(headerParameters);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
