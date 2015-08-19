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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import org.teavm.flavour.regex.core.MapOfCharsIterator;

/**
 *
 * @author Alexey Andreev
 */
public class Dfa {
    private List<DfaState> states = new ArrayList<>();
    private List<DfaState> readonlyStates = Collections.unmodifiableList(states);

    public List<DfaState> getStates() {
        return readonlyStates;
    }

    public DfaState getStartState() {
        return states.get(0);
    }

    public DfaState createState() {
        return new DfaState(this, states.size());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < states.size(); ++i) {
            sb.append(i).append("\n");
            for (MapOfCharsIterator<DfaTransition> iter = states.get(i).getTransitions(); iter.hasValue();
                    iter.next()) {
                DfaTransition transition = iter.getValue();
                if (transition == null) {
                    continue;
                }

                sb.append("  -> ").append(transition.getTarget().getIndex()).append(" : ");
                if (iter.getStart() + 1 == iter.getEnd()) {
                    append(sb, iter.getStart());
                } else {
                    sb.append('[');
                    append(sb, iter.getStart());
                    sb.append('-');
                    append(sb, iter.getEnd() - 1);
                    sb.append(']');
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

    private static void append(StringBuilder sb, int c) {
        if (c >= 32) {
            switch ((char) c) {
                case '-':
                    sb.append("\\-").append(c);
                    break;
                default:
                    sb.append((char) c);
                    break;
            }
        } else if (c >= 0) {
            sb.append("\\u00").append(Character.forDigit(c / 16, 16)).append(Character.forDigit(c % 16, 16));
        } else {
            sb.append("EOF");
        }
    }

    public static Dfa fromNfa(Nfa nfa) {
        Dfa dfa = new Dfa();
        Set<NfaStateSet> visited = new HashSet<>();
        Map<NfaStateSet, DfaState> stateMap = new HashMap<>();
        Queue<NfaStateSet> queue = new ArrayDeque<>();
        queue.add(new NfaStateSet(0));

        while (!queue.isEmpty()) {
            NfaStateSet stateSet = queue.remove();
            if (!visited.add(stateSet)) {
                continue;
            }

            Queue<TransitionDescriptor> transitions = new PriorityQueue<>();
            for (int nfaIndex : stateSet.indexes) {
                NfaState nfaState = nfa.getStates().get(nfaIndex);
                for (NfaTransition transition : nfaState.getTransitions()) {
                    if (transition.getCharSet() == null) {
                        continue;
                    }
                    transitions.add(new TransitionDescriptor(transition));
                }
            }

            while (!transitions.isEmpty()) {
                int index = transitions.peek().getFirstIndex();
                Set<NfaState> targetStates = new HashSet<>();
                while (!transitions.isEmpty() && transitions.peek().getFirstIndex() == index) {
                    TransitionDescriptor td = transitions.remove();
                    if (td.getTransition().getCharSet().has(index)) {
                        targetStates.add(td.getTransition().getTarget());
                    }
                    td = td.next();
                    if (td != null) {
                        transitions.add(td);
                    }
                }
                NfaStateSet targetSet = new NfaStateSet(targetStates.toArray(new NfaState[0]));
                DfaState state = stateMap.computeIfAbsent(targetSet, s -> dfa.createState());
            }
        }

        return dfa;
    }

    private Set<NfaState> emptyClosure(NfaState state) {
        Set<NfaState> result = new HashSet<>();
        emptyClosure(state, result);
        return result;
    }

    private void emptyClosure(NfaState state, Set<NfaState> set) {
        if (!set.add(state)) {
            return;
        }
        for (NfaTransition transition : state.getTransitions()) {
            if (transition.getCharSet() == null) {
                emptyClosure(transition.getTarget(), set);
            }
        }
    }

    static class TransitionDescriptor implements Comparable<TransitionDescriptor> {
        NfaTransition transition;
        int index;
        int[] toggleIndexes;

        public TransitionDescriptor(NfaTransition transition) {
            this(transition, transition.getCharSet().getToggleIndexes(), 0);
        }

        private TransitionDescriptor(NfaTransition transition, int[] toggleIndexes, int index) {
            this.transition = transition;
            this.index = index;
            this.toggleIndexes = toggleIndexes;
        }

        public NfaTransition getTransition() {
            return transition;
        }

        public int getFirstIndex() {
            return toggleIndexes[index];
        }

        public TransitionDescriptor next() {
            return index + 1 < toggleIndexes.length
                    ? new TransitionDescriptor(transition, toggleIndexes, index + 1)
                    : null;
        }

        @Override
        public int compareTo(TransitionDescriptor o) {
            return Integer.compare(getFirstIndex(), o.getFirstIndex());
        }
    }

    static class NfaStateSet {
        public final int[] indexes;
        private int hash;

        public NfaStateSet(NfaState... states) {
            this(mapStates(states));
        }

        private static int[] mapStates(NfaState... states) {
            int[] indexes = new int[states.length];
            for (int i = 0; i < indexes.length; ++i) {
                indexes[i] = states[i].getIndex();
            }
            return indexes;
        }

        public NfaStateSet(int... indexes) {
            Arrays.sort(indexes);
            int j = 1;
            for (int i = 1; i < indexes.length; ++i) {
                if (indexes[i] != indexes[i - 1]) {
                    indexes[j++] = indexes[i];
                }
            }
            if (j < indexes.length) {
                indexes = Arrays.copyOf(indexes, j);
            }
            this.indexes = indexes;
        }

        @Override
        public int hashCode() {
            if (hash == 0) {
                hash = Arrays.hashCode(indexes);
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return Arrays.equals(((NfaStateSet) obj).indexes, indexes);
        }
    }
}
