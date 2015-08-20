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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class NfaState {
    private Nfa automaton;
    private int index;
    private int domain = -1;
    private List<NfaTransition> transitions = new ArrayList<>();
    private List<NfaTransition> readonlyTransitions = Collections.unmodifiableList(transitions);

    NfaState(Nfa automaton, int index) {
        this.automaton = automaton;
        this.index = index;
    }

    public Nfa getAutomaton() {
        return automaton;
    }

    public int getIndex() {
        return index;
    }

    public int getDomain() {
        return domain;
    }

    public void setDomain(int domain) {
        this.domain = domain;
    }

    public List<NfaTransition> getTransitions() {
        return readonlyTransitions;
    }

    public NfaTransition createTransition(NfaState target, SetOfChars chars) {
        NfaTransition transition = new NfaTransition(this);
        transition.setTarget(target);
        transition.setCharSet(chars);
        transitions.add(transition);
        return transition;
    }

    public NfaTransition createTransition(NfaState target) {
        return createTransition(target, null);
    }
}
