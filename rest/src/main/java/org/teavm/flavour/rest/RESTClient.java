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
package org.teavm.flavour.rest;

import org.teavm.flavour.mp.CompileTime;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Reflected;
import org.teavm.flavour.rest.impl.FactoryEmitter;

/**
 *
 * @author Alexey Andreev
 */
@CompileTime
public final class RESTClient {
    private RESTClient() {
    }

    @Reflected
    public static native <T> ResourceFactory<T> factory(Class<T> type);
    private static void factory(Emitter<ResourceFactory<?>> em, ReflectClass<?> type) {
        em.returnValue(FactoryEmitter.getInstance(em.getContext()).emitFactory(em, type));
    }
}
