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
package org.teavm.flavour.mp.impl;

import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ExitInstruction;

/**
 *
 * @author Alexey Andreev
 */
public class EmitterImpl<T> extends AbstractEmitterImpl<T> {
    public EmitterImpl(EmitterContextImpl context, MethodReference templateMethod, ValueType returnType) {
        super(context, new CompositeMethodGenerator(context,
                new TopLevelVariableContext(context.agent.getDiagnostics())),
                templateMethod, returnType);
    }

    public EmitterImpl(EmitterContextImpl context, MethodReference templateMethod, ValueType returnType,
            VariableContext varContext) {
        super(context, new CompositeMethodGenerator(context, varContext), templateMethod,
                returnType);
    }

    @Override
    protected void returnValue(Variable var) {
        ExitInstruction insn = new ExitInstruction();
        insn.setValueToReturn(var);
        generator.add(insn);
    }
}
