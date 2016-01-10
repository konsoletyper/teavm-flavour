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
import org.teavm.flavour.mp.CompileTime;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.routing.emit.RoutingImpl;
import org.teavm.jso.browser.Window;

/**
 *
 * @author Alexey Andreev
 */
@CompileTime
public final class Routing {
    private Routing() {
    }

    @Reflected
    public static native <T extends Route> T build(Class<T> routeType, Consumer<String> consumer);
    @SuppressWarnings("unchecked")
    private static <T extends Route> void build(Emitter<T> em, ReflectClass<T> routeType,
            Value<Consumer<String>> consumer) {
        em.returnValue(() -> (T) RoutingImpl.getImplementorByClass(routeType.asJavaClass()).write(consumer.get()));
    }

    @Reflected
    public static native <T extends Route> T open(Window window, Class<T> routeType);
    private static <T extends Route> void open(Emitter<T> em, Value<Window> window, ReflectClass<T> routeType) {
        em.returnValue(() -> {
            Window w = window.get();
            return build(routeType.asJavaClass(), hash -> w.getLocation().setHash(hash));
        });
    }

    @Reflected
    public static native <T extends Route> T open(Class<T> routeType);
    private static <T extends Route> void open(Emitter<T> em, ReflectClass<T> routeType) {
        em.returnValue(() -> open(Window.current(), routeType.asJavaClass()));
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
