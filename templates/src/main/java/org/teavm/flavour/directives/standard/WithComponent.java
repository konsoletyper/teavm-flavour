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
package org.teavm.flavour.directives.standard;

import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.Variable;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "with")
public class WithComponent<T> extends AbstractComponent {
    private Fragment content;
    private Variable<T> variable;
    private Computation<T> value;
    private Component contentRenderer;

    public WithComponent(Slot slot) {
        super(slot);
    }

    @BindContent
    public void setContent(Fragment content) {
        this.content = content;
    }

    @BindAttribute(name = "var")
    public void setVariable(Variable<T> variable) {
        this.variable = variable;
    }

    @BindAttribute(name = "value")
    public void setValue(Computation<T> value) {
        this.value = value;
    }

    @Override
    public void render() {
        if (contentRenderer == null) {
            contentRenderer = content.create();
            getSlot().append(contentRenderer.getSlot());
        }
        variable.set(value.perform());
        contentRenderer.render();
    }

    @Override
    public void destroy() {
        if (contentRenderer != null) {
            contentRenderer.destroy();
            contentRenderer = null;
        }
        super.destroy();
    }
}
