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
package org.teavm.flavour.directives.standard;

import java.util.List;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Slot;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "let")
public class LetComponent extends AbstractComponent {
    private List<LetDefinition<?>> definitions;
    private LetBody body;
    private Component child;

    public LetComponent(Slot slot) {
        super(slot);
    }

    @BindDirective(name = "var")
    public void setDefinitions(List<LetDefinition<?>> definitions) {
        this.definitions = definitions;
    }

    @BindDirective(name = "in")
    public void setBody(LetBody body) {
        this.body = body;
    }

    @Override
    public void render() {
        if (child == null) {
            child = body.content.create();
            getSlot().append(child.getSlot());
        }
        for (LetDefinition<?> definition : definitions) {
            @SuppressWarnings("unchecked")
            LetDefinition<Object> safeDefinition = (LetDefinition<Object>) definition;
            safeDefinition.value = definition.computation.get();
        }
        child.render();
    }

    @Override
    public void destroy() {
        if (child != null) {
            child.destroy();
            child = null;
        }
        super.destroy();
    }
}
