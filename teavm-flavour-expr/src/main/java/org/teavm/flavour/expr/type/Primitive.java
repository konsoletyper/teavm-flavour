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
package org.teavm.flavour.expr.type;

/**
 *
 * @author Alexey Andreev
 */
public final class Primitive extends ValueType {
    public static final Primitive BOOLEAN = Primitive.get(PrimitiveKind.BOOLEAN);
    public static final Primitive CHAR = Primitive.get(PrimitiveKind.CHAR);
    public static final Primitive BYTE = Primitive.get(PrimitiveKind.BYTE);
    public static final Primitive SHORT = Primitive.get(PrimitiveKind.SHORT);
    public static final Primitive INT = Primitive.get(PrimitiveKind.INT);
    public static final Primitive LONG = Primitive.get(PrimitiveKind.LONG);
    public static final Primitive FLOAT = Primitive.get(PrimitiveKind.FLOAT);
    public static final Primitive DOUBLE = Primitive.get(PrimitiveKind.DOUBLE);

    private static Primitive[] builders;
    private PrimitiveKind kind;

    static {
        PrimitiveKind[] kinds = PrimitiveKind.values();
        builders = new Primitive[kinds.length];
        for (int i = 0; i < kinds.length; ++i) {
            builders[i] = new Primitive(kinds[i]);
        }
    }

    private Primitive(PrimitiveKind kind) {
        this.kind = kind;
    }

    public PrimitiveKind getKind() {
        return kind;
    }

    private static Primitive get(PrimitiveKind kind) {
        return builders[kind.ordinal()];
    }
}
