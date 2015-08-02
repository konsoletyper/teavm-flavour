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
package org.teavm.flavour.json.test;

import java.util.List;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.Instruction;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 *
 * @author Alexey Andreev
 */
public class TeaVMJSONTestHack implements ClassHolderTransformer, TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        host.add(this);
    }

    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program == null) {
                continue;
            }
            for (int i = 0; i < program.basicBlockCount(); ++i) {
                BasicBlock block = program.basicBlockAt(i);
                transformBlock(block);
            }
        }
    }

    private void transformBlock(BasicBlock block) {
        List<Instruction> instructions = block.getInstructions();
        for (int i = 0; i < instructions.size(); ++i) {
            Instruction insn = instructions.get(i);
            if (!(insn instanceof InvokeInstruction)) {
                continue;
            }

            InvokeInstruction invocation = (InvokeInstruction)insn;
            if (invocation.getMethod().getClassName().equals(JSONRunner.class.getName()) &&
                    invocation.getMethod().getName().equals("serialize")) {
                invocation.setMethod(new MethodReference(TeaVMJSONRunner.class.getName(),
                        invocation.getMethod().getDescriptor()));
            }
            if (invocation.getMethod().getClassName().equals(JSONRunner.class.getName()) &&
                    invocation.getMethod().getName().equals("deserialize")) {
                invocation.setMethod(new MethodReference(TeaVMJSONRunner.class.getName(),
                        invocation.getMethod().getDescriptor()));
            }
        }
    }
}
