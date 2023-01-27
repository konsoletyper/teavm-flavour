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

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Methods required of a pluggable routing implementation.
 */
public interface RoutingStrategy<E extends Event> {
    void notifyListeners();

    void addListener(RoutingListener listenerNew);

    <T extends Route> T open(Window window, Class<T> routeType);

    <T extends Route> T replace(Window window, Class<T> routeType);

    String parse(Window window);

    boolean isBlank(Window window);

    String makeUri(HTMLElement element, String path);

    void addListener(Window window, EventListener<E> listener);

    void open(String path);
}
