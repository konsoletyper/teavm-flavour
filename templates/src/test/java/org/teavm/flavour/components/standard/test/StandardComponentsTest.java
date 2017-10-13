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
package org.teavm.flavour.components.standard.test;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
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
public class StandardComponentsTest {
    private HTMLDocument document;
    private HTMLElement root;

    public StandardComponentsTest() {
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
        assertArrayEquals("Initial list construction", new String[] { "foo", "bar", "baz" }, values);

        model.collection.add("qqq");
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Add element to end", new String[] { "foo", "bar", "baz", "qqq" }, values);

        model.collection.add(1, "www");
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Insert element ", new String[] { "foo", "www", "bar", "baz", "qqq" }, values);

        model.collection.add(4, "ttt");
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Insert element 2", new String[] { "foo", "www", "bar", "baz", "ttt", "qqq" }, values);

        model.collection.add(0, "eee");
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Add element to start",
                new String[] { "eee", "foo", "www", "bar", "baz", "ttt", "qqq" }, values);

        model.collection.remove(3);
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Remove element from inside list",
                new String[] { "eee", "foo", "www", "baz", "ttt", "qqq" }, values);

        model.collection.remove(0);
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Remove first element", new String[] { "foo", "www", "baz", "ttt", "qqq" }, values);

        model.collection.remove(4);
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Remove last element", new String[] { "foo", "www", "baz", "ttt" }, values);

        model.collection.clear();
        model.collection.addAll(Arrays.asList("1", "2", "3", "4", "5"));
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("Refresh list", new String[] { "1", "2", "3", "4", "5" }, values);

        model.collection.clear();
        component.render();
        assertEquals("Clear list", 0, toList(root.getElementsByTagName("div")).size());
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

    @Test
    public void withWorks() {
        WithWorksModel model = new WithWorksModel();
        model.a = "-foo-";
        Component component = Templates.bind(model, root);
        component.render();
        assertEquals("foo", document.getElementById("value").getAttribute("class"));
    }

    @BindTemplate("templates/with-works.html")
    static class WithWorksModel {
        public String a;

        public String f() {
            return a;
        }
    }

    @Test
    public void ifSurroundedWithElements() {
        IfSurroundedModel model = new IfSurroundedModel();
        Component component = Templates.bind(model, root);

        component.render();
        String[] values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("first", new String[] { "foo", "bar", "baz" }, values);

        model.a = true;
        component.render();
        values = toStrings(root.getElementsByTagName("div"));
        assertArrayEquals("second", new String[] { "foo", "ttt", "baz" }, values);
    }

    @BindTemplate("templates/if-surrounded.html")
    static class IfSurroundedModel {
        public boolean a;
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
