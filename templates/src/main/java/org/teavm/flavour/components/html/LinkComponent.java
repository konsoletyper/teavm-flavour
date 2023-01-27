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
package org.teavm.flavour.components.html;

import java.util.function.Consumer;
import org.teavm.flavour.routing.HashRoutingStrategy;
import org.teavm.flavour.routing.Routing;
import org.teavm.flavour.templates.BindAttributeComponent;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.Renderable;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;

@BindAttributeComponent(name = "link", elements = "a")
public class LinkComponent implements Renderable, EventListener<Event> {
    private HTMLElement element;
    private String value;
    private Consumer<Consumer<String>> path;

    public LinkComponent(ModifierTarget target) {
        this.element = target.getElement();
    }

    private Consumer<String> linkConsumer = str -> {
        value = Routing.makeUri(element, str);
    };

    @BindContent
    public void setPath(Consumer<Consumer<String>> path) {
        this.path = path;
    }

    @Override
    public void render() {
      if (Routing.getRoutingStrategy() instanceof HashRoutingStrategy) {
        path.accept(linkConsumer);
        setHref(element, value);
      } else {
        path.accept(linkConsumer);
        element.addEventListener("click", this);
      }
    }

    @Override
    public void destroy() {
    }

    @JSBody(params = { "elem", "value" }, script = "elem.href = value;")
    private static native void setHref(HTMLElement elem, String value);

    @Override
    public void handleEvent(Event e) {
        Routing.open(value);
    }
}
