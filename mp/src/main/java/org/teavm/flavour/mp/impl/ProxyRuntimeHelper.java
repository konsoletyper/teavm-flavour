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
package org.teavm.flavour.mp.impl;

import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public final class ProxyRuntimeHelper {
    private ProxyRuntimeHelper() {
    }

    public static List<Object> add(int value, List<Object> args) {
        args.add(value);
        return args;
    }

    public static List<Object> add(long value, List<Object> args) {
        args.add(value);
        return args;
    }

    public static List<Object> add(float value, List<Object> args) {
        args.add(value);
        return args;
    }

    public static List<Object> add(double value, List<Object> args) {
        args.add(value);
        return args;
    }

    public static List<Object> add(Object value, List<Object> args) {
        args.add(value);
        return args;
    }
}
