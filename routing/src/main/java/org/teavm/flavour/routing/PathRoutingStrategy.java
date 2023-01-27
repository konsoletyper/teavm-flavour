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

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Location;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLHeadElement;
import org.teavm.jso.dom.xml.NodeList;

/**
 * Handles routing via URL path changes.
 */
public class PathRoutingStrategy implements RoutingStrategy<Event> {
    private List<RoutingListener> listListeners = new ArrayList<>();

    @JSBody(params = {"document"}, script = "return document.baseURI;")
    native public static String getBaseUri(HTMLDocument document);

    @JSBody(params = {"window", "listener"}, script = "window.onpopstate = listener;")
    native public static void listenPopState(Window window, EventListener<Event> listener);

    public PathRoutingStrategy() {
        setupBaseHref("");
    }

    public PathRoutingStrategy(String rootPath) {
        setupBaseHref(rootPath);
    }

    public void setupBaseHref(String rootPath) {
        final HTMLDocument document = Window.current().getDocument();
        HTMLHeadElement head = document.getHead();
        NodeList<? extends HTMLElement> baseNodes = head.getElementsByTagName("base");
        if (baseNodes.getLength() == 0) {
            HTMLElement base = document.createElement("base");
            head.appendChild(base);
            final Location location = Window.current().getLocation();
            base.setAttribute("href", location.getProtocol() + "//" + location.getHost() + "/"
                    + rootPath + (rootPath.isEmpty() ? "" : "/"));
        }
    }

    private String trim(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        else return path;
    }

    @Override
    public void notifyListeners() {
        for (RoutingListener listener : listListeners) {
            listener.handleLocationChange();
        }
    }

    @Override
    public void addListener(RoutingListener listenerNew) {
        listListeners.add(listenerNew);
    }

    @Override
    public <T extends Route> T open(Window window, Class<T> routeType) {
        return Routing.build(routeType, hash -> {
            open(window, getBaseUri(window.getDocument()) + Window.encodeURI(trim(hash)));
        });
    }

    public void open(Window window, String path) {
        window.getHistory().pushState(null, null, path);
        notifyListeners();
    }

    @Override
    public <T extends Route> T replace(Window window, Class<T> routeType) {
        return Routing.build(routeType, hash -> {
            window.getHistory().replaceState(null, null,
                    getBaseUri(window.getDocument()) + Window.encodeURI(trim(hash)));
            notifyListeners();
        });
    }

    @Override
    public String parse(Window window) {
        Location location = window.getLocation();
        String baseUri = getBaseUri(window.getDocument());
        String fullUrl = location.getFullURL();
        String path = fullUrl.substring(baseUri.length());
        if (path.startsWith("#")) {
            path = path.substring(1);
        }
        return "/" + path;
    }

    @Override
    public boolean isBlank(Window window) {
        String path = parse(window);
        return path.isEmpty();
    }

    @Override
    public String makeUri(HTMLElement element, String path) {
        String pathTrimmed = path.startsWith("/") ? path.substring(1) : path;
        return getBaseUri(element.getOwnerDocument()) + pathTrimmed;
    }

    @Override
    public void addListener(Window window, EventListener<Event> listener) {
        listenPopState(window, (EventListener<Event>) listener);
    }

    @Override
    public void open(String path) {
        open(Window.current(), path);
    }
}
