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
package org.teavm.flavour.components.standard;

import java.util.function.Supplier;
import org.teavm.flavour.templates.AbstractComponent;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindElement;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;

@BindElement(name = "if")
public class IfComponent extends AbstractComponent {
    private Supplier<Boolean> condition;
    private Fragment body;
    private Component childComponent;
    private boolean showing;

    public IfComponent(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "condition")
    public void setCondition(Supplier<Boolean> condition) {
        this.condition = condition;
    }

    @BindContent
    public void setBody(Fragment body) {
        this.body = body;
    }

    @Override
    public void render() {
        boolean newShowing = condition.get();
        if (showing != newShowing) {
            if (newShowing) {
                if (childComponent == null) {
                    childComponent = body.create();
                }
                getSlot().append(childComponent.getSlot());
            } else {
                childComponent.getSlot().delete();
            }
        }
        showing = newShowing;

        if (showing) {
            childComponent.render();
        }
    }

    @Override
    public void destroy() {
        if (childComponent != null) {
            childComponent.destroy();
        }
        super.destroy();
    }
}
