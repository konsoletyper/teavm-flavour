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
package org.teavm.flavour.widgets;

import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Templates;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

@BindTemplate("templates/flavour/widgets/popup.html")
public final class Popup {
    private static HTMLDocument document = HTMLDocument.current();
    private Fragment content;
    private Component component;
    private AsyncCallback<Void> callback;
    private HTMLElement wrapper;

    private Popup(Fragment content) {
        this.content = content;
    }

    public Fragment getContent() {
        return content;
    }

    public void close() {
        component.destroy();
        wrapper.getParentNode().removeChild(wrapper);
        wrapper = null;
        component = null;
        callback.complete(null);
    }

    @Async
    public static native void showModal(PopupContent content);

    private static void showModal(PopupContent content, AsyncCallback<Void> callback) {
        Popup popup = new Popup(Templates.create(content));
        popup.wrapper = document.createElement("div");
        document.getBody().appendChild(popup.wrapper);
        popup.component = Templates.bind(popup, popup.wrapper);
        popup.callback = callback;
        content.setDelegate(popup::close);
    }
}
