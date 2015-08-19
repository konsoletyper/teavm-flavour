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

import org.teavm.flavour.regex.ast.CapturingGroupNode;
import org.teavm.flavour.regex.ast.CharSetNode;
import org.teavm.flavour.regex.ast.ConcatNode;
import org.teavm.flavour.regex.ast.EmptyNode;
import org.teavm.flavour.regex.ast.NodeVisitor;
import org.teavm.flavour.regex.ast.OneOfNode;
import org.teavm.flavour.regex.ast.RepeatNode;
import org.teavm.flavour.regex.ast.TextNode;
import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class NfaBuilder implements NodeVisitor {
    private Nfa automaton;
    private NfaState start;
    private NfaState end;

    public NfaBuilder(Nfa automaton) {
        this.automaton = automaton;
        start = automaton.getStartState();
    }

    public NfaState getEnd() {
        return end;
    }

    public void setEnd(NfaState end) {
        this.end = end;
    }

    @Override
    public void visit(TextNode node) {
        String text = node.getValue();
        NfaState state = start;
        int last = text.length() - 1;
        for (int i = 0; i < last; ++i) {
            NfaState next = automaton.createState();
            state.createTransition(next, new SetOfChars(text.charAt(i)));
            state = next;
        }
        if (last >= 0) {
            state.createTransition(end, new SetOfChars(text.charAt(last)));
        }
    }

    @Override
    public void visit(ConcatNode node) {
        NfaState oldEnd = end;

        for (int i = 0; i < node.getSequence().size() - 1; ++i) {
            NfaState intermediate = automaton.createState();
            end = intermediate;
            node.getSequence().get(i).acceptVisitor(this);
            start = intermediate;
        }

        end = oldEnd;
        node.getSequence().get(node.getSequence().size() - 1).acceptVisitor(this);
    }

    @Override
    public void visit(CharSetNode node) {
        start.createTransition(end, node.getCharSet());
    }

    @Override
    public void visit(CapturingGroupNode node) {
        NfaState intermediateStart = automaton.createState();
        NfaTransition enter = start.createTransition(intermediateStart);
        enter.setStartGroup(node.getName());
        enter.setStartGroupIndex(node.getIndex());
        start = intermediateStart;

        NfaState intermediateEnd = automaton.createState();
        NfaTransition exit = start.createTransition(intermediateEnd);
        exit.setEndGroup(node.getName());
        exit.setEndGroupIndex(node.getIndex());
        end = intermediateEnd;

        node.getCaptured().acceptVisitor(this);
    }

    @Override
    public void visit(EmptyNode node) {
        start.createTransition(end);
    }

    @Override
    public void visit(RepeatNode node) {
        NfaState oldEnd = end;
        NfaState intermediate = start;
        for (int i = 0; i < node.getMinimum(); ++i) {
            start = intermediate;
            intermediate = automaton.createState();
            end = intermediate;
            node.getRepeated().acceptVisitor(this);
        }

        if (node.getMaximum() == 0) {
            NfaState tmp = automaton.createState();
            start = intermediate;
            node.getRepeated().acceptVisitor(this);
            tmp.createTransition(start).setReluctant(node.isReluctant());
            start.createTransition(oldEnd);
        } else {
            for (int i = node.getMinimum(); i < node.getMaximum(); ++i) {
                start = intermediate;
                start.createTransition(oldEnd);
                intermediate = automaton.createState();
                end = intermediate;
                node.getRepeated().acceptVisitor(this);
                if (node.isReluctant()) {
                    NfaState tmp = automaton.createState();
                    intermediate.createTransition(tmp).setReluctant(true);
                    intermediate = tmp;
                }
            }
            intermediate.createTransition(oldEnd);
        }

        end = oldEnd;
    }

    @Override
    public void visit(OneOfNode node) {
        NfaState oldStart = start;
        NfaState oldEnd = end;
        node.getFirst().acceptVisitor(this);

        start = oldStart;
        end = oldEnd;
        node.getSecond().acceptVisitor(this);
    }
}
