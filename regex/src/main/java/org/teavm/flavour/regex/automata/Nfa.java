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
import org.teavm.flavour.regex.ast.Node;

/**
 *
 * @author Alexey Andreev
 */
public class Nfa {
    List<NfaState> states = new ArrayList<>();
    private List<NfaState> readonlyStates = Collections.unmodifiableList(states);

    public Nfa() {
        states.add(new NfaState(this, 0));
    }

    public Nfa(Node... nodes) {
        this();
        NfaBuilder builder = new NfaBuilder(this);
        NfaState start = builder.getStart();
        for (int i = 0; i < nodes.length; ++i) {
            NfaState end = createState();
            end.setDomain(i);
            builder.setStart(start);
            builder.setEnd(end);
            nodes[i].acceptVisitor(builder);
        }
    }

    public NfaState getStartState() {
        return states.get(0);
    }

    public List<NfaState> getStates() {
        return readonlyStates;
    }

    public NfaState createState() {
        NfaState state = new NfaState(this, states.size());
        states.add(state);
        return state;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < states.size(); ++i) {
            sb.append(i);
            if (states.get(i).getDomain() >= 0) {
                sb.append('*');
            }
            sb.append("\n");
            for (NfaTransition transition : states.get(i).getTransitions()) {
                sb.append("  -> ").append(transition.getTarget().getIndex()).append(" : ")
                        .append(transition.getCharSet());

                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
