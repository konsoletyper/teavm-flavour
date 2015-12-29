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
package org.teavm.flavour.templates.emitting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.flavour.mp.ReflectValue;

/**
 *
 * @author Alexey Andreev
 */
class EmitContext {
    String sourceFileName;
    ReflectValue<Object> model;
    List<Map<String, VariableEmitter>> boundVariableStack = new ArrayList<>();
    Map<String, Deque<VariableEmitter>> variables = new HashMap<>();

    public void pushBoundVars() {
        boundVariableStack.add(new HashMap<String, VariableEmitter>());
    }

    public Map<String, VariableEmitter> popBoundVars() {
        Map<String, VariableEmitter> vars = boundVariableStack.get(boundVariableStack.size() - 1);
        boundVariableStack.remove(boundVariableStack.size() - 1);
        return vars;
    }

    public VariableEmitter getVariable(String name) {
        Deque<VariableEmitter> stack = variables.get(name);
        VariableEmitter var = stack != null && !stack.isEmpty() ? stack.peek() : null;
        boundVariableStack.get(boundVariableStack.size() - 1).put(name, var);
        return var;
    }

    public void addVariable(String name, VariableEmitter value) {
        Deque<VariableEmitter> stack = variables.get(name);
        if (stack == null) {
            stack = new ArrayDeque<>();
            variables.put(name, stack);
        }
        stack.push(value);
    }

    public void removeVariable(String name) {
        Deque<VariableEmitter> stack = variables.get(name);
        if (stack != null) {
            stack.pop();
            if (stack.isEmpty()) {
                variables.remove(name);
            }
        }
    }
}
