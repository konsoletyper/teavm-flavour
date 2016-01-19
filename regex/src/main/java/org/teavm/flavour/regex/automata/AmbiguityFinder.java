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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.teavm.flavour.regex.core.MapOfCharsIterator;

/**
 *
 * @author Alexey Andreev
 */
public final class AmbiguityFinder {
    private AmbiguityFinder() {
    }

    public static List<Ambiguity> findAmbiguities(Dfa dfa) {
        List<Ambiguity> ambiguities = new ArrayList<>();
        for (DfaState state : dfa.getStates()) {
            if (!state.isTerminal()) {
                continue;
            }
            int[] domains = state.getDomains();
            if (domains.length > 1) {
                ambiguities.add(new Ambiguity(buildExampleString(state), domains));
            }
        }
        return ambiguities;
    }

    public static String buildExampleString(DfaState state) {
        class Step {
            DfaState state;
            int index;
            int code;
            Step(DfaState state, int index, int code) {
                this.state = state;
                this.index = index;
                this.code = code;
            }
        }

        Dfa dfa = state.getAutomaton();
        StringBuilder sb = new StringBuilder();
        Deque<Step> worklist = new ArrayDeque<>();
        worklist.push(new Step(dfa.getStartState(), 0, -1));
        Set<DfaState> visited = new HashSet<>();

        while (!worklist.isEmpty()) {
            Step step = worklist.pop();
            if (!visited.add(step.state)) {
                continue;
            }

            sb.setLength(step.index);
            int nextIndex = step.index;
            if (step.code >= 0) {
                sb.append((char) step.code);
                nextIndex++;
            }

            for (MapOfCharsIterator<DfaTransition> iter = step.state.getTransitions(); iter.hasValue(); iter.next()) {
                DfaTransition transition = iter.getValue();
                if (transition == null) {
                    continue;
                }
                if (transition.getTarget() == state) {
                    worklist.clear();
                    break;
                }
                if (!visited.contains(transition.getTarget())) {
                    worklist.push(new Step(transition.getTarget(), nextIndex, iter.getStart()));
                }
            }
        }

        return sb.toString();
    }
}
