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
package org.teavm.flavour.regex.bytecode;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.teavm.flavour.regex.Matcher;
import org.teavm.flavour.regex.Pattern;
import org.teavm.flavour.regex.automata.Dfa;
import org.teavm.flavour.regex.automata.DfaState;
import org.teavm.flavour.regex.automata.DfaTransition;
import org.teavm.flavour.regex.core.MapOfChars;
import org.teavm.flavour.regex.core.MapOfCharsIterator;

/**
 *
 * @author Alexey Andreev
 */
public class MatcherClassBuilder {
    private Label[] stateLabels;
    private Label loopLabel;
    private Label continueLabel;
    private Label errorLabel;
    private Label saveLabel;
    private String className;
    private boolean debugMode;

    public MatcherClassBuilder() {
        this(false);
    }

    public MatcherClassBuilder(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public Pattern compile(ClassLoader originalClassLoader, Dfa dfa) {
        String className = Matcher.class.getName() + "$$Impl";
        byte[] buffer = build(className, dfa);
        ClassLoader classLoader = new ClassLoader(originalClassLoader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(className, buffer, 0, buffer.length);
                }
                return super.findClass(name);
            }
        };
        try {
            Class<?> cls = Class.forName(className, true, classLoader);
            return new CompiledPattern(cls.asSubclass(Matcher.class).getConstructor());
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }

    public byte[] build(String className, Dfa dfa) {
        dfa = reorder(dfa, getOrdering(dfa));

        className = className.replace('.', '/');
        this.className = className;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassVisitor cv = writer;
        cv.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object",
                new String[] { Type.getInternalName(Matcher.class) });

        cv.visitField(Opcodes.ACC_PRIVATE, "state", "I", null, null).visitEnd();
        cv.visitField(Opcodes.ACC_PRIVATE, "domain", "I", null, null).visitEnd();
        cv.visitField(Opcodes.ACC_PRIVATE, "index", "I", null, null).visitEnd();

        buildConstructor(cv, className);
        buildValidMethod(cv, className);
        buildDomainMethod(cv, className);
        buildIndexMethod(cv, className);
        buildRestartMethod(cv, className);
        buildForkMethod(cv, className);
        buildEndMethod(cv, className, dfa);
        buildWorkerMethod(cv, className, dfa);

        cv.visitEnd();

