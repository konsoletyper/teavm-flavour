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

import java.util.Set;

/**
 *
 * @author Alexey Andreev
 */
public final class Primitive extends ValueType {
    public static final Primitive BOOLEAN = new Primitive(PrimitiveKind.BOOLEAN);
    public static final Primitive CHAR = new Primitive(PrimitiveKind.CHAR);
    public static final Primitive BYTE = new Primitive(PrimitiveKind.BYTE);
    public static final Primitive SHORT = new Primitive(PrimitiveKind.SHORT);
    public static final Primitive INT = new Primitive(PrimitiveKind.INT);
    public static final Primitive LONG = new Primitive(PrimitiveKind.LONG);
    public static final Primitive FLOAT = new Primitive(PrimitiveKind.FLOAT);
    public static final Primitive DOUBLE = new Primitive(PrimitiveKind.DOUBLE);
    private PrimitiveKind kind;

    private Primitive(PrimitiveKind kind) {
        this.kind = kind;
    }

    public PrimitiveKind getKind() {
        return kind;
    }

    @Override
    public Primitive substitute(Substitutions substitutions) {
        return this;
    }

    @Override
    ValueType substitute(Substitutions substitutions, Set<TypeVar> visited) {
        return this;
    }
}
