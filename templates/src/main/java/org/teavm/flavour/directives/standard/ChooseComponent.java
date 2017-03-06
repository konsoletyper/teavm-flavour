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
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.Slot;

@BindDirective(name = "choose")
public class ChooseComponent extends AbstractComponent {
    private List<ChooseClause> clauses;
    private OtherwiseClause otherwiseClause;
    private Component child;
    private ChooseClause currentClause;
    private boolean dirty;

    public ChooseComponent(Slot slot) {
        super(slot);
    }

    @BindDirective(name = "option")
    public void setClauses(List<ChooseClause> clauses) {
        this.clauses = clauses;
    }

    @BindDirective(name = "otherwise")
    @OptionalBinding
    public void setOtherwiseClause(OtherwiseClause otherwiseClause) {
        this.otherwiseClause = otherwiseClause;
    }

    @Override
    public void render() {
        ChooseClause newClause = null;
        for (ChooseClause clause : clauses) {
            if (clause.predicate.getAsBoolean()) {
                newClause = clause;
                break;
            }
        }

        if (dirty || currentClause != newClause) {
            if (child != null) {
                child.destroy();
                child = null;
            }
            currentClause = newClause;
            if (currentClause != null) {
                child = currentClause.content.create();
            } else if (otherwiseClause != null) {
                child = otherwiseClause.content.create();
            }
            getSlot().append(child.getSlot());
            dirty = false;
        }

        if (child != null) {
            child.render();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (child != null) {
            child.destroy();
            child = null;
        }
    }
}
