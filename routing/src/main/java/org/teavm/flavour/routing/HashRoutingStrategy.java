/*
 *  Copyright 2020 ScraM-Team.
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

import static org.teavm.flavour.routing.Routing.build;
import org.teavm.jso.browser.Location;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.HashChangeEvent;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Handles routing via URL hashes.
 */
class HashRoutingStrategy implements RoutingStrategy<HashChangeEvent> {
    @Override
    public void notifyListeners() {
    }

    @Override
    public void addListener(RoutingListener listenerNew) {
    }

    @Override
    public <T extends Route> T open(Window window, Class<T> routeType) {
        return build(routeType, hash -> window.getLocation().setHash(hash));
    }

    @Override
    public <T extends Route> T replace(Window window, Class<T> routeType) {
        return build(routeType, hash -> {
            window.getHistory().replaceState(null, null, "#" + Window.encodeURI(hash));
        });
    }

    @Override
    public String parse(Window window) {
        Location location = window.getLocation();
        String hash = location.getHash();
        if (hash.startsWith("#")) {
            hash = hash.substring(1);
        }
        return hash;
    }

    @Override
    public boolean isBlank(Window window) {
        return window.getLocation().getHash().isEmpty() || window.getLocation().getHash().equals("#");
    }

    @Override
    public String makeUri(HTMLElement element, String hash) {
        return "#" + hash;
    }

    @Override
    public void addListener(Window window, EventListener<HashChangeEvent> listener) {
        window.listenHashChange(listener);
    }
}
