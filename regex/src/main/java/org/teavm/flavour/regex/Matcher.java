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
package org.teavm.flavour.regex;

/**
 *
 * @author Alexey Andreev
 */
public interface Matcher {
    Matcher feed(String text);

    Matcher end();

    boolean isValid();

    default boolean isTerminal() {
        return getDomain() >= 0;
    }

    int getDomain();

    Matcher restart();

    default boolean matches(String text) {
        restart().feed(text).end();
        return isValid();
    }

    default int domain(String text) {
        restart().feed(text).end();
        return getDomain();
    }
}
