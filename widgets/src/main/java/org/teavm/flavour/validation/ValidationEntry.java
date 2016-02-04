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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;

/**
 *
 * @author Alexey Andreev
 */
public class ValidationEntry<T> {
    Validation<T> validation = new Validation<>();

    @BindAttribute(name = "as")
    public Validation<T> getValidation() {
        return validation;
    }

    @BindAttribute(name = "convert")
    public void setConverter(Supplier<Converter<T>> converter) {
        validation.converter = converter;
    }

    @BindAttribute(name = "of")
    public void setConsumer(Consumer<T> consumer) {
        validation.consumer = consumer;
    }

    @BindAttribute(name = "of")
    public void setSupplier(Supplier<T> supplier) {
        validation.supplier = supplier;
    }

    @BindDirective(name = "check")
    public void setRules(List<ValidationRule<T>> rules) {
        this.validation.rules.addAll(rules);
    }
}
