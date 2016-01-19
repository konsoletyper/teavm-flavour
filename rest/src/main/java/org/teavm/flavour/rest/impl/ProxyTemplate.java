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
import org.teavm.flavour.rest.processor.RequestProcessor;
import org.teavm.flavour.rest.processor.ResponseProcessor;

/**
 *
 * @author Alexey Andreev
 */
class ProxyTemplate {
    private List<RequestProcessor> requestProcessors;
    private List<ResponseProcessor> responseProcessors;
    private String prefix;

    ProxyTemplate(FactoryTemplate factory, String prefix) {
        requestProcessors = new ArrayList<>(factory.requestProcessors);
        responseProcessors = new ArrayList<>(factory.responseProcessors);
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        this.prefix = prefix;
    }

    protected ResponseImpl send(RequestImpl request) {
        request.setUrl(prefix + request.url);
        for (RequestProcessor processor : requestProcessors) {
            processor.process(request);
        }
        ResponseImpl response = request.send();
        for (ResponseProcessor processor : responseProcessors) {
            processor.process(response);
        }
        return response;
    }
}
