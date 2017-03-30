/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.components.html;

import org.teavm.flavour.templates.BindAttributeComponent;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.Renderable;
import org.teavm.flavour.templates.ValueChangeListener;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLInputElement;

@BindAttributeComponent(name = "checked-change")
public class CheckedChangeBinder implements Renderable {
    private HTMLInputElement element;
    private ValueChangeListener<Boolean> listener;
    private boolean bound;

    public CheckedChangeBinder(ModifierTarget target) {
        element = (HTMLInputElement) target.getElement();
    }

    @BindContent
    public void setListener(ValueChangeListener<Boolean> listener) {
        this.listener = listener;
    }

    @Override
    public void render() {
        if (!bound) {
            bound = true;
            element.addEventListener("change", nativeListener);
        }
    }

    @Override
    public void destroy() {
        if (bound) {
            bound = false;
            element.removeEventListener("change", nativeListener);
        }
    }

    private EventListener nativeListener = new EventListener() {
        @Override
        public void handleEvent(Event evt) {
            listener.changed(element.isChecked());
        }
    };
}
