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
package org.teavm.flavour.templates.expr.type;

import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public final class PrimitiveBuilder extends GenericTypeBuilder {
    private static PrimitiveBuilder[] builders;
    private PrimitiveKind kind;

    static {
        PrimitiveKind[] kinds = PrimitiveKind.values();
        builders = new PrimitiveBuilder[kinds.length];
        for (int i = 0; i < kinds.length; ++i) {
            builders[i] = new PrimitiveBuilder(kinds[i]);
        }
    }

    private PrimitiveBuilder(PrimitiveKind kind) {
        this.kind = kind;
    }

    public PrimitiveKind getKind() {
        return kind;
    }

    public static PrimitiveBuilder get(PrimitiveKind kind) {
        return builders[kind.ordinal()];
    }

    @Override
    GenericType buildCacheMiss(Map<GenericTypeBuilder, GenericType> cache) {
        return null;
    }
}
