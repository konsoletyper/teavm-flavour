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
package org.teavm.flavour.directives.standard.test;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.NodeList;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTestRunner;

@RunWith(TeaVMTestRunner.class)
@SkipJVM
public class StandardDirectivesTest {
    private HTMLDocument document;
    private HTMLElement root;

    public StandardDirectivesTest() {
        document = Window.current().getDocument();
        root = document.createElement("div");
        document.getBody().appendChild(root);
    }

    @Test
    public void foreachWorks() {
        ForeachWorksModel model = new ForeachWorksModel();
        Component component = Templates.bind(model, root);

        assertEquals(0, toList(root.getElementsByTagName("div")).size());

        model.collection.add("foo");
        model.collection.add("bar");
        model.collection.add("baz");
        component.render();
        String[] values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals(new String[] { "foo", "bar", "baz" }, values);

        model.collection.add("qqq");
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals(new String[] { "foo", "bar", "baz", "qqq" }, values);

        model.collection.add(1, "www");
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals(new String[] { "foo", "www", "bar", "baz", "qqq" }, values);

        model.collection.remove(2);
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals(new String[] { "foo", "www", "baz", "qqq" }, values);

        model.collection.clear();
        component.render();
        assertEquals(0, toList(root.getElementsByTagName("div")).size());
    }

    @BindTemplate("templates/foreach-works.html")
    static class ForeachWorksModel {
        public List<String> collection = new ArrayList<>();
    }

    @Test
    public void chooseWorks() {
        ChooseWorksModel model = new ChooseWorksModel();
        Component component = Templates.bind(model, root);

        component.render();
        assertEquals("one", document.getElementById("value").getAttribute("class"));

        model.index = 2;
        component.render();
        assertEquals("two", document.getElementById("value").getAttribute("class"));

        model.index = 3;
        component.render();
        assertEquals("many", document.getElementById("value").getAttribute("class"));
    }

    @BindTemplate("templates/choose-works.html")
    static class ChooseWorksModel {
        public int index = 1;
    }

    @Test
    public void letWorks() {
        LetWorksModel model = new LetWorksModel();
        Component component = Templates.bind(model, root);

        model.a = 23;
        model.b = "foo";
        component.render();
        assertEquals("24:foo2", document.getElementById("value").getAttribute("class"));
    }

    @BindTemplate("templates/let-works.html")
    static class LetWorksModel {
        public int a;
        public String b;
    }

    private static List<HTMLElement> toList(NodeList<? extends HTMLElement> nodeList) {
        List<HTMLElement> list = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); ++i) {
            list.add(nodeList.get(i));
        }
        return list;
    }

    private static String[] toStrings(NodeList<? extends HTMLElement> nodeList) {
        List<HTMLElement> elements = toList(nodeList);
        String[] result = new String[elements.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = elements.get(i).getAttribute("class");
        }
        return result;
    }
}
