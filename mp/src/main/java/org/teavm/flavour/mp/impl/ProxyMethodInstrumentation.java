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

import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.teavm.flavour.mp.Action;
import org.teavm.flavour.mp.Computation;
import org.teavm.model.MethodReference;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyMethodInstrumentation {
    private static String lambdaMetafactory = LambdaMetafactory.class.getName().replace('.', '/');
    private static String actionType = Action.class.getName().replace('.', '/');
    private static String computationType = Computation.class.getName().replace('.', '/');
    private static String proxyHelperType = Type.getInternalName(ProxyRuntimeHelper.class);
    private static String listDesc = Type.getDescriptor(List.class);

    public byte[] instrument(byte[] bytecode) {
        ClassReader reader = new ClassReader(bytecode);
        ClassWriter writer = new ClassWriter(0);
        ClassTransformer transformer = new ClassTransformer(writer);
        reader.accept(transformer, 0);
        return writer.toByteArray();
    }

    class ClassTransformer extends ClassVisitor {
        public ClassTransformer(ClassVisitor cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor innerVisitor = super.visitMethod(access, name, desc, signature, exceptions);
            return new MethodTransformer(innerVisitor);
        }
    }

    class MethodTransformer extends MethodVisitor {
        public MethodTransformer(MethodVisitor mv) {
            super(Opcodes.ASM5, mv);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            if (!bsm.getOwner().equals(lambdaMetafactory) || bsm.getName().equals("metafactory")) {
                Type returnType = Type.getReturnType(desc);
                if (returnType.getSort() == Type.OBJECT) {
                    if (returnType.getClassName().equals(actionType)) {
                        transformAction(desc, bsmArgs);
                        return;
                    } else if (returnType.getClassName().equals(computationType)) {
                        transformComputation(desc, bsmArgs);
                        return;
                    }
                }
            }
            super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        }

        private void transformAction(String desc, Object[] bsmArgs) {
            transformArguments(desc);
            transformMethod((Handle) bsmArgs[1]);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ActionImpl.class), "create",
                    "(" + Type.getDescriptor(List.class) + Type.getDescriptor(MethodReference.class) + ")"
                    + Type.getDescriptor(ActionImpl.class), false);
        }

        private void transformComputation(String desc, Object[] bsmArgs) {
            transformArguments(desc);
            transformMethod((Handle) bsmArgs[1]);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ComputationImpl.class), "create",
                    "(" + Type.getDescriptor(List.class) + Type.getDescriptor(MethodReference.class) + ")"
                    + Type.getDescriptor(ComputationImpl.class), false);
        }

        private void transformArguments(String desc) {
            Type[] argTypes = Type.getArgumentTypes(desc);
            String arrayListType = Type.getInternalName(ArrayList.class);

            super.visitTypeInsn(Opcodes.NEW, arrayListType);
            super.visitInsn(Opcodes.DUP);
            super.visitIntInsn(Opcodes.SIPUSH, argTypes.length);
            super.visitMethodInsn(Opcodes.INVOKESPECIAL, arrayListType, "<init>", "(I)V", false);

            for (int i = argTypes.length - 1; i >= 0; --i) {
                transformArgument(argTypes[i]);
            }

            super.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Collections.class), "reverse",
                    "(" + listDesc + ")V", false);
        }

        private void transformArgument(Type type) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.SHORT:
                case Type.CHAR:
                case Type.INT:
                    transformArgument("I");
                    break;
                case Type.LONG:
                    transformArgument("L");
                    break;
                case Type.FLOAT:
                    transformArgument("F");
                    break;
                case Type.DOUBLE:
                    transformArgument("D");
                    break;
                default:
                    transformArgument("Ljava/lang/Object;");
                    break;
            }
        }

        private void transformArgument(String desc) {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, proxyHelperType, "add",
                    "(" + desc + listDesc + ")" + listDesc, false);
        }

        private void transformMethod(Handle handle) {
            super.visitTypeInsn(Opcodes.NEW, Type.getInternalName(MethodReference.class));
            super.visitInsn(Opcodes.DUP);

            Type ownerType = Type.getType("L" + handle.getOwner() + ";");
            super.visitLdcInsn(ownerType);
            super.visitLdcInsn(handle.getName());

            Type[] argTypes = Type.getArgumentTypes(handle.getDesc());
            Type resultType = Type.getReturnType(handle.getDesc());
            super.visitIntInsn(Opcodes.SIPUSH, argTypes.length + 1);
            super.visitTypeInsn(Opcodes.ANEWARRAY, "[Ljava/lang/Class;");
            for (int i = 0; i < argTypes.length; ++i) {
                super.visitInsn(Opcodes.DUP);
                super.visitIntInsn(Opcodes.SIPUSH, i);
                super.visitLdcInsn(argTypes[i]);
                super.visitInsn(Opcodes.AASTORE);
            }
            super.visitInsn(Opcodes.DUP);
            super.visitIntInsn(Opcodes.SIPUSH, argTypes.length);
            super.visitLdcInsn(resultType);
            super.visitInsn(Opcodes.AASTORE);

            super.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(MethodReference.class),
                    "<init>", "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)V", false);
        }
    }
}
