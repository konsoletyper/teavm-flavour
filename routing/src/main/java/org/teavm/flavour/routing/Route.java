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

import org.teavm.flavour.routing.emit.PathImplementor;
import org.teavm.flavour.routing.emit.RoutingImpl;
import org.teavm.jso.browser.Location;
import org.teavm.jso.browser.Window;

public interface Route {
    default boolean parse() {
        return parse(Window.current());
    }

    default boolean parse(Window window) {
        Location location = window.getLocation();
        String hash = location.getHash();
        if (hash.startsWith("#")) {
            hash = hash.substring(1);
        }
        return parse(hash);
    }

    default boolean parse(String path) {
        PathImplementor reader = RoutingImpl.getImplementor(this);
        if (reader == null) {
            throw new IllegalArgumentException("Wrong route interface: " + getClass().getName());
        }
        return reader.read(path, this);
    }
}
