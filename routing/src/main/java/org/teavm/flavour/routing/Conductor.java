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
package org.teavm.flavour.routing;

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.HashChangeEvent;

/**
 *
 * @author Alexey Andreev
 */
public class Conductor {
    Window window;
    private List<Route> routes = new ArrayList<>();
    private Runnable errorHandler;

    public Conductor() {
        this(Window.current());
    }

    public Conductor(Window window) {
        attach(window);
    }

    public Window getWindow() {
        return window;
    }

    public void attach(Window window) {
        if (this.window != null) {
            throw new IllegalStateException("This dispatcher is already attached to a window");
        }
        this.window = window;
        window.listenHashChange(listener);
    }

    public void detach() {
        if (this.window == null) {
            throw new IllegalStateException("This dispatcher is not attached to any window");
        }
    }

    public Conductor add(Route route) {
        if (!routes.contains(route)) {
            routes.add(route);
        }
        return this;
    }

    public Conductor remove(Route route) {
        routes.remove(route);
        return this;
    }

    EventListener<HashChangeEvent> listener = evt -> {
        for (Route route : routes) {
            if (route.parse(window)) {
                return;
            }
        }
        if (errorHandler != null) {
            errorHandler.run();
        }
    };
}
