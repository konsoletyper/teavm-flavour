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

import org.teavm.flavour.routing.Route;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

public abstract class ApplicationTemplate implements Route {
    private Fragment content;
    private Object currentView;

    public Fragment getContent() {
        return content;
    }

    public Object getCurrentView() {
        return currentView;
    }

    protected void setView(Object view) {
        this.currentView = view;
        content = Templates.create(view);
    }

    public void bind(HTMLElement elem) {
        Templates.bind(this, elem);
    }

    public void bind(String id) {
        bind(HTMLDocument.current().getElementById(id));
    }
}
