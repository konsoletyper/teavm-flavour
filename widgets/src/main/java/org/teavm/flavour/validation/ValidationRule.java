/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.flavour.validation;

import java.util.function.Predicate;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.OptionalBinding;

public class ValidationRule<T> {
    Predicate<T> predicate;
    boolean valid = true;

    @BindAttribute(name = "as")
    @OptionalBinding
    public boolean isValid() {
        return valid;
    }

    @BindAttribute(name = "rule")
    public void setPredicate(Predicate<T> predicate) {
        this.predicate = predicate;
    }
}
