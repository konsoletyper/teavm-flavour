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
import org.junit.runner.RunWith;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
@SkipJVM
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
        Component component = Templates.bind(model, root);
        HTMLElement elem = document.getElementById("value-copy");

        model.property = "foo";
        component.render();
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

    @Test
    public void bindsLambdaToAttributeContent() {
        ModelForAttributeLambdaToAttribute model = new ModelForAttributeLambdaToAttribute();
        Component component = Templates.bind(model, root);
        HTMLElement elem = document.getElementById("result");

        model.property = "foo";
        component.render();
        assertEquals("foo", elem.getAttribute("class"));

        model.property = "bar";
        component.render();
        assertEquals("bar", elem.getAttribute("class"));
    }

    @BindTemplate("templates/binds-attribute-lambda-to-attribute.html")
    static class ModelForAttributeLambdaToAttribute {
        public String property;
    }

    @Test
    public void infersComponentTypeArgument() {
        ModelForComponentWithTypeParameter model = new ModelForComponentWithTypeParameter();
        model.property = new A();
        Component component = Templates.bind(model, root);
        component.render();

        HTMLElement elem = document.getElementById("type-param-component");
        assertEquals("OK", elem.getAttribute("class"));
    }

    @BindTemplate("templates/infers-component-type-argument.html")
    static class ModelForComponentWithTypeParameter {
        public A property;
    }

    @Test
    public void infersWildcardTypeArgument() {
        ModelForComponentWithWildcardTypeArgument model = new ModelForComponentWithWildcardTypeArgument();
        model.property = new A();
        Component component = Templates.bind(model, root);
        component.render();

        HTMLElement elem = document.getElementById("test-component");
        assertEquals("OK;23;", elem.getAttribute("class"));
    }

    @BindTemplate("templates/infers-type-for-wildcard.html")
    static class ModelForComponentWithWildcardTypeArgument {
        public A property;
    }

    @Test
    public void supportsGenericAttributeComponent() {
        ModelForGenericAttributeComponent model = new ModelForGenericAttributeComponent();
        model.property = new B();

        Component component = Templates.bind(model, root);
        component.render();
        HTMLElement elem = document.getElementById("result");

        assertEquals("BBB", elem.getAttribute("class"));
    }

    @BindTemplate("templates/supports-generic-attribute-component.html")
    static class ModelForGenericAttributeComponent {
        public B property;
    }

    @Test
    public void infersTypeOfVariableWithWildcardType() {
        ModelForVariableWithWildcard model = new ModelForVariableWithWildcard();
        model.a = new A();
        model.b = new B();

        Component component = Templates.bind(model, root);
        component.render();

        HTMLElement elem1 = document.getElementById("result1");
        assertEquals("OK", elem1.getAttribute("class"));

        HTMLElement elem2 = document.getElementById("result2");
        assertEquals("BBB", elem2.getAttribute("class"));
    }

    @BindTemplate("templates/infers-variable-with-wildcard-type.html")
    static class ModelForVariableWithWildcard {
        public A a;
        public B b;
    }

    public class A {
        public String foo() {
            return "OK";
        }
    }

    public class B {
        @Override
        public String toString() {
            return "BBB";
        }
    }
}
