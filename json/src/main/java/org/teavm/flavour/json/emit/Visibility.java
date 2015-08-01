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
package org.teavm.flavour.json.emit;

import org.teavm.model.AccessLevel;

/**
 *
 * @author Alexey Andreev
 */
enum Visibility {
    ANY,
    NON_PRIVATE,
    PROTECTED_AND_PUBLIC,
    PUBLIC_ONLY,
    NONE,
    DEFAULT;

    public boolean match(AccessLevel level) {
        switch (this) {
            case ANY:
                return true;
            case NON_PRIVATE:
                return level != AccessLevel.PRIVATE;
            case NONE:
                return false;
            case PUBLIC_ONLY:
                return level == AccessLevel.PUBLIC;
            case PROTECTED_AND_PUBLIC:
                return level == AccessLevel.PUBLIC || level == AccessLevel.PROTECTED;
            default:
                return false;
        }
    }
}
