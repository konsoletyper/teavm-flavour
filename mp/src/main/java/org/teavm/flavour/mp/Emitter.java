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
package org.teavm.flavour.mp;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class Emitter<S> {
    static boolean emitting;
    List<Runnable> steps = new ArrayList<>();
    private Value<S> returnValue;
    private boolean locked;
    private List<Choice<?>> choices = new ArrayList<>();

    Emitter(Value<S> returnValue) {
        this.returnValue = returnValue;
    }

    public <T> Value<T> emit(Computation<T> computation) {
        acquire();
        Value<T> result = new Value<>();
        emit(() -> result.set(computation.compute()));
        return result;
    }

    public void emit(Runnable runnable) {
        acquire();
        steps.add(runnable);
    }

    public <T> Choice<T> choose(Value<Integer> value) {
        Choice<T> choice = new Choice<>(this, value);
        choices.add(choice);
        emit(() -> choice.eval());
        return choice;
    }

    public void returnValue(Computation<S> computation) {
        emit(() -> returnValue.set(computation.compute()));
    }

    void acquire() {
        if (locked) {
            throw new IllegalStateException("Can't change this emitter as it's already locked");
        }
    }

    void lock() {
        if (locked) {
            return;
        }
        locked = true;
        for (Choice<?> choice : choices) {
            choice.lock();
        }
    }

    S eval() {
        for (Runnable step : steps) {
            step.run();
        }
        return returnValue.get();
    }

    S run() {
        emitting = true;
        try {
            return eval();
        } finally {
            emitting = false;
        }
    }
}
