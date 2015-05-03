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
import java.util.Map;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.DomFragment;
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
class FragmentEmitter {
    EmitContext context;

    public FragmentEmitter(EmitContext context) {
        this.context = context;
    }

    public String emitTemplate(List<TemplateNode> fragment) {
        String ownerCls = context.classStack.get(context.classStack.size() - 1);
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(Fragment.class.getName());
        context.addConstructor(cls);

        MethodHolder method = new MethodHolder("create", ValueType.parse(Component.class));
        method.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        Variable thisVar = program.createVariable();
        BasicBlock block = program.createBasicBlock();

        Variable ownerVar = program.createVariable();
        GetFieldInstruction getOwner = new GetFieldInstruction();
        getOwner.setInstance(thisVar);
        getOwner.setField(new FieldReference(cls.getName(), "this$owner"));
        getOwner.setFieldType(ValueType.object(ownerCls));
        getOwner.setReceiver(ownerVar);
        block.getInstructions().add(getOwner);

        String workerCls = emitWorkerClass(fragment);
        Variable workerVar = program.createVariable();
        ConstructInstruction createWorker = new ConstructInstruction();
        createWorker.setType(workerCls);
        createWorker.setReceiver(workerVar);
        block.getInstructions().add(createWorker);

        InvokeInstruction initWorker = new InvokeInstruction();
        initWorker.setType(InvocationType.SPECIAL);
        initWorker.setInstance(workerVar);
        initWorker.setMethod(new MethodReference(workerCls, "<init>", ValueType.object(ownerCls), ValueType.VOID));
        initWorker.getArguments().add(ownerVar);
        block.getInstructions().add(initWorker);

        Variable resultVar = program.createVariable();
        InvokeInstruction runWorker = new InvokeInstruction();
        runWorker.setType(InvocationType.VIRTUAL);
        runWorker.setInstance(workerVar);
        runWorker.setMethod(new MethodReference(Fragment.class, "create", Component.class));
        runWorker.setReceiver(resultVar);
        block.getInstructions().add(runWorker);

        ExitInstruction exit = new ExitInstruction();
        exit.setValueToReturn(resultVar);
        block.getInstructions().add(exit);

        method.setProgram(program);
        cls.addMethod(method);

        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private String emitWorkerClass(List<TemplateNode> fragment) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setParent(DomFragment.class.getName());
        context.addConstructor(cls);
        context.pushBoundVars();
        emitBuildDomMethod(cls, fragment);
        Map<String, EmittedVariable> vars = context.popBoundVars();
        if (!vars.isEmpty()) {
            emitVariableCache(cls, vars);
        }
        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void emitBuildDomMethod(ClassHolder cls, List<TemplateNode> fragment) {
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

    private void emitVariableCache(ClassHolder cls, Map<String, EmittedVariable> vars) {
        MethodHolder updateMethod = new MethodHolder("update", ValueType.VOID);
        updateMethod.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        Variable thisVar = program.createVariable();
        BasicBlock block = program.createBasicBlock();

        for (EmittedVariable var : vars.values()) {
            FieldHolder field = new FieldHolder("cache$" + var.name);
            cls.addField(field);
            PutFieldInstruction saveValue = new PutFieldInstruction();
            saveValue.setInstance(thisVar);
            saveValue.setField(field.getReference());
            saveValue.setValue(emitVariableReader(cls, thisVar, var, block));
            block.getInstructions().add(saveValue);
        }

        block.getInstructions().add(new ExitInstruction());
        updateMethod.setProgram(program);
        cls.addMethod(updateMethod);
    }

    private Variable emitVariableReader(ClassHolder cls, Variable thisVar, EmittedVariable emitVar,
            BasicBlock block) {
        Program program = block.getProgram();
        String lastClass = cls.getName();
        Variable var = thisVar;

        for (int i = context.classStack.size() - 1; i >= emitVar.depth; --i) {
            GetFieldInstruction insn = new GetFieldInstruction();
            insn.setFieldType(ValueType.object(context.classStack.get(i)));
            insn.setInstance(var);
            insn.setField(new FieldReference(lastClass, "this$owner"));
            var = program.createVariable();
            insn.setReceiver(var);
            block.getInstructions().add(insn);
            lastClass = context.classStack.get(i);
        }

        GetFieldInstruction getVarInsn = new GetFieldInstruction();
        getVarInsn.setField(new FieldReference(lastClass, "var$" + emitVar.name));
        getVarInsn.setInstance(var);
        getVarInsn.setFieldType(emitVar.type);
        var = program.createVariable();
        getVarInsn.setReceiver(var);
        block.getInstructions().add(getVarInsn);

        return var;
    }
}
