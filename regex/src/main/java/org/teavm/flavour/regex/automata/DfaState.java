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

import java.util.Arrays;
import java.util.PrimitiveIterator;
import org.teavm.flavour.regex.core.MapOfChars;
import org.teavm.flavour.regex.core.MapOfCharsIterator;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class DfaState {
    private Dfa automaton;
    private int index;
    private int[] domains;
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
        return domains != null;
    }

    public int[] getDomains() {
        return domains != null ? domains.clone() : new int[0];
    }

    public void setDomains(int[] domains) {
        if (domains.length == 0) {
            this.domains = null;
        } else {
            domains = domains.clone();
            Arrays.sort(domains);
            int j = 1;
            for (int i = 1; i < domains.length; ++i) {
                if (domains[i] != domains[i - 1]) {
                    domains[j++] = domains[i];
                }
            }
            if (j < domains.length) {
                domains = Arrays.copyOf(domains, j);
            }
            this.domains = domains;
        }
    }

    public DfaTransition getTransition(int c) {
        return transitions.get(c);
    }

    public MapOfCharsIterator<DfaTransition> getTransitions() {
        return transitions.iterate();
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
