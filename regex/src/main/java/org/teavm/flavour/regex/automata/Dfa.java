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
import java.util.function.Function;
import org.teavm.flavour.regex.Pattern;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.bytecode.MatcherClassBuilder;
import org.teavm.flavour.regex.core.MapOfCharsIterator;
import org.teavm.flavour.regex.parsing.RegexParser;

/**
 *
 * @author Alexey Andreev
 */
public class Dfa {
    private List<DfaState> states = new ArrayList<>();
    private List<DfaState> readonlyStates = Collections.unmodifiableList(states);

    public Dfa() {
        createState();
    }

    public List<DfaState> getStates() {
        return readonlyStates;
    }

    public DfaState getStartState() {
        return states.get(0);
    }

    public DfaState createState() {
        DfaState state = new DfaState(this, states.size());
        states.add(state);
        return state;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < states.size(); ++i) {
            sb.append(i);
            if (states.get(i).isTerminal()) {
                sb.append('*');
            }
            sb.append("\n");
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

    public Pattern compile() {
        return compile(Dfa.class.getClassLoader());
    }

    public Pattern compile(ClassLoader classLoader) {
        return new MatcherClassBuilder().compile(classLoader, this);
    }

    public boolean matches(String text) {
        DfaState state = getStartState();
        for (int i = 0; i < text.length(); ++i) {
            DfaTransition transition = state.getTransition(text.charAt(i));
            if (transition == null) {
                return false;
            }
            state = transition.getTarget();
        }
        DfaTransition transition = state.getTransition(-1);
        return transition != null && transition.getTarget().isTerminal();
    }

    public int[] domains(String text) {
        DfaState state = getStartState();
        for (int i = 0; i < text.length(); ++i) {
            DfaTransition transition = state.getTransition(text.charAt(i));
            if (transition == null) {
                return new int[0];
            }
            state = transition.getTarget();
        }
        DfaTransition transition = state.getTransition(-1);
        return transition != null ? transition.getTarget().getDomains() : new int[0];
    }

    public static Dfa fromNode(Node node) {
        return fromNfa(new Nfa(node));
    }

    public static Dfa fromNodes(Node... nodes) {
        return fromNfa(new Nfa(nodes));
    }

    public static Dfa parse(String... clauses) {
        RegexParser parser = new RegexParser();
        Node[] nodes = Arrays.stream(clauses)
                .map(clause -> Node.concat(parser.parse(clause), Node.eof()))
                .toArray(sz -> new Node[sz]);
        return fromNodes(nodes);
    }

    private static int[] getDomains(NfaStateSet u, Nfa nfa) {
        return Arrays.stream(u.indexes)
                .mapToObj(i -> nfa.getStates().get(i))
                .mapToInt(NfaState::getDomain)
                .filter(dom -> dom >= 0)
                .toArray();
    }

    public static Dfa fromNfa(Nfa nfa) {
        Dfa dfa = new Dfa();

        Map<NfaStateSet, DfaState> stateMap = new HashMap<>();
        Function<NfaStateSet, DfaState> stateFunction = u -> {
            DfaState result = dfa.createState();
            result.setDomains(getDomains(u, nfa));
            return result;
        };

        Set<NfaStateSet> visited = new HashSet<>();
        Queue<NfaStateSet> queue = new ArrayDeque<>();
        NfaStateSet initialStateSet = new NfaStateSet(emptyClosure(nfa.getStartState()).toArray(new NfaState[0]));
        queue.add(initialStateSet);
        stateMap.put(initialStateSet, dfa.getStartState());
        dfa.getStartState().setDomains(getDomains(initialStateSet, nfa));

        while (!queue.isEmpty()) {
            NfaStateSet stateSet = queue.remove();
            if (!visited.add(stateSet)) {
                continue;
            }
            DfaState dfaState = stateMap.get(stateSet);

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

            DfaState lastState = null;
            int lastIndex = -1;
            Map<DfaState, DfaTransition> transitionsByTarget = new HashMap<>();
            while (!transitions.isEmpty()) {
                int index = transitions.peek().getFirstIndex();
                while (!transitions.isEmpty() && transitions.peek().getFirstIndex() == index) {
                    TransitionDescriptor td = transitions.remove();
                    td = td.next();
                    if (td != null) {
                        transitions.add(td);
                    }
                }

                Set<NfaState> targetStates = new HashSet<>();
                for (TransitionDescriptor td : transitions) {
                    if (td.getTransition().getCharSet().has(index)) {
                        targetStates.add(td.getTransition().getTarget());
                    }
                }
                targetStates = emptyClosure(targetStates);

                DfaState state;
                if (targetStates.isEmpty()) {
                    state = null;
                } else {
                    NfaStateSet targetStateSet = new NfaStateSet(targetStates.toArray(new NfaState[0]));
                    if (!visited.contains(targetStateSet)) {
                        queue.add(targetStateSet);
                    }
                    state = stateMap.computeIfAbsent(targetStateSet, stateFunction);
                }

                if (lastState != null) {
                    dfaState.replaceTransitions(lastIndex, index, transitionsByTarget.computeIfAbsent(lastState, s -> {
                        DfaTransition newTransition = dfaState.createTransition();
                        newTransition.setTarget(s);
                        return newTransition;
                    }));
                }

                lastState = state;
                lastIndex = index;
            }
        }

        new MatcherClassBuilder().build(Dfa.class.getName() + "_impl", dfa);
        return dfa;
    }

    private static Set<NfaState> emptyClosure(NfaState state) {
        Set<NfaState> result = new HashSet<>();
        emptyClosure(state, result);
        return result;
    }

    private static Set<NfaState> emptyClosure(Set<NfaState> states) {
        Set<NfaState> result = new HashSet<>();
        for (NfaState state : states) {
            emptyClosure(state, result);
        }
        return result;
    }

    private static void emptyClosure(NfaState state, Set<NfaState> set) {
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
