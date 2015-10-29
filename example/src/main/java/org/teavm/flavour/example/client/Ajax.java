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
package org.teavm.flavour.example.client;

import java.io.IOException;
import org.teavm.javascript.spi.Async;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.platform.async.AsyncCallback;

/**
 *
 * @author Alexey Andreev
 */
public final class Ajax {
    private Ajax() {
    }

    @Async
    public static native String get(String address) throws IOException;

    private static void get(String address, AsyncCallback<String> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open("GET", address);
        setup(xhr);
        xhr.onComplete(() -> {
            if (xhr.getStatus() == 200) {
                callback.complete(xhr.getResponseText());
            } else {
                callback.error(new IOException("HTTP Error " + xhr.getStatus() + " " + xhr.getStatusText()));
            }
        });
        xhr.send();
    }

    @Async
    public static native String post(String address, String content) throws IOException;

    private static void post(String address, String content, AsyncCallback<String> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open("POST", address);
        setup(xhr);
        xhr.onComplete(() -> {
            if (xhr.getStatus() == 200) {
                callback.complete(xhr.getResponseText());
            } else {
                callback.error(new IOException("HTTP Error " + xhr.getStatus() + " " + xhr.getStatusText()));
            }
        });
        xhr.send(content);
    }

    @Async
    public static native void put(String address, String content) throws IOException;

    private static void put(String address, String content, AsyncCallback<Void> callback) {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open("PUT", address);
        setup(xhr);
        xhr.onComplete(() -> {
            if (xhr.getStatus() == 200) {
                callback.complete(null);
            } else {
                callback.error(new IOException("HTTP Error " + xhr.getStatus() + " " + xhr.getStatusText()));
            }
        });
        xhr.send(content);
    }

    private static void setup(XMLHttpRequest xhr) {
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.setRequestHeader("Accept", "application/json");
    }
}
