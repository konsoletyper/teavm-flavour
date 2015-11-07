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
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

/**
 *
 * @author Alexey Andreev
 */
public class TemplateEmitter {
    private DependencyAgent dependencyAgent;

    public TemplateEmitter(DependencyAgent dependencyAgent) {
        this.dependencyAgent = dependencyAgent;
    }

    public String emitTemplate(String modelClassName, String sourceFileName, List<TemplateNode> fragment) {
        ClassHolder cls = new ClassHolder(modelClassName + "$Flavour_Template");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(Fragment.class.getName());

        String innerName = emitInnerFragment(sourceFileName, cls, modelClassName, fragment);
        emitConstructor(cls, modelClassName, innerName);
        emitWorker(cls, innerName);

        dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private String emitInnerFragment(String sourceFileName, ClassHolder cls, String modelClassName,
            List<TemplateNode> fragment) {
        EmitContext context = new EmitContext();
        context.sourceFileName = sourceFileName;
        context.addVariable("this", ValueType.object(modelClassName));
        context.classStack.add(cls.getName());
        context.dependencyAgent = dependencyAgent;
        context.modelClassName = modelClassName;
        return new FragmentEmitter(context).emitTemplate(fragment);
    }

    private void emitConstructor(ClassHolder cls, String modelClassName, String innerName) {
        FieldHolder model = new FieldHolder("var$this");
        model.setType(ValueType.object(modelClassName));
        model.setLevel(AccessLevel.PUBLIC);
        cls.addField(model);

        FieldHolder innerFragment = new FieldHolder("this$inner");
        innerFragment.setType(ValueType.object(innerName));
        innerFragment.setLevel(AccessLevel.PUBLIC);
        cls.addField(innerFragment);

        MethodHolder ctor = new MethodHolder("<init>", ValueType.object(modelClassName), ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        Variable thisVar = program.createVariable();
        Variable modelVar = program.createVariable();
        BasicBlock block = program.createBasicBlock();

        InvokeInstruction invokeSuper = new InvokeInstruction();
        invokeSuper.setType(InvocationType.SPECIAL);
        invokeSuper.setInstance(thisVar);
        invokeSuper.setMethod(new MethodReference(Object.class, "<init>", void.class));
        block.getInstructions().add(invokeSuper);

        PutFieldInstruction putField = new PutFieldInstruction();
        putField.setField(new FieldReference(cls.getName(), "var$this"));
        putField.setFieldType(ValueType.object(modelClassName));
        putField.setInstance(thisVar);
        putField.setValue(modelVar);
        block.getInstructions().add(putField);

        Variable innerVar = program.createVariable();
        ConstructInstruction createInner = new ConstructInstruction();
        createInner.setReceiver(innerVar);
        createInner.setType(innerName);
        block.getInstructions().add(createInner);

        InvokeInstruction initInner = new InvokeInstruction();
        initInner.setInstance(innerVar);
        initInner.setType(InvocationType.SPECIAL);
        initInner.setMethod(new MethodReference(innerName, "<init>", ValueType.object(cls.getName()), ValueType.VOID));
        initInner.getArguments().add(thisVar);
        block.getInstructions().add(initInner);

        PutFieldInstruction saveInner = new PutFieldInstruction();
        saveInner.setFieldType(ValueType.object(innerName));
        saveInner.setField(new FieldReference(cls.getName(), "this$inner"));
        saveInner.setInstance(thisVar);
        saveInner.setValue(innerVar);
        block.getInstructions().add(saveInner);

        block.getInstructions().add(new ExitInstruction());

        ctor.setProgram(program);
        cls.addMethod(ctor);
    }

    private void emitWorker(ClassHolder cls, String innerName) {
        MethodHolder method = new MethodHolder("create", ValueType.parse(Component.class));
        method.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        BasicBlock block = program.createBasicBlock();
        Variable thisVar = program.createVariable();
        Variable innerVar = program.createVariable();
        Variable resultVar = program.createVariable();

        GetFieldInstruction getInner = new GetFieldInstruction();
        getInner.setField(new FieldReference(cls.getName(), "this$inner"));
        getInner.setFieldType(ValueType.object(innerName));
        getInner.setInstance(thisVar);
        getInner.setReceiver(innerVar);
        block.getInstructions().add(getInner);

        InvokeInstruction invokeInner = new InvokeInstruction();
        invokeInner.setInstance(innerVar);
        invokeInner.setType(InvocationType.VIRTUAL);
        invokeInner.setMethod(new MethodReference(innerName, "create", ValueType.parse(Component.class)));
        invokeInner.setReceiver(resultVar);
        block.getInstructions().add(invokeInner);

        ExitInstruction exit = new ExitInstruction();
        exit.setValueToReturn(resultVar);
        block.getInstructions().add(exit);

        method.setProgram(program);
        cls.addMethod(method);
    }
}
