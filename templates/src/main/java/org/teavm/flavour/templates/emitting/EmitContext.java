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
import org.teavm.flavour.expr.Location;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.InstructionLocation;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class EmitContext {
    String sourceFileName;
    String modelClassName;
    int suffixGenerator;
    DependencyAgent dependencyAgent;
    List<String> classStack = new ArrayList<>();
    List<Map<String, EmittedVariable>> boundVariableStack = new ArrayList<>();
    Map<String, Deque<EmittedVariable>> variables = new HashMap<>();

    public void pushBoundVars() {
        boundVariableStack.add(new HashMap<String, EmittedVariable>());
    }

    public Map<String, EmittedVariable> popBoundVars() {
        Map<String, EmittedVariable> vars = boundVariableStack.get(boundVariableStack.size() - 1);
        boundVariableStack.remove(boundVariableStack.size() - 1);
        return vars;
    }

    public EmittedVariable getVariable(String name) {
        Deque<EmittedVariable> stack = variables.get(name);
        EmittedVariable var = stack != null && !stack.isEmpty() ? stack.peek() : null;
        boundVariableStack.get(boundVariableStack.size() - 1).put(name, var);
        return var;
    }

    public void addVariable(String name, ValueType type) {
        Deque<EmittedVariable> stack = variables.get(name);
        if (stack == null) {
            stack = new ArrayDeque<>();
            variables.put(name, stack);
        }
        EmittedVariable ev = new EmittedVariable();
        ev.name = name;
        ev.depth = classStack.size();
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

    void addConstructor(ClassHolder cls, Location location) {
        String ownerType = classStack.get(classStack.size() - 1);
        FieldHolder ownerField = new FieldHolder("this$owner");
        ownerField.setType(ValueType.object(ownerType));
        ownerField.setLevel(AccessLevel.PUBLIC);
        cls.addField(ownerField);

        MethodHolder ctor = new MethodHolder("<init>", ValueType.object(ownerType), ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(ctor, dependencyAgent.getClassSource());
        location(pe, location);
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter ownerVar = pe.var(1, ValueType.object(ownerType));

        thisVar.invokeSpecial(new MethodReference(cls.getParent(), "<init>", ValueType.VOID));
        thisVar.setField("this$owner", ownerVar);
        pe.exit();

        cls.addMethod(ctor);
    }

    void location(ProgramEmitter pe, Location location) {
        if (location != null) {
            pe.setCurrentLocation(new InstructionLocation(sourceFileName, location.getStart()));
        }
    }
}
