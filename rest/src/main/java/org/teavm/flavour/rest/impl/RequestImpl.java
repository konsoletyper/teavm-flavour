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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.json.tree.NullNode;
import org.teavm.flavour.rest.HttpStatusException;
import org.teavm.flavour.rest.JsonParseException;
import org.teavm.flavour.rest.processor.HttpMethod;
import org.teavm.flavour.rest.processor.Request;
import org.teavm.interop.Async;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.platform.async.AsyncCallback;

/**
 *
 * @author Alexey Andreev
 */
class RequestImpl implements Request {
    HttpMethod method;
    String url;
    Node content;
    boolean contentExists;
    Map<String, String> headers = new HashMap<>();
    XMLHttpRequest xhr;

    RequestImpl(HttpMethod method, String url) {
        this(method, url, NullNode.instance());
    }

    RequestImpl(HttpMethod method, String url, Node content) {
        this.method = method;
        this.url = url;
        this.content = content;
        contentExists = true;
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public RequestImpl setMethod(HttpMethod method) {
        this.method = method;
        return this;
    }

    @Override
    public Node getContent() {
        return content;
    }

    @Override
    public RequestImpl setContent(Node content) {
        this.content = content;
        contentExists = true;
        return this;
    }

    @Override
    public RequestImpl setHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    @Override
    public RequestImpl removeHeader(String name) {
        headers.remove(name);
        return this;
    }

    @Override
    public String getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public Collection<String> getHeaders() {
        return headers.keySet();
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public RequestImpl setUrl(String url) {
        this.url = url;
        return this;
    }

    @Async
    public native ResponseImpl send();

    private void send(AsyncCallback<ResponseImpl> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open(method.name(), url);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            xhr.setRequestHeader(header.getKey(), header.getValue());
        }
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.setRequestHeader("Accept", "application/json");
        xhr.onComplete(() -> {
            ResponseImpl response = new ResponseImpl();
            response.status = xhr.getStatus();
            response.textContent = xhr.getResponseText();
            parseResponseHeaders(response.headers, xhr.getAllResponseHeaders());
            if (xhr.getStatus() / 100 == 2) {
                String text = xhr.getResponseText();
                if (text != null && !text.isEmpty()) {
                    JsonParseResult parseResult = parseJson(text);
                    response.validJson = parseResult.isSuccess();
                    if (response.validJson) {
                        response.content = parseResult.getNode();
                    } else {
                        response.defaultAction = () -> {
                            throw new JsonParseException(text);
                        };
                    }
                }
            } else {
                response.defaultAction = () -> {
                    throw new HttpStatusException(response.status, xhr.getStatusText());
                };
            }
            callback.complete(response);
        });
        xhr.send(contentExists ? content.stringify() : null);
    }

    private void parseResponseHeaders(Map<String, String> map, String text) {
        int index = 0;
        while (index < text.length()) {
            int lineEnd = text.indexOf("\r\n", index);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            int sep = text.indexOf(':', index);
            map.put(text.substring(index, sep), text.substring(sep + 1, lineEnd).trim());
            index = lineEnd + 2;
        }
    }

    @JSBody(params = "text", script = ""
            + "try {"
                + "return { success : true, node : JSON.parse(text) };"
            + "} catch (e) {"
                + "return { success : false };"
            + "}")
    private static native JsonParseResult parseJson(String text);

    interface JsonParseResult extends JSObject {
        @JSProperty
        boolean isSuccess();

        @JSProperty
        Node getNode();
    }
}
