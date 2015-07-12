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
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "insert")
public class InsertComponent extends AbstractComponent {
    private Computation<Fragment> fragment;
    private Fragment renderedFragment;
    private Component body;

    public InsertComponent(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "fragment")
    public void setFragment(Computation<Fragment> fragment) {
        this.fragment = fragment;
    }

    @Override
    public void render() {
        Fragment newFragment = fragment.perform();
        if (newFragment != renderedFragment) {
            if (body != null) {
                body.destroy();
            }
            renderedFragment = newFragment;
            body = newFragment.create();
            getSlot().append(body.getSlot());
        }
        body.render();
    }
}
