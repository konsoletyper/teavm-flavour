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
import org.teavm.dependency.DependencyAgent;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
class EmitContext {
    DependencyAgent dependencyAgent;
    List<String> classStack = new ArrayList<>();
    Map<String, Deque<EmittedVariable>> variables = new HashMap<>();
    FragmentEmitter fragmentEmitter;
    ExprPlanEmitter exprEmitter;

    public EmittedVariable getVariable(String name) {
        Deque<EmittedVariable> stack = variables.get(name);
        return stack != null && !stack.isEmpty() ? stack.peek() : null;
    }

    public void addVariable(String name, ValueType type) {
        Deque<EmittedVariable> stack = variables.get(name);
        if (stack == null) {
            stack = new ArrayDeque<>();
            variables.put(name, stack);
        }
        EmittedVariable ev = new EmittedVariable();
        ev.name = name;
        ev.depth = classStack.size() - 1;
        ev.type = type;
        stack.push(ev);
    }

    public void removeVariable(String name) {
        Deque<EmittedVariable> stack = variables.get(name);
        if (stack != null) {
            stack.pop();
            if (stack.isEmpty()) {
                variables.remove(name);
            }
        }
    }
}
