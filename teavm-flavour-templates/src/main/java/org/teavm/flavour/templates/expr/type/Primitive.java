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

/**
 *
 * @author Alexey Andreev
 */
public final class Primitive extends GenericType {
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

    public static Primitive get(PrimitiveKind kind) {
        return builders[kind.ordinal()];
    }

    @Override
    public boolean isClass() {
        return false;
    }

    @Override
    public GenericClass asClass() {
        throw new IllegalStateException("This type is not a class");
    }

    @Override
    public boolean isVariable() {
        return false;
    }

    @Override
    public GenericVariable asVariable() {
        throw new IllegalStateException("This type is not a variable");
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public Primitive asPrimitive() {
        return this;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public GenericArray asArray() {
        throw new IllegalStateException("This type is not an array");
    }
}