        byte[] result = writer.toByteArray();
        return result;
    }

    private void buildConstructor(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "state", "I");

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "domain", "I");

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "index", "I");

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void buildValidMethod(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "isValid", "()Z", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "state", "I");
        Label trueLabel = new Label();
        mv.visitJumpInsn(Opcodes.IFGE, trueLabel);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(trueLabel);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void buildDomainMethod(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "getDomain", "()I", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "domain", "I");
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void buildIndexMethod(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "index", "()I", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "index", "I");
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void buildRestartMethod(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "restart", "()"
                + Type.getDescriptor(Matcher.class), null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "domain", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "state", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void buildForkMethod(ClassVisitor cv, String className) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "fork", "()"
                + Type.getDescriptor(Matcher.class), null, null);
        mv.visitCode();

        mv.visitTypeInsn(Opcodes.NEW, className);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, className, "<init>", "()V", false);

        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "domain", "I");
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "domain", "I");

        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "state", "I");
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "state", "I");

        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "index", "I");
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "index", "I");

        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    private void buildEndMethod(ClassVisitor cv, String className, Dfa dfa) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "end", "()"
                + Type.getDescriptor(Matcher.class), null, null);

        stateLabels = new Label[dfa.getStates().size()];
        Arrays.setAll(stateLabels, i -> new Label());
        int[] keys = new int[dfa.getStates().size()];
        Arrays.setAll(keys, IntUnaryOperator.identity());

        saveLabel = new Label();
        errorLabel = new Label();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "state", "I");
        mv.visitLookupSwitchInsn(errorLabel, keys, stateLabels);

        for (int i = 0; i < dfa.getStates().size(); ++i) {
            mv.visitLabel(stateLabels[i]);
            DfaTransition transition = dfa.getStates().get(i).getTransition(-1);
            if (transition == null) {
                mv.visitJumpInsn(Opcodes.GOTO, errorLabel);
            } else {
                DfaState target = transition.getTarget();
                mv.visitIntInsn(Opcodes.SIPUSH, transition.getTarget().getIndex());
                mv.visitVarInsn(Opcodes.ISTORE, 1);
                mv.visitIntInsn(Opcodes.SIPUSH, !target.isTerminal() ? -1 : target.getDomains()[0]);
                mv.visitVarInsn(Opcodes.ISTORE, 2);
                debug(mv, "DFA: " + i + " .-> " + target.getIndex() + " " + Arrays.toString(target.getDomains()));
                mv.visitJumpInsn(Opcodes.GOTO, saveLabel);
            }
        }

        mv.visitLabel(errorLabel);
        debug(mv, "DFA: error");
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitVarInsn(Opcodes.ISTORE, 1);
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitVarInsn(Opcodes.ISTORE, 2);
        mv.visitLabel(saveLabel);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "state", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "domain", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private void buildWorkerMethod(ClassVisitor cv, String className, Dfa dfa) {
        MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "feed", "(Ljava/lang/String;IIZ)"
                + Type.getDescriptor(Matcher.class), null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, className, "state", "I");
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        errorLabel = new Label();
        saveLabel = new Label();
        loopLabel = new Label();
        continueLabel = new Label();

        mv.visitLabel(loopLabel);
        generateLengthGuard(mv);

        stateLabels = new Label[dfa.getStates().size()];
        Arrays.setAll(stateLabels, i -> new Label());
        int[] keys = new int[dfa.getStates().size()];
        Arrays.setAll(keys, IntUnaryOperator.identity());

        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitLookupSwitchInsn(errorLabel, keys, stateLabels);

        mv.visitLabel(continueLabel);
        mv.visitIincInsn(2, 1);
        mv.visitJumpInsn(Opcodes.GOTO, loopLabel);

        mv.visitLabel(errorLabel);
        debug(mv, "DFA: error");
        mv.visitInsn(Opcodes.ICONST_M1);
        mv.visitVarInsn(Opcodes.ISTORE, 5);

        mv.visitLabel(saveLabel);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 5);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "state", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitFieldInsn(Opcodes.PUTFIELD, className, "index", "I");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);

        for (int i = 0; i < dfa.getStates().size(); ++i) {
            mv.visitLabel(stateLabels[i]);
            DfaState state = dfa.getStates().get(i);
            generateTransitions(state, mv);
        }

        mv.visitMaxs(3, 6);
        mv.visitEnd();
    }

    private void generateLengthGuard(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ILOAD, 3);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitInsn(Opcodes.ISUB);
        mv.visitJumpInsn(Opcodes.IFLE, saveLabel);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
        mv.visitVarInsn(Opcodes.ISTORE, 6);

        if (debugMode) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", Type.getDescriptor(PrintStream.class));
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("DFA <- ");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ILOAD, 6);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "print", "(C)V", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
        }
    }

    private void generateTransitions(DfaState source, MethodVisitor mv) {
        if (source.isTerminal()) {
            Label noReluctant = new Label();
            mv.visitVarInsn(Opcodes.ILOAD, 4);
            mv.visitJumpInsn(Opcodes.IFNE, saveLabel);
            mv.visitLabel(noReluctant);
        }

        MapOfChars<DfaState> targets = getTransitions(source);
        MapOfChars<DfaState> rangeTargets = targets.clone();

        List<Integer> keys = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        List<DfaState> singleTargets = new ArrayList<>();
        for (MapOfCharsIterator<DfaState> iter = targets.iterate(); iter.hasValue(); iter.next()) {
            if (iter.getValue() == null) {
                continue;
            }
            if (iter.getStart() + 1 == iter.getEnd()) {
                rangeTargets.fill(iter.getStart(), iter.getEnd(), null);
                keys.add(iter.getStart());
                labels.add(new Label());
                singleTargets.add(iter.getValue());
            }
        }

        Label nonSingleChars = new Label();
        if (!keys.isEmpty()) {
            mv.visitVarInsn(Opcodes.ILOAD, 6);
            mv.visitLookupSwitchInsn(nonSingleChars, keys.stream().mapToInt(Integer::intValue).toArray(),
                    labels.toArray(new Label[0]));
            for (int i = 0; i < labels.size(); ++i) {
                mv.visitLabel(labels.get(i));
                generateTransition(mv, source, singleTargets.get(i));
            }
        }

        mv.visitLabel(nonSingleChars);
        generateBinaryMatcher(mv, source, rangeTargets);
    }

    private void generateBinaryMatcher(MethodVisitor mv, DfaState source, MapOfChars<DfaState> targets) {
        int[] toggleIndexes = targets.getToggleIndexes();
        if (toggleIndexes.length == 0) {
            debug(mv, "DFA: " + source.getIndex() + " -> error");
            mv.visitJumpInsn(Opcodes.GOTO, errorLabel);
        } else {
            generateBinaryMatcher(mv, source, targets, toggleIndexes, 0, toggleIndexes.length - 1);
        }
    }

    private void generateBinaryMatcher(MethodVisitor mv, DfaState source, MapOfChars<DfaState> targets,
            int[] indexes, int l, int u) {
        int mid = (l + u) / 2;
        mv.visitVarInsn(Opcodes.ILOAD, 6);
        mv.visitLdcInsn(indexes[mid]);
        mv.visitInsn(Opcodes.ISUB);
        Label less = new Label();
        mv.visitJumpInsn(Opcodes.IFLT, less);

        if (mid + 1 > u) {
            DfaState target = targets.get(indexes[mid]);
            if (target == null) {
                debug(mv, "DFA: " + source.getIndex() + " -> error");
                mv.visitJumpInsn(Opcodes.GOTO, errorLabel);
            } else {
                generateTransition(mv, source, target);
            }
        } else {
            generateBinaryMatcher(mv, source, targets, indexes, mid + 1, u);
        }

        mv.visitLabel(less);

        if (mid - 1 < l) {
            DfaState target = targets.get(indexes[mid] - 1);
            if (target == null) {
                debug(mv, "DFA: " + source.getIndex() + " -> error");
                mv.visitJumpInsn(Opcodes.GOTO, errorLabel);
            } else {
                generateTransition(mv, source, target);
            }
        } else {
            generateBinaryMatcher(mv, source, targets, indexes, l, mid - 1);
        }
    }

    private void generateTransition(MethodVisitor mv, DfaState source, DfaState target) {
        if (source.isTerminal() && source != target) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.ICONST_M1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, className, "domain", "I");
        }
        mv.visitIntInsn(Opcodes.SIPUSH, target.getIndex());
        mv.visitVarInsn(Opcodes.ISTORE, 5);
        if (target.isTerminal() && source != target) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitIntInsn(Opcodes.SIPUSH, target.getDomains()[0]);
            mv.visitFieldInsn(Opcodes.PUTFIELD, className, "domain", "I");
        }
        debug(mv, "DFA: " + source.getIndex() + " -> " + target.getIndex() + " "
                + Arrays.toString(target.getDomains()));
        if (source.getIndex() + 1 == target.getIndex()) {
            mv.visitIincInsn(2, 1);
            generateLengthGuard(mv);
            mv.visitJumpInsn(Opcodes.GOTO, stateLabels[target.getIndex()]);
        } else {
            mv.visitJumpInsn(Opcodes.GOTO, continueLabel);
        }
    }

    private MapOfChars<DfaState> getTransitions(DfaState state) {
        MapOfChars<DfaState> transitions = new MapOfChars<>();
        for (MapOfCharsIterator<DfaTransition> iter = state.getTransitions(); iter.hasValue(); iter.next()) {
            transitions.fill(iter.getStart(), iter.getEnd(),
                    iter.getValue() != null ? iter.getValue().getTarget() : null);
        }
        return transitions;
    }

    private int[] getOrdering(Dfa dfa) {
        int[] result = new int[dfa.getStates().size()];
        Arrays.fill(result, -1);
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(0);

        int index = 0;
        while (!stack.isEmpty()) {
            int state = stack.pop();
            if (result[state] >= 0) {
                continue;
            }
            result[state] = index++;
            for (MapOfCharsIterator<DfaTransition> iter = dfa.getStates().get(state).getTransitions();
                    iter.hasValue(); iter.next()) {
                if (iter.getValue() == null) {
                    continue;
                }
                int next = iter.getValue().getTarget().getIndex();
                if (result[next] < 0) {
                    stack.push(next);
                }
            }
        }

        return result;
    }

    private Dfa reorder(Dfa dfa, int[] ordering) {
        Dfa result = new Dfa();
        while (result.getStates().size() < dfa.getStates().size()) {
            result.createState();
        }
        for (int i = 0; i < dfa.getStates().size(); ++i) {
            DfaState source = result.getStates().get(ordering[i]);
            source.setDomains(dfa.getStates().get(i).getDomains());
            for (MapOfCharsIterator<DfaTransition> iter = dfa.getStates().get(i).getTransitions(); iter.hasValue();
                    iter.next()) {
                if (iter.getValue() == null) {
                    continue;
                }
                DfaState target = result.getStates().get(ordering[iter.getValue().getTarget().getIndex()]);
                source.createTransition(iter.getStart(), iter.getEnd()).setTarget(target);
            }
        }
        return result;
    }

    private void debug(MethodVisitor mv, String string) {
        if (!debugMode) {
            return;
        }
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", Type.getDescriptor(PrintStream.class));
        mv.visitLdcInsn(string);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }
}
