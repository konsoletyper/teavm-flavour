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
import org.teavm.dependency.DependencyAgent;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomFragment;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.model.*;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

/**
 *
 * @author Alexey Andreev
 */
public class TemplateEmitter {
    DependencyAgent dependencyAgent;

    public TemplateEmitter(DependencyAgent dependencyAgent) {
        this.dependencyAgent = dependencyAgent;
    }

    public String emitTemplate(List<TemplateNode> template) {
        ClassHolder cls = new ClassHolder(dependencyAgent.generateClassName());
        cls.setParent(DomFragment.class.getName());
        addConstructor(cls);
        addBuildDomMethod(cls, template);

        dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void addConstructor(ClassHolder cls) {
        MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        Program prog = new Program();
        Variable thisVar = prog.createVariable();
        BasicBlock block = prog.createBasicBlock();

        InvokeInstruction invokeSuper = new InvokeInstruction();
        invokeSuper.setInstance(thisVar);
        invokeSuper.setMethod(new MethodReference(DomFragment.class.getName(), "<init>", ValueType.VOID));
        invokeSuper.setType(InvocationType.SPECIAL);
        block.getInstructions().add(invokeSuper);

        ExitInstruction exit = new ExitInstruction();
        block.getInstructions().add(exit);

        ctor.setProgram(prog);
        cls.addMethod(ctor);
    }

    private void addBuildDomMethod(ClassHolder cls, List<TemplateNode> template) {
        MethodHolder buildDomMethod = new MethodHolder("buildDom", ValueType.object(DomBuilder.class.getName()),
                ValueType.VOID);
        buildDomMethod.setLevel(AccessLevel.PUBLIC);
        Program prog = new Program();
        Variable thisVar = prog.createVariable();
        Variable builderVar = prog.createVariable();
        BasicBlock block = prog.createBasicBlock();

        cls.addMethod(buildDomMethod);
    }
}
