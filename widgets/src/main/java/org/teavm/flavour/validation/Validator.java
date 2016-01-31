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

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.Slot;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "validator")
public class Validator extends AbstractComponent {
    private ValidatorState state;
    boolean valid;
    private List<ValidationEntry<?>> entries = new ArrayList<>();
    private Fragment content;
    private Component component;

    public Validator(Slot slot) {
        super(slot);
        state = new ValidatorState(this);
    }

    @BindAttribute(name = "as")
    @OptionalBinding
    public ValidatorState getState() {
        return state;
    }

    public boolean isValid() {
        return valid;
    }

    @BindDirective(name = "validation")
    public void setEntries(List<ValidationEntry<?>> entries) {
        this.entries = entries;
    }

    @BindContent
    public void setContent(Fragment content) {
        this.content = content;
    }

    @Override
    public void render() {
        if (component == null) {
            component = content.create();
            getSlot().append(component.getSlot());
            for (ValidationEntry<?> entry : entries) {
                entry.validation.validator = this;
            }
        }

        updateValid();
        component.render();
    }

    void updateValid() {
        valid = true;
        for (ValidationEntry<?> entry : entries) {
            valid &= entry.validation.isValid();
        }
    }

    void update() {
        for (ValidationEntry<?> entry : entries) {
            entry.validation.check();
        }
        updateValid();
    }

    @Override
    public void destroy() {
        component.destroy();
        component = null;
        super.destroy();
    }
}
