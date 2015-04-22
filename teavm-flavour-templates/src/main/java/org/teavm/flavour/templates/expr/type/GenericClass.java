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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class GenericClass extends GenericType {
    String name;
    List<GenericType> arguments = new ArrayList<>();
    private List<GenericType> safeArguments = Collections.unmodifiableList(arguments);

    @Override
    public boolean isClass() {
        return true;
    }

    @Override
    public boolean isVariable() {
        return false;
    }

    public String getName() {
        return name;
    }

    public List<GenericType> getArguments() {
        return safeArguments;
    }

    @Override
    public GenericClass asClass() {
        return this;
    }

    @Override
    public GenericVariable asVariable() {
        throw new IllegalStateException("This type is not a variable");
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public Primitive asPrimitive() {
        throw new IllegalStateException("This type is not a primitive");
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
