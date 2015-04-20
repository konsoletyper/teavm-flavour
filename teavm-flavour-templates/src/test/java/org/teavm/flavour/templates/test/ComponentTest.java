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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.teavm.dom.browser.Window;
import org.teavm.dom.html.HTMLDocument;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomFragment;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.Variable;
import org.teavm.flavour.templates.directives.ForEachComponent;
import org.teavm.jso.JS;

/**
 *
 * @author Alexey Andreev
 */
public class ComponentTest {
    private static final Window window = (Window)JS.getGlobal();
    private static final HTMLDocument document = window.getDocument();

    @Test
    public void buildsDom() {
        Fragment fragment = new DomFragment() {
            @Override
            protected void buildDom(DomBuilder builder) {
                builder.open("ul")
                    .attribute("class", "simple")
                    .open("li")
                        .text("First item")
                    .close()
                    .open("li")
                        .text("Second ").open("b").text("item").close()
                    .close()
                .close();
            }
        };
        Slot.root(document.getBody()).append(fragment.create().getSlot());
    }

    @Test
    public void buildsList() {
        final List<Integer> integers = new ArrayList<>();
        integers.add(2);
        integers.add(3);
        integers.add(5);
        integers.add(7);
        Fragment fragment = new DomFragment() {
            @Override
            protected void buildDom(DomBuilder builder) {
                builder.open("table")
                    .open("thead")
                        .open("tr")
                            .open("th").text("x").close()
                            .open("th").text("f(x)").close()
                        .close()
                    .close()
                    .open("tbody")
                        .add(new Fragment() {
                            int index;
                            Integer item;
                            @Override public Component create() {
                                ForEachComponent<Integer> forEach = new ForEachComponent<>(Slot.create());
                                forEach.setBody(new DomFragment() {
                                    @Override
                                    protected void buildDom(DomBuilder builder) {
                                        builder.open("tr")
                                            .open("td")
                                                .text(String.valueOf(index))
                                            .close()
                                            .open("td")
                                                .text(item.toString())
                                            .close()
                                        .close();
                                    }
                                });
                                forEach.setCollection(new Computation<Iterable<Integer>>() {
                                    @Override
                                    public Iterable<Integer> perform() {
                                        return integers;
                                    }
                                });
                                forEach.setIndexVariable(new Variable<Integer>() {
                                    @Override  public void set(Integer value) {
                                        index = value;
                                    }
                                });
                                forEach.setElementVariable(new Variable<Integer>() {
                                    @Override public void set(Integer value) {
                                        item = value;
                                    }
                                });
                                return forEach;
                            }
                        })
                    .close()
                .close();
            }
        };
        Slot.root(document.getBody()).append(fragment.create().getSlot());
    }
}
