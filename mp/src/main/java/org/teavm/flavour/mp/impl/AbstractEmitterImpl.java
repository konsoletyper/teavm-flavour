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

import org.teavm.flavour.mp.Action;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Computation;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.DoubleConstantInstruction;
import org.teavm.model.instructions.FloatConstantInstruction;
import org.teavm.model.instructions.IntegerConstantInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NullConstantInstruction;

/**
 *
 * @author Alexey Andreev
 */
public abstract class AbstractEmitterImpl<T> implements Emitter<T> {
    private EmitterContextImpl context;
    private ClassReaderSource classSource;
    CompositeMethodGenerator generator;
    private MethodReference templateMethod;
    private ValueType returnType;
    private boolean hasReturn;

    public AbstractEmitterImpl(EmitterContextImpl context, ClassReaderSource classSource,
            CompositeMethodGenerator generator, MethodReference templateMethod, ValueType returnType) {
        this.context = context;
        this.classSource = classSource;
        this.generator = generator;
        this.templateMethod = templateMethod;
        this.returnType = returnType;
    }

    @Override
    public EmitterContextImpl getContext() {
        return context;
    }

    @Override
    public <S> Value<S> emit(Computation<S> computation) {
        Fragment fragment = (Fragment) computation;
        MethodReader method = classSource.resolve(fragment.method);
        generator.addProgram(templateMethod, method.getProgram(), fragment.capturedValues);
        return new ValueImpl<>(generator.getResultVar());
    }

    @Override
    public void emit(Action action) {
        Fragment fragment = (Fragment) action;
        MethodReader method = classSource.resolve(fragment.method);
        generator.addProgram(templateMethod, method.getProgram(), fragment.capturedValues);
    }

    @Override
    public <S> Choice<S> choose(ReflectClass<S> type) {
        return null;
    }

    @Override
    public <S> Choice<S> choose(Class<S> type) {
        return null;
    }

    @Override
    public void returnValue(Computation<T> computation) {
        hasReturn = true;
        if (computation instanceof Fragment) {
            Fragment fragment = (Fragment) computation;
            MethodReader method = classSource.resolve(fragment.method);
            generator.addProgram(templateMethod, method.getProgram(), fragment.capturedValues);

            Program targetProgram = generator.program;
            targetProgram.basicBlockAt(targetProgram.basicBlockCount() - 1);
            returnValue(unbox(generator.getResultVar()));
        } else if (computation instanceof ValueImpl) {
            ValueImpl<?> value = (ValueImpl<?>) computation;
            returnValue(unbox(value.innerValue));
        } else {
            throw new IllegalStateException("Unexpected computation type: " + computation.getClass().getName());
        }
    }

    protected abstract void returnValue(Variable var);

    public void close() {
        if (hasReturn) {
            return;
        }

        Variable var;
        if (returnType instanceof ValueType.Void) {
            var = null;
        } else if (returnType instanceof ValueType.Primitive) {
            var = generator.program.createVariable();
            switch (((ValueType.Primitive) returnType).getKind()) {
                case BOOLEAN:
                case BYTE:
                case SHORT:
                case CHARACTER:
                case INTEGER: {
                    IntegerConstantInstruction constantInsn = new IntegerConstantInstruction();
                    constantInsn.setReceiver(var);
                    generator.add(constantInsn);
                    break;
                }
                case LONG: {
                    LongConstantInstruction constantInsn = new LongConstantInstruction();
                    constantInsn.setReceiver(var);
                    generator.add(constantInsn);
                    break;
                }
                case FLOAT: {
                    FloatConstantInstruction constantInsn = new FloatConstantInstruction();
                    constantInsn.setReceiver(var);
                    generator.add(constantInsn);
                    break;
                }
                case DOUBLE: {
                    DoubleConstantInstruction constantInsn = new DoubleConstantInstruction();
                    constantInsn.setReceiver(var);
                    generator.add(constantInsn);
                    break;
                }
            }
        } else {
            NullConstantInstruction constantInsn = new NullConstantInstruction();
            var = generator.program.createVariable();
            constantInsn.setReceiver(var);
            generator.add(constantInsn);
        }

        returnValue(var);
    }

    private Variable unbox(Variable var) {
        if (returnType instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) returnType).getKind()) {
                case BOOLEAN:
                    var = unbox(var, Boolean.class, boolean.class);
                    break;
                case BYTE:
                    var = unbox(var, Byte.class, byte.class);
                    break;
                case SHORT:
                    var = unbox(var, Short.class, short.class);
                    break;
                case CHARACTER:
                    var = unbox(var, Character.class, char.class);
                    break;
                case INTEGER:
                    var = unbox(var, Integer.class, int.class);
                    break;
                case LONG:
                    var = unbox(var, Long.class, long.class);
                    break;
                case FLOAT:
                    var = unbox(var, Float.class, float.class);
                    break;
                case DOUBLE:
                    var = unbox(var, Double.class, double.class);
                    break;
            }
        }
        return var;
    }

    private Variable unbox(Variable var, Class<?> boxed, Class<?> primitive) {
        InvokeInstruction insn = new InvokeInstruction();
        insn.setInstance(var);
        insn.setType(InvocationType.VIRTUAL);
        insn.setMethod(new MethodReference(boxed, primitive.getName() + "Value", primitive));
        var = generator.program.createVariable();
        insn.setReceiver(var);
        generator.add(insn);
        return var;
    }
}
