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

import org.teavm.jso.browser.Location;
import org.teavm.jso.browser.Window;

/**
 *
 * @author Alexey Andreev
 */
public class PathChangeHandler {
    private Window window;
    private static PathParser parser;

    public PathChangeHandler(Window window) {
        this.window = window;
    }

    public PathChangeHandler onChange(Object pathSet) {
        if (parser == null) {
            parser = new PathParser();
            window.listenHashChange(event -> {
                StringBuilder sb = new StringBuilder();
                Location loc = window.getLocation();
                sb.append(loc.getPathName());
                if (!loc.getHash().isEmpty()) {
                    sb.append('#').append(loc.getHash());
                }
                parser.parse(sb.toString());
            });
        }
        parser.addPathSet(pathSet);
        return this;
    }
}
