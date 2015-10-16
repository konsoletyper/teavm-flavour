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
package org.teavm.flavour.regex.bytecode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.teavm.flavour.regex.Matcher;
import org.teavm.flavour.regex.Pattern;

/**
 *
 * @author Alexey Andreev
 */
class CompiledPattern implements Pattern {
    private Constructor<? extends Matcher> constructor;

    public CompiledPattern(Constructor<? extends Matcher> constructor) {
        this.constructor = constructor;
    }

    @Override
    public Matcher matcher() {
        try {
            return constructor.newInstance();
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("Error instantiating matcher", e);
        }
    }
}
