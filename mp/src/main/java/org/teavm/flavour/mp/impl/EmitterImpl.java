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
package org.teavm.flavour.mp.impl;

import org.teavm.flavour.mp.Action;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Computation;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.Value;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.Program;
import org.teavm.model.instructions.ExitInstruction;

/**
 *
 * @author Alexey Andreev
 */
public class EmitterImpl<T> implements Emitter<T> {
    private ClassReaderSource classSource;
    private CompoundMethodGenerator generator;

    public EmitterImpl(ClassReaderSource classSource, CompoundMethodGenerator generator) {
        this.classSource = classSource;
        this.generator = generator;
    }

    @Override
    public <S> Value<S> emit(Computation<S> computation) {
        Fragment fragment = (Fragment) computation;
        MethodReader method = classSource.resolve(fragment.method);
        generator.addProgram(method.getProgram(), fragment.capturedValues);
        return new ValueImpl<>(generator.getResultVar());
    }

    @Override
    public void emit(Action action) {
        Fragment fragment = (Fragment) action;
        MethodReader method = classSource.resolve(fragment.method);
        generator.addProgram(method.getProgram(), fragment.capturedValues);
    }

    @Override
    public <S> Choice<S> choose(Value<Integer> value) {
        return null;
    }

    @Override
    public void returnValue(Computation<T> computation) {
        Fragment fragment = (Fragment) computation;
        MethodReader method = classSource.resolve(fragment.method);
        generator.addProgram(method.getProgram(), fragment.capturedValues);

        Program targetProgram = generator.program;
        targetProgram.basicBlockAt(targetProgram.basicBlockCount() - 1);
        ExitInstruction insn = new ExitInstruction();
        insn.setValueToReturn(generator.getResultVar());
        generator.add(insn);
    }
}
