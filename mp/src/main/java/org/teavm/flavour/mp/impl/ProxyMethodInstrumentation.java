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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyMethodInstrumentation {
    private static String lambdaMetafactory = LambdaMetafactory.class.getName().replace('.', '/');

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
                super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
                return;
            }
        }
    }
}
