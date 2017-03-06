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

import java.util.List;

public class DomComponentTemplate extends AbstractComponent {
    private DomComponentHandler handler;
    private List<Renderable> renderables;

    public DomComponentTemplate(DomComponentHandler handler) {
        super(Slot.create());
        this.handler = handler;
    }

    @Override
    public void render() {
        handler.update();
        if (renderables == null) {
            DomBuilder builder = new DomBuilder(getSlot());
            handler.buildDom(builder);
            renderables = builder.getRenderables();
        }
        for (Renderable renderable : renderables) {
            renderable.render();
        }
    }

    @Override
    public void destroy() {
        if (renderables != null) {
            for (Renderable renderable : renderables) {
                renderable.destroy();
            }
            renderables = null;
        }
        super.destroy();
    }
}
