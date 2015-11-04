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

import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.rest.processor.Response;
import org.teavm.jso.JSBody;

/**
 *
 * @author Alexey Andreev
 */
class ResponseImpl implements Response {
    int status;
    Node content;
    String textContent;
    boolean validJson;
    Map<String, String> headers = new HashMap<>();
    Runnable defaultAction;

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public Node getContent() {
        return content;
    }

    @Override
    public void setContent(Node content) {
        this.content = content;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public String getTextContent() {
        return textContent;
    }

    @Override
    public boolean isValidJson() {
        return validJson;
    }

    @Override
    public void defaultAction() {
        if (defaultAction != null) {
            defaultAction.run();
        }
    }

    @JSBody(params = "node", script = "return { data : node };")
    static native Node wrapJson(Node node);
}
