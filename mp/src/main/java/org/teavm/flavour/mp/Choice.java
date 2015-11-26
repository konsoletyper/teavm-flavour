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
package org.teavm.flavour.mp;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class Choice<T> {
    Emitter<?> parent;
    Map<Integer, Emitter<T>> options = new HashMap<>();
    Value<Integer> argument;
    Value<T> returnValue = new Value<>();
    Emitter<T> defaultOption = new Emitter<>(returnValue);

    public Choice(Emitter<?> parent, Value<Integer> argument) {
        this.parent = parent;
        this.argument = argument;
    }

    public Emitter<T> option(int value) {
        parent.acquire();
        Emitter<T> emitter = new Emitter<>(returnValue);
        options.put(value, emitter);
        return emitter;
    }

    public Emitter<T> defaultOption() {
        return defaultOption;
    }

    void lock() {
        options.values().forEach(option -> option.lock());
    }

    T eval() {
        Emitter<T> option = options.get(argument.get());
        if (option == null) {
            option = defaultOption;
        }
        return option.eval();
    }
}
