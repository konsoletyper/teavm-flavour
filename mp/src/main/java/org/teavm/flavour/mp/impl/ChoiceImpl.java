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

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Computation;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.Value;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Incoming;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Phi;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.BranchingCondition;
import org.teavm.model.instructions.BranchingInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.JumpInstruction;

/**
 *
 * @author Alexey Andreev
 */
public class ChoiceImpl<T> implements Choice<T> {
    private EmitterContextImpl context;
    private ClassReaderSource classSource;
    private MethodReference templateMethod;
    CompositeMethodGenerator generator;
    private ValueImpl<T> value;
    ValueType type;
    private BasicBlock successor;
    private BasicBlock predecessor;
    private BasicBlock defaultBlock;
    private ChoiceEmitterImpl defaultOption;
    private Phi phi;
    private List<ChoiceEmitterImpl> options = new ArrayList<>();

    ChoiceImpl(EmitterContextImpl context, MethodReference templateMethod,
            CompositeMethodGenerator generator, ValueType type) {
        this.context = context;
        this.classSource = context.getReflectContext().getClassSource();
        this.templateMethod = templateMethod;
        this.generator = generator;
        this.type = type;
        predecessor = generator.currentBlock();
        successor = generator.program.createBasicBlock();

        defaultBlock = generator.program.createBasicBlock();
        generator.blockIndex = defaultBlock.getIndex();
        defaultOption = createEmitter();

        JumpInstruction insn = new JumpInstruction();
        insn.setTarget(defaultBlock);
        generator.blockIndex = predecessor.getIndex();
        generator.add(insn);

        if (type != ValueType.VOID) {
            value = new ValueImpl<>(generator.program.createVariable(), generator.varContext, type);
            phi = new Phi();
            CallLocation location = new CallLocation(templateMethod, generator.location);
            phi.setReceiver(generator.varContext.emitVariable(value, location));
            successor.getPhis().add(phi);
        }

        generator.blockIndex = successor.getIndex();
    }

    @Override
    public Emitter<T> option(Computation<Boolean> condition) {
        predecessor.getInstructions().remove(predecessor.getInstructions().size() - 1);

        Fragment fragment = (Fragment) condition;
        MethodReader method = classSource.resolve(fragment.method);
        generator.blockIndex = predecessor.getIndex();
        generator.addProgram(templateMethod, method.getProgram(), fragment.capturedValues);

        InvokeInstruction unboxInsn = new InvokeInstruction();
        unboxInsn.setInstance(generator.getResultVar());
        unboxInsn.setMethod(new MethodReference(Boolean.class, "booleanValue", boolean.class));
        unboxInsn.setType(InvocationType.VIRTUAL);
        unboxInsn.setReceiver(generator.program.createVariable());
        generator.add(unboxInsn);
        BasicBlock optionBlock = generator.program.createBasicBlock();
        BasicBlock nextPredecessor = generator.program.createBasicBlock();

        BranchingInstruction insn = new BranchingInstruction(BranchingCondition.EQUAL);
        insn.setOperand(unboxInsn.getReceiver());
        insn.setConsequent(nextPredecessor);
        insn.setAlternative(optionBlock);
        generator.add(insn);
        predecessor = nextPredecessor;

        generator.blockIndex = optionBlock.getIndex();
        ChoiceEmitterImpl optionEmitter = createEmitter();
        options.add(optionEmitter);

        generator.blockIndex = predecessor.getIndex();
        JumpInstruction defaultInsn = new JumpInstruction();
        defaultInsn.setTarget(defaultBlock);
        generator.add(defaultInsn);

        generator.blockIndex = successor.getIndex();
        return optionEmitter;
    }

    @Override
    public Emitter<T> defaultOption() {
        return defaultOption;
    }

    @Override
    public Value<T> getValue() {
        return value;
    }

    private ChoiceEmitterImpl createEmitter() {
        return new ChoiceEmitterImpl(context, templateMethod);
    }

    public void close() {
        for (ChoiceEmitterImpl option : options) {
            option.close();
        }
        defaultOption.close();
    }

    private static CompositeMethodGenerator createGenerator(ChoiceImpl<?> choice) {
        CompositeMethodGenerator generator = new CompositeMethodGenerator(choice.context,
                choice.generator.varContext, choice.generator.program);
        generator.location = choice.generator.location;
        generator.blockIndex = choice.generator.blockIndex;
        generator.forcedLocation = choice.generator.forcedLocation;
        return generator;
    }

    class ChoiceEmitterImpl extends AbstractEmitterImpl<T> {
        ChoiceEmitterImpl(EmitterContextImpl context, MethodReference templateMethod) {
            super(context, createGenerator(ChoiceImpl.this), templateMethod, type);
        }

        @Override
        protected void returnValue(Variable var) {
            if (var != null) {
                Incoming incoming = new Incoming();
                incoming.setSource(generator.currentBlock());
                incoming.setValue(var);
                phi.getIncomings().add(incoming);
            }
            JumpInstruction insn = new JumpInstruction();
            insn.setTarget(successor);
            generator.add(insn);
        }
    }
}
