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

/**
 *
 * @author Alexey Andreev
 */
public interface Emitter<T> {
    EmitterContext getContext();

    <S> Value<S> emit(Computation<S> computation);

    void emit(Action action);

    <S> Value<S> lazyFragment(ReflectClass<S> type, LazyComputation<S> computation);

    default <S> Value<S> lazyFragment(Class<S> type, LazyComputation<S> computation) {
        return lazyFragment(getContext().findClass(type), computation);
    }

    <S> Value<S> lazy(Computation<S> computation);

    <S> Choice<S> choose(ReflectClass<S> type);

    <S> Choice<S> choose(Class<S> type);

    void returnValue(Computation<? extends T> computation);

    default <S> Value<S> proxy(Class<S> type, InvocationHandler<S> handler)  {
        return proxy(getContext().findClass(type), handler);
    }

    <S> Value<S> proxy(ReflectClass<S> type, InvocationHandler<S> handler);

    void location(String fileName, int lineNumber);

    void defaultLocation();
}
