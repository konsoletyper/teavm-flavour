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
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.NodeList;

/**
 *
 * @author Alexey Andreev
 */
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
