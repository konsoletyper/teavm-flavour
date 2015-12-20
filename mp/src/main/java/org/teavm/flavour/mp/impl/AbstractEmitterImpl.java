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
import org.teavm.flavour.mp.Action;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Computation;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.InvocationHandler;
import org.teavm.flavour.mp.LazyComputation;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.impl.reflect.ReflectClassImpl;
import org.teavm.flavour.mp.impl.reflect.ReflectMethodImpl;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodHolder;
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
import org.teavm.model.instructions.JumpInstruction;
import org.teavm.model.instructions.LongConstantInstruction;
import org.teavm.model.instructions.NullConstantInstruction;

/**
 *
 * @author Alexey Andreev
 */
public abstract class AbstractEmitterImpl<T> implements Emitter<T> {
    private EmitterContextImpl context;
    ClassReaderSource classSource;
    CompositeMethodGenerator generator;
    MethodReference templateMethod;
    private ValueType returnType;
    private boolean hasReturn;
    private boolean closed;
    private List<ChoiceImpl<?>> choices = new ArrayList<>();
    VariableContext varContext;
    int blockIndex;

    public AbstractEmitterImpl(EmitterContextImpl context, CompositeMethodGenerator generator,
            MethodReference templateMethod, ValueType returnType) {
        this.context = context;
        this.classSource = context.getReflectContext().getClassSource();
        this.generator = generator;
        blockIndex = generator.blockIndex;
        this.templateMethod = templateMethod;
        this.returnType = returnType;
        this.varContext = generator.varContext;
    }

    @Override
    public EmitterContextImpl getContext() {
        return context;
    }

    @Override
    public <S> Value<S> emit(Computation<S> computation) {
        generator.blockIndex = blockIndex;
        Fragment fragment = (Fragment) computation;
        MethodReader method = classSource.resolve(fragment.method);
        generator.addProgram(templateMethod, method.getProgram(), fragment.capturedValues);
        blockIndex = generator.blockIndex;
        return new ValueImpl<>(generator.getResultVar(), varContext, templateMethod.getReturnType());
    }

    @Override
    public void emit(Action action) {
        Fragment fragment = (Fragment) action;
        MethodReader method = classSource.resolve(fragment.method);
        generator.blockIndex = blockIndex;
        generator.addProgram(templateMethod, method.getProgram(), fragment.capturedValues);
        blockIndex = generator.blockIndex;
    }

    @Override
    public <S> Value<S> lazyFragment(LazyComputation<S> computation) {
        return new LazyValueImpl<>(varContext, computation, returnType);
    }

    @Override
    public <S> Choice<S> choose(ReflectClass<S> type) {
        ChoiceImpl<S> choice = new ChoiceImpl<>(context, templateMethod, generator, returnType);
        choices.add(choice);
        blockIndex = generator.blockIndex;
        return choice;
    }

    @Override
    public <S> Choice<S> choose(Class<S> type) {
        return choose(context.findClass(type));
    }

    @Override
    public void returnValue(Computation<? extends T> computation) {
        generator.blockIndex = blockIndex;
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
            returnValue(unbox(varContext.emitVariable(value)));
        } else {
            throw new IllegalStateException("Unexpected computation type: " + computation.getClass().getName());
        }
        blockIndex = generator.blockIndex;
    }

    @Override
    public <S> Value<S> proxy(ReflectClass<S> type, InvocationHandler<S> handler) {
        ValueType innerType = ((ReflectClassImpl<?>) type).type;
        ClassHolder cls = new ClassHolder(context.createProxyName(type.getName()));
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent("java.lang.Object");
        cls.getInterfaces().add(((ValueType.Object) innerType).getClassName());

        ProxyVariableContext nestedVarContext = new ProxyVariableContext(varContext, cls);
        for (ReflectMethod method : type.getMethods()) {
            ReflectMethodImpl methodImpl = (ReflectMethodImpl) method;
            MethodHolder methodHolder = new MethodHolder(methodImpl.method.getDescriptor());
            methodHolder.setLevel(AccessLevel.PUBLIC);

            EmitterImpl<Object> nestedEmitter = new EmitterImpl<>(context, templateMethod,
                    methodHolder.getResultType(), nestedVarContext);
            CompositeMethodGenerator nestedGenerator = nestedEmitter.generator;
            Program program = nestedGenerator.program;
            BasicBlock startBlock = nestedGenerator.currentBlock();
            nestedEmitter.blockIndex = program.createBasicBlock().getIndex();
            nestedVarContext.init(startBlock);

            methodHolder.setProgram(program);
            Variable thisVar = program.createVariable();
            @SuppressWarnings("unchecked")
            ValueImpl<Object>[] arguments = (ValueImpl<Object>[]) new ValueImpl<?>[methodImpl.method.parameterCount()];
            for (int i = 0; i < arguments.length; ++i) {
                arguments[i] = new ValueImpl<>(program.createVariable(), nestedVarContext,
                        methodImpl.method.parameterType(i));
            }

            handler.invoke(nestedEmitter, new ValueImpl<S>(thisVar, nestedVarContext, innerType),
                    methodImpl, arguments);

            JumpInstruction jumpToStart = new JumpInstruction();
            jumpToStart.setTarget(program.basicBlockAt(startBlock.getIndex() + 1));
            startBlock.getInstructions().add(jumpToStart);

            cls.addMethod(methodHolder);
        }

        ValueImpl<S> result = new ValueImpl<>(nestedVarContext.createInstance(generator), varContext, innerType);
        context.submitClass(cls);
        return result;
    }

    protected abstract void returnValue(Variable var);

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (ChoiceImpl<?> choice : choices) {
            choice.close();
        }

        if (hasReturn) {
            return;
        }

        hasReturn = true;
        generator.blockIndex = blockIndex;
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
