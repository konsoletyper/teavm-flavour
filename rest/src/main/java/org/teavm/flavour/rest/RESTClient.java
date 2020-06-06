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

import org.teavm.flavour.rest.impl.FactoryEmitter;
import org.teavm.metaprogramming.CompileTime;
import org.teavm.metaprogramming.Meta;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;

@CompileTime
public final class RESTClient {
    private RESTClient() {
    }

    public static <T> ResourceFactory<T> factory(Class<T> type) {
        ResourceFactory<T> factory = factoryImpl(type);
        if (factory == null) {
            throw new IllegalArgumentException("Not a proper factory interface: " + type.getName());
        }
        return factory;
    }

    @Meta
    private static native <T> ResourceFactory<T> factoryImpl(Class<T> type);
    private static void factoryImpl(ReflectClass<?> type) {
        Value<? extends ResourceFactory<?>> result = FactoryEmitter.getInstance().emitFactory(type);
        if (result == null) {
            Metaprogramming.unsupportedCase();
        } else {
            Metaprogramming.exit(() -> result.get());
        }
    }
}
