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
package org.teavm.flavour.regex.automata;

/**
 *
 * @author Alexey Andreev
 */
public class DfaTransition {
    private DfaState source;
    private int targetIndex = -1;

    public DfaTransition(DfaState source) {
        this.source = source;
    }

    public Dfa getAutomaton() {
        return source.getAutomaton();
    }

    public DfaState getTarget() {
        return targetIndex >= 0 ? getAutomaton().getStates().get(targetIndex) : null;
    }

    public void setTarget(DfaState target) {
        if (target != null && target.getAutomaton() != getAutomaton()) {
            throw new IllegalArgumentException("Can't set target state from another automaton");
        }
        targetIndex = target.getIndex();
    }

    public DfaState getSource() {
        return source;
    }
}
