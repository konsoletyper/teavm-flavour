/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.flavour.expr.type.inference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class ClassType extends Type {
    private String name;
    private List<Type> arguments;

    public ClassType(String name, List<Type> arguments) {
        this.name = name;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public ClassType(String name, Type... arguments) {
        this.name = name;
        this.arguments = Collections.unmodifiableList(Arrays.asList(arguments));
    }

    public String getName() {
        return name;
    }

    public List<Type> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        if (arguments.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name).append('<');
        sb.append(arguments.get(0));
        for (int i = 1; i < arguments.size(); ++i) {
            sb.append(", ").append(arguments.get(i));
        }
        return sb.append('>').toString();
    }
}
