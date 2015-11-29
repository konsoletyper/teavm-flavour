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
import org.teavm.model.emit.ProgramEmitter;

/**
 *
 * @author Alexey Andreev
 */
public class EmitterImpl<T> implements Emitter<T> {
    private ProgramEmitter pe;

    public EmitterImpl(ProgramEmitter pe) {
        this.pe = pe;
    }

    @Override
    public <S> Value<S> emit(Computation<S> computation) {
        ComputationImpl<S> impl = (ComputationImpl<S>) computation;
        return new ValueImpl<>(impl.generator.apply(pe));
    }

    @Override
    public void emit(Action action) {
        RunnableImpl impl = (RunnableImpl) action;
        impl.generator.accept(pe);
    }

    @Override
    public <S> Choice<S> choose(Value<Integer> value) {
        return null;
    }

    @Override
    public void returnValue(Computation<T> computation) {
        ComputationImpl<T> impl = (ComputationImpl<T>) computation;
        impl.generator.apply(pe).returnValue();
    }
}
