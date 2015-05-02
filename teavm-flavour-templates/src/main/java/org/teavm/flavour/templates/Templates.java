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
package org.teavm.flavour.templates;

import org.teavm.dom.browser.Window;
import org.teavm.dom.html.HTMLElement;
import org.teavm.jso.JS;

/**
 *
 * @author Alexey Andreev
 */
public final class Templates {
    private static Component root;

    private Templates() {
    }

    public static Component bind(Object model, String id) {
        return bind(model, ((Window)JS.getGlobal()).getDocument().getElementById(id));
    }

    public static Component bind(Object model, HTMLElement element) {
        Fragment fragment = create(model);
        Component component = fragment.create();
        Slot root = Slot.root(element);
        root.append(component.getSlot());
        renderRoot(component);
        return component;
    }

    public static Fragment create(Object model) {
        return createImpl(model, model.getClass().getName());
    }

    public static void renderRoot(Component component) {
        if (root != null) {
            throw new IllegalStateException("This method can't be called recursively");
        }
        root = component;
        component.render();
        root = null;
    }

    private static native Fragment createImpl(Object model, String modelType);

    public static Component root() {
        return root;
    }

    public static void update() {
        if (root == null) {
            throw new IllegalStateException("This method can be only called during rendering process");
        }
        root.render();
    }

    public static Action wrap(final Action action) {
        if (root == null) {
            throw new IllegalStateException("This method can be only called during rendering process");
        }
        final Component savedRoot = root;
        return new Action() {
            @Override
            public void perform() {
                root = savedRoot;
                action.perform();
                update();
                root = null;
            }
        };
    }
}
