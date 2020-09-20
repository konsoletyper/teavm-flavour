/*
 *  Copyright 2016 Alexey Andreev.
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

import java.util.function.Consumer;
import org.teavm.flavour.routing.emit.PathImplementor;
import org.teavm.flavour.routing.emit.RoutingImpl;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;

public final class Routing {
    private static RoutingStrategy routingStrategy = new HashRoutingStrategy();

    public static void usePathStrategy() {
        routingStrategy = new PathRoutingStrategy();
    }

    public static void usePathStrategy(String rootPath) {
        routingStrategy = new PathRoutingStrategy(rootPath);
    }

    public static RoutingStrategy getRoutingStrategy() {
        return routingStrategy;
    }

    public static void addListener(RoutingListener listenerNew) {
        routingStrategy.addListener(listenerNew);
    }

    static String parse(Window window) {
        return routingStrategy.parse(window);
    }

    public static boolean isBlank(Window window) {
        return routingStrategy.isBlank(window);
    }

    public static String makeUri(HTMLElement element, String path) {
        return routingStrategy.makeUri(element, path);
    }

    public static void addListener(Window window, EventListener<Event> listener) {
        routingStrategy.addListener(window, listener);
    }

    private Routing() {
    }

    public static <T extends Route> T build(Class<T> routeType, Consumer<String> consumer) {
        PathImplementor implementor = RoutingImpl.getImplementorByClass(routeType);
        if (implementor == null) {
            throw new IllegalArgumentException("Invalid route type: " + routeType);
        }
        @SuppressWarnings("unchecked")
        T result = (T) implementor.write(consumer);
        if (result == null) {
            throw new IllegalArgumentException("Invalid route type: " + routeType);
        }
        return result;
    }

    public static <T extends Route> T open(Window window, Class<T> routeType) {
        return (T) routingStrategy.open(window, routeType);
    }

    public static <T extends Route> T open(Class<T> routeType) {
        return open(Window.current(), routeType);
    }

    public static void open(String path) {
        routingStrategy.open(path);
    }

    static <T extends Route> T replace(Window window, Class<T> routeType) {
        return (T) routingStrategy.replace(window, routeType);
    }

    static <T extends Route> T replace(Class<T> routeType) {
        return replace(Window.current(), routeType);
    }
}
