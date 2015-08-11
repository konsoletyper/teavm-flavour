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

import java.util.PrimitiveIterator;
import org.teavm.flavour.regex.core.MapOfChars;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class DfaState {
    private Dfa automaton;
    private int index;
    private boolean terminal;
    private MapOfChars<DfaTransition> transitions = new MapOfChars<>();

    DfaState(Dfa automaton, int index) {
        this.automaton = automaton;
        this.index = index;
    }

    public Dfa getAutomaton() {
        return automaton;
    }

    public int getIndex() {
        return index;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public DfaTransition createTransition(SetOfChars chars) {
        DfaTransition transition = new DfaTransition(this);
        for (PrimitiveIterator.OfInt iter = chars.iterator(); iter.hasNext();) {
            transitions.fill(iter.nextInt(), iter.nextInt(), transition);
        }
        return transition;
    }

    public DfaTransition createTransition(int from, int to) {
        DfaTransition transition = new DfaTransition(this);
        transitions.fill(from, to, transition);
        return transition;
    }

    public DfaTransition createTransition() {
        return new DfaTransition(this);
    }

    public void replaceTransitions(SetOfChars chars, DfaTransition transition) {
        if (transition.getSource() != this) {
            throw new IllegalArgumentException("Can't put transition that originates from another node");
        }
        for (PrimitiveIterator.OfInt iter = chars.iterator(); iter.hasNext();) {
            transitions.fill(iter.nextInt(), iter.nextInt(), transition);
        }
    }

    public void replaceTransitions(int from, int to, DfaTransition transition) {
        if (transition.getSource() != this) {
            throw new IllegalArgumentException("Can't put transition that originates from another node");
        }
        transitions.fill(from, to, transition);
    }
}
