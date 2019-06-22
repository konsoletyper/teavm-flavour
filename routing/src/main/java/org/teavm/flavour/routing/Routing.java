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

public final class Routing {
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
        return build(routeType, hash -> window.getLocation().setHash(hash));
    }

    public static <T extends Route> T open(Class<T> routeType) {
        return open(Window.current(), routeType);
    }

    static <T extends Route> T replace(Window window, Class<T> routeType) {
        return build(routeType, hash -> {
            window.getHistory().replaceState(null, null, "#" + Window.encodeURI(hash));
        });
    }

    static <T extends Route> T replace(Class<T> routeType) {
        return replace(Window.current(), routeType);
    }
}
