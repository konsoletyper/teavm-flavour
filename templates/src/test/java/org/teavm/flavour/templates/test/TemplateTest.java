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
package org.teavm.flavour.templates.test;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 *
 * @author Alexey Andreev
 */
public class TemplateTest {
    private HTMLDocument document;
    private HTMLElement root;

    public TemplateTest() {
        document = Window.current().getDocument();
        root = document.createElement("div");
        document.getBody().appendChild(root);
    }

    @Test
    public void bindsLambdaToAttribute() {
        ModelForLambdaToAttribute model = new ModelForLambdaToAttribute();
        model.property = "foo";
        Component component = Templates.bind(model, root);
        HTMLElement elem = document.getElementById("value-copy");

        assertEquals("foo", elem.getAttribute("class"));

        model.property = "bar";
        component.render();
        assertEquals("bar", elem.getAttribute("class"));
    }

    @BindTemplate("templates/binds-lambda-to-attribute.html")
    static class ModelForLambdaToAttribute {
        public String property;
    }

    @Test
    public void bindsVariableToAttribute() {
        ModelForVariable model = new ModelForVariable();
        Component component = Templates.bind(model, root);
        HTMLElement elem = document.getElementById("value-copy");

        ExposeValueComponent.value = "foo";
        component.render();
        assertEquals("foo", elem.getAttribute("class"));

        ExposeValueComponent.value = "bar";
        component.render();
        assertEquals("bar", elem.getAttribute("class"));
    }

    @BindTemplate("templates/binds-variable-to-attribute.html")
    static class ModelForVariable {
    }
}
