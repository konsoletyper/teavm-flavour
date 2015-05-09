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
package org.teavm.flavour.example;

import org.teavm.dom.browser.Window;
import org.teavm.dom.events.EventListener;
import org.teavm.dom.events.MouseEvent;
import org.teavm.dom.html.HTMLButtonElement;
import org.teavm.dom.html.HTMLDocument;
import org.teavm.dom.html.HTMLElement;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Templates;
import org.teavm.javascript.spi.Async;
import org.teavm.jso.JS;
import org.teavm.platform.async.AsyncCallback;

/**
 *
 * @author Alexey Andreev
 */
public final class Popup {
    private static Window window = (Window)JS.getGlobal();
    private static HTMLDocument document = window.getDocument();

    private Popup() {
    }

    @Async
    public static native void showModal(PopupContent content);

    private static void showModal(PopupContent content, final AsyncCallback<Void> callback) {
        final HTMLElement wrapper = document.getElementById("popup-wrapper");
        final HTMLButtonElement closeButton = (HTMLButtonElement)document.getElementById("popup-close-button");
        wrapper.getStyle().removeProperty("display");
        final Component root = Templates.bind(content, "popup");
        content.setDelegate(new PopupDelegate() {
            @Override public void close() {
                wrapper.getStyle().setProperty("display", "none");
                root.destroy();
                callback.complete(null);
            }
        });
        closeButton.addEventListener("click", new EventListener<MouseEvent>() {
            @Override
            public void handleEvent(MouseEvent evt) {
                wrapper.getStyle().setProperty("display", "none");
                root.destroy();
                callback.complete(null);
            }
        });
    }
}
