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
package org.teavm.flavour.rest.impl;

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.rest.ResourceFactory;
import org.teavm.flavour.rest.processor.RequestProcessor;
import org.teavm.flavour.rest.processor.ResponseProcessor;

/**
 *
 * @author Alexey Andreev
 */
abstract class FactoryTemplate<T> implements ResourceFactory<T> {
    List<RequestProcessor> requestProcessors = new ArrayList<>();
    List<ResponseProcessor> responseProcessors = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public T createResource(String baseUrl) {
        ProxyTemplate resource = createResourceImpl(baseUrl);
        return (T) resource;
    }

    @Override
    public void add(RequestProcessor processor) {
        requestProcessors.add(processor);
    }

    @Override
    public void add(ResponseProcessor processor) {
        responseProcessors.add(processor);
    }

    abstract ProxyTemplate createResourceImpl(String baseUrl);
}
