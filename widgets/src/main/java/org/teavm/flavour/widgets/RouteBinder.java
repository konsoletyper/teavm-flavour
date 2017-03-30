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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.teavm.flavour.routing.Route;
import org.teavm.flavour.routing.Routing;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.HashChangeEvent;

public class RouteBinder {
    Window window;
    private List<Route> routes = new ArrayList<>();
    private Runnable errorHandler;
    private Route defaultRoute;
    private Consumer<Route> defaultAction;

    public RouteBinder() {
        this(Window.current());
    }

    public RouteBinder(Window window) {
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

    public RouteBinder add(Route route) {
        if (!routes.contains(route)) {
            routes.add(route);
        }
        return this;
    }

    public RouteBinder remove(Route route) {
        routes.remove(route);
        return this;
    }

    public void update() {
        if (window.getLocation().getHash().isEmpty() || window.getLocation().getHash().equals("#")) {
            defaultAction.accept(defaultRoute);
            return;
        }
        for (Route route : routes) {
            if (route.parse(window)) {
                Templates.update();
                return;
            }
        }
        if (errorHandler != null) {
            errorHandler.run();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Route> RouteBinder withDefault(Class<T> routeType, Consumer<T> action) {
        defaultRoute = Routing.open(routeType);
        defaultAction = (Consumer<Route>) action;
        return this;
    }

    EventListener<HashChangeEvent> listener = evt -> update();
}
