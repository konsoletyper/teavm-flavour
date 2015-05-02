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

import java.util.List;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomFragment;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.model.*;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

/**
 *
 * @author Alexey Andreev
 */
class FragmentEmitter {
    EmitContext context;

    public FragmentEmitter(EmitContext context) {
        this.context = context;
    }

    public String emitTemplate(List<TemplateNode> fragment) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setParent(DomFragment.class.getName());
        addConstructor(cls);
        addBuildDomMethod(cls, fragment);

        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    void addConstructor(ClassHolder cls) {
        String ownerType = context.classStack.get(context.classStack.size() - 1);
        FieldHolder ownerField = new FieldHolder("this$owner");
        ownerField.setType(ValueType.object(ownerType));
        ownerField.setLevel(AccessLevel.PUBLIC);
        cls.addField(ownerField);

        MethodHolder ctor = new MethodHolder("<init>", ValueType.object(ownerType), ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        Program prog = new Program();
        Variable thisVar = prog.createVariable();
        Variable ownerVar = prog.createVariable();
        BasicBlock block = prog.createBasicBlock();

        InvokeInstruction invokeSuper = new InvokeInstruction();
        invokeSuper.setInstance(thisVar);
        invokeSuper.setMethod(new MethodReference(DomFragment.class.getName(), "<init>", ValueType.VOID));
        invokeSuper.setType(InvocationType.SPECIAL);
        block.getInstructions().add(invokeSuper);

        PutFieldInstruction putOwner = new PutFieldInstruction();
        putOwner.setInstance(thisVar);
        putOwner.setField(new FieldReference(cls.getName(), "this$owner"));
        putOwner.setFieldType(ValueType.object(ownerType));
        putOwner.setValue(ownerVar);
        block.getInstructions().add(putOwner);

        ExitInstruction exit = new ExitInstruction();
        block.getInstructions().add(exit);

        ctor.setProgram(prog);
        cls.addMethod(ctor);
    }

    private void addBuildDomMethod(ClassHolder cls, List<TemplateNode> fragment) {
        MethodHolder buildDomMethod = new MethodHolder("buildDom", ValueType.object(DomBuilder.class.getName()),
                ValueType.VOID);
        buildDomMethod.setLevel(AccessLevel.PUBLIC);
        Program prog = new Program();
        Variable thisVar = prog.createVariable();
        Variable builderVar = prog.createVariable();
        TemplateNodeEmitter nodeEmitter = new TemplateNodeEmitter(context, prog, thisVar, builderVar);
        nodeEmitter.block = prog.createBasicBlock();

        context.classStack.add(cls.getName());
        for (TemplateNode node : fragment) {
            node.acceptVisitor(nodeEmitter);
        }
        context.classStack.remove(context.classStack.size() - 1);

        nodeEmitter.block.getInstructions().add(new ExitInstruction());

        buildDomMethod.setProgram(prog);
        cls.addMethod(buildDomMethod);
    }
}
