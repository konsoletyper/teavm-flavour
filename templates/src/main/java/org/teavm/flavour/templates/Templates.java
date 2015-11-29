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

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

/**
 *
 * @author Alexey Andreev
 */
public final class Templates {
    private static boolean updating;
    private static List<RootComponent> rootComponents = new ArrayList<>();

    private Templates() {
    }

    public static Component bind(Object model, String id) {
        return bind(model, Window.current().getDocument().getElementById(id));
    }

    public static Component bind(Object model, HTMLElement element) {
        Fragment fragment = create(model);
        Slot root = Slot.root(element);
        RootComponent component = new RootComponent(root, fragment.create());
        rootComponents.add(component);
        updating = true;
        try {
            component.render();
            return component;
        } finally {
            updating = false;
        }
    }

    //@Proxy(TemplatingProxyGenerator.class)
    public static native Fragment create(Object model);

    public static void update() {
        if (updating) {
            return;
        }
        updating = true;
        try {
            for (RootComponent component : rootComponents) {
                component.render();
            }
        } finally {
            updating = false;
        }
    }

    private static class RootComponent extends AbstractComponent {
        private Component inner;

        public RootComponent(Slot slot, Component inner) {
            super(slot);
            this.inner = inner;
            slot.append(inner.getSlot());
        }

        @Override
        public void render() {
            inner.render();
        }

        @Override
        public void destroy() {
            inner.destroy();
            super.destroy();
            rootComponents.remove(this);
        }
    }
}
