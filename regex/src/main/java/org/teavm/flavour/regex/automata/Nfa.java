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

/**
 *
 * @author Alexey Andreev
 */
public class Nfa {
    private List<NfaState> states = new ArrayList<>();
    private List<NfaState> readonlyStates = Collections.unmodifiableList(states);

    public Nfa() {
        states.add(new NfaState(this, 0));
    }

    public NfaState getStartState() {
        return states.get(0);
    }

    public List<NfaState> getStates() {
        return readonlyStates;
    }

    public NfaState createState() {
        return new NfaState(this, states.size());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < states.size(); ++i) {
            sb.append(i).append("\n");
            for (NfaTransition transition : states.get(i).getTransitions()) {
                sb.append("  -> ").append(transition.getTarget().getIndex()).append(" : ")
                        .append(transition.getCharSet());
                if (transition.isReluctant()) {
                    sb.append(" reluctant");
                }

                if (transition.getStartGroup() != null) {
                    sb.append(" start<" + transition.getStartGroup() + ">");
                }
                if (transition.getStartGroupIndex() >= 0) {
                    sb.append(" start<" + transition.getStartGroupIndex() + ">");
                }

                if (transition.getEndGroup() != null) {
                    sb.append(" end<" + transition.getStartGroup() + ">");
                }
                if (transition.getEndGroupIndex() >= 0) {
                    sb.append(" end<" + transition.getStartGroupIndex() + ">");
                }

                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
