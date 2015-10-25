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

import java.util.function.Consumer;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSDate;
import org.teavm.jso.core.JSRegExp;
import org.teavm.jso.core.JSString;


/**
 *
 * @author Alexey Andreev
 */
final class Routing {
    private Routing() {
    }

    static PathReader getReader(Route route) {
        return getReaderImpl(route.getClass().getName());
    }

    private static native PathReader getReaderImpl(String className);

    static native <T extends Route> T createBuilderProxy(Class<T> routeType, Consumer<String> consumer);

    static long parseDate(String text) {
        JSRegExp regex = JSRegExp.create("(\\d{4})-(\\d{2})-(\\d{2})(T(\\d{2}):(\\d{2}):(\\d{2}))?");
        JSArray<JSString> groups = regex.exec(JSString.valueOf(text));
        JSDate date = JSDate.create(parseInt(groups.get(1)), parseInt(groups.get(2)) - 1, parseInt(groups.get(3)),
                parseInt(groups.get(5)), parseInt(groups.get(6)), parseInt(groups.get(7)));
        return (long) date.getTime();
    }

    @JSBody(params = "string", script = "return typeof(string) != 'undefined' ? parseInt(string) : 0;")
    static native int parseInt(JSString string);
}
