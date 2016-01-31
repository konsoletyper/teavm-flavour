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

import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.Renderable;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;

/**
 *
 * @author Alexey Andreev
 */
@BindAttributeDirective(name = "bind", elements = { "textarea", "input" })
public class BindValidation<T> implements Renderable {
    private HTMLElement element;
    private boolean bound;
    private Supplier<Validation<T>> validation;
    private Validation<T> currentValidation;

    public BindValidation(HTMLElement element) {
        this.element = element;
    }

    @BindContent
    public void setValidation(Supplier<Validation<T>> validation) {
        this.validation = validation;
    }

    @Override
    public void render() {
        if (!bound) {
            element.addEventListener("change", listener);
            element.listenFocus(focusListener);
            element.listenBlur(blurListener);
            bound = true;
        }
        Validation<T> v = validation.get();
        if (currentValidation != v) {
            if (currentValidation != null) {
                currentValidation.bindings.remove(this);
            }
            v.bindings.add(this);
        }
        if (!hasFocus(element) && v.validFormat) {
            setValue(element, v.converter.get().makeString(v.supplier.get()));
        }
    }

    @Override
    public void destroy() {
        if (currentValidation != null) {
            currentValidation.bindings.remove(this);
        }
        if (bound) {
            element.removeEventListener("change", listener);
            element.neglectFocus(focusListener);
            element.neglectBlur(blurListener);
            bound = false;
        }
    }

    void check() {
        Validation<T> v = validation.get();
        String text = getValue(element);
        T value;
        try {
            value = v.converter.get().parse(text);
        } catch (ConversionException e) {
            v.validFormat = false;
            v.valid = true;
            return;
        }

        v.validFormat = true;
        v.valid = true;
        for (ValidationRule<T> rule : v.rules) {
            rule.valid = rule.predicate.test(value);
            if (!rule.valid) {
                v.valid = false;
            }
        }
        v.consumer.accept(value);
    }

    private EventListener<Event> listener = event -> {
        check();
        Templates.update();
    };

    private EventListener<Event> focusListener = event -> {
        Templates.update();
    };

    private EventListener<Event> blurListener = event -> {
        Templates.update();
    };

    @JSBody(params = "elem", script = "return elem.value;")
    private static native String getValue(HTMLElement elem);

    @JSBody(params = { "elem", "value" }, script = "elem.value = value;")
    private static native void setValue(HTMLElement elem, String value);

    @JSBody(params = "elem", script = "return elem === document.activeElement;")
    private static native boolean hasFocus(HTMLElement elem);
}
