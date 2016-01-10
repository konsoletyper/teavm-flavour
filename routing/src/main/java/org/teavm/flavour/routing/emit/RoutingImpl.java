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
package org.teavm.flavour.routing.emit;

import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.ReflectValue;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.routing.Route;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSDate;
import org.teavm.jso.core.JSRegExp;
import org.teavm.jso.core.JSString;

/**
 *
 * @author Alexey Andreev
 */
public final class RoutingImpl {
    private RoutingImpl() {
    }

    @Reflected
    public static native PathImplementor getImplementor(Route route);
    private static void getImplementor(Emitter<PathImplementor> em, ReflectValue<Route> route) {
        ReflectClass<Route> cls = route.getReflectClass();
        em.returnValue(() -> getImplementorByClass(cls.asJavaClass()));
    }

    @Reflected
    public static native PathImplementor getImplementorByClass(Class<?> routeType);
    private static void getImplementorByClass(Emitter<PathImplementor> em, ReflectClass<?> routeType) {
        em.returnValue(RouteImplementorEmitter.getInstance(em.getContext()).emitParser(em, routeType));
    }

    @Reflected
    public static native PathImplementor getImplementorByClassImpl(Class<?> routeType);
    private static void getImplementorByClassImpl(Emitter<PathImplementor> em, ReflectClass<?> routeType) {
        em.returnValue(RouteImplementorEmitter.getInstance(em.getContext()).emitInterfaceParser(em, routeType));
    }

    public static long parseDate(String text) {
        JSRegExp regex = JSRegExp.create("(\\d{4})-(\\d{2})-(\\d{2})(T(\\d{2}):(\\d{2}):(\\d{2}))?");
        JSArray<JSString> groups = regex.exec(JSString.valueOf(text));
        JSDate date = JSDate.create(parseInt(groups.get(1)), parseInt(groups.get(2)) - 1, parseInt(groups.get(3)),
                parseInt(groups.get(5)), parseInt(groups.get(6)), parseInt(groups.get(7)));
        return (long) date.getTime();
    }

    public static String dateToString(long millis) {
        JSDate date = JSDate.create(millis);
        return padYear(date.getUTCFullYear())
                .concat(JSString.valueOf("-")).concat(padDate(date.getUTCMonth() + 1))
                .concat(JSString.valueOf("-")).concat(padDate(date.getUTCDate()))
                .concat(JSString.valueOf("T")).concat(padDate(date.getUTCHours()))
                .concat(JSString.valueOf(":")).concat(padDate(date.getUTCMinutes()))
                .concat(JSString.valueOf(":")).concat(padDate(date.getUTCSeconds()))
                .stringValue();
    }

    @JSBody(params = "string", script = "return typeof(string) != 'undefined' ? parseInt(string) : 0;")
    static native int parseInt(JSString string);

    static JSString padYear(int value) {
        JSString str = intToString(value);
        str = JSString.valueOf("0000").substring(str.getLength()).concat(str);
        return str;
    }

    static JSString padDate(int value) {
        return value < 10 ? JSString.valueOf("0").concat(intToString(value)) : intToString(value);
    }

    @JSBody(params = "num", script = "return num.toString();")
    static native JSString intToString(int num);
}
