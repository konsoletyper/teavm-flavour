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

import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.tree.DOMAttribute;
import org.teavm.flavour.templates.tree.DOMElement;
import org.teavm.flavour.templates.tree.DOMText;
import org.teavm.flavour.templates.tree.DirectiveBinding;
import org.teavm.flavour.templates.tree.DirectiveComputationBinding;
import org.teavm.flavour.templates.tree.DirectiveVariableBinding;
import org.teavm.flavour.templates.tree.TemplateNode;
import org.teavm.flavour.templates.tree.TemplateNodeVisitor;
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
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.GetFieldInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;
import org.teavm.model.instructions.StringConstantInstruction;

/**
 *
 * @author Alexey Andreev
 */
class TemplateNodeEmitter implements TemplateNodeVisitor {
    private EmitContext context;
    private Program program;
    private Variable thisVar;
    private Variable builderVar;
    BasicBlock block;

    public TemplateNodeEmitter(EmitContext context, Program program, Variable thisVar, Variable builderVar) {
        this.context = context;
        this.program = program;
        this.thisVar = thisVar;
        this.builderVar = builderVar;
    }

    @Override
    public void visit(DOMElement node) {
        Variable tagNameVar = stringConstant(node.getName());

        InvokeInstruction openInsn = new InvokeInstruction();
        openInsn.setInstance(builderVar);
        openInsn.setMethod(new MethodReference(DomBuilder.class.getName(), "open", ValueType.parse(String.class),
                ValueType.parse(DomBuilder.class)));
        openInsn.setType(InvocationType.VIRTUAL);
        openInsn.getArguments().add(tagNameVar);
        builderVar = program.createVariable();
        openInsn.setReceiver(builderVar);
        block.getInstructions().add(openInsn);

        for (DOMAttribute attr : node.getAttributes()) {
            Variable attrNameVar = stringConstant(attr.getName());
            Variable attrValueVar = stringConstant(attr.getValue());

            InvokeInstruction attrInsn = new InvokeInstruction();
            attrInsn.setInstance(builderVar);
            attrInsn.setMethod(new MethodReference(DomBuilder.class.getName(), "attribute",
                    ValueType.parse(String.class), ValueType.parse(String.class),
                    ValueType.parse(DomBuilder.class)));
            attrInsn.setType(InvocationType.VIRTUAL);
            attrInsn.getArguments().add(attrNameVar);
            attrInsn.getArguments().add(attrValueVar);
            builderVar = program.createVariable();
            attrInsn.setReceiver(builderVar);
            block.getInstructions().add(attrInsn);
        }

        for (TemplateNode child : node.getChildNodes()) {
            child.acceptVisitor(this);
        }

        InvokeInstruction closeInsn = new InvokeInstruction();
        openInsn.setInstance(builderVar);
        openInsn.setMethod(new MethodReference(DomBuilder.class.getName(), "close",
                ValueType.parse(DomBuilder.class)));
        openInsn.setType(InvocationType.VIRTUAL);
        builderVar = program.createVariable();
        openInsn.setReceiver(builderVar);
        block.getInstructions().add(closeInsn);
    }

    @Override
    public void visit(DOMText node) {
        Variable textVar = stringConstant(node.getValue());

        InvokeInstruction insn = new InvokeInstruction();
        insn.setInstance(builderVar);
        insn.setMethod(new MethodReference(DomBuilder.class.getName(), "text", ValueType.parse(String.class),
                ValueType.parse(DomBuilder.class)));
        insn.getArguments().add(textVar);
        builderVar = program.createVariable();
        insn.setReceiver(builderVar);
        block.getInstructions().add(insn);
    }

    @Override
    public void visit(DirectiveBinding node) {
        String className = emitComponentFragmentClass(node);

        Variable fragmentVar = program.createVariable();
        ConstructInstruction constructInsn = new ConstructInstruction();
        constructInsn.setType(className);
        constructInsn.setReceiver(fragmentVar);
        block.getInstructions().add(constructInsn);

        String ownerType = context.classStack.get(context.classStack.size() - 1);
        InvokeInstruction initInsn = new InvokeInstruction();
        initInsn.setInstance(fragmentVar);
        initInsn.setType(InvocationType.SPECIAL);
        initInsn.setMethod(new MethodReference(className, "<init>", ValueType.object(ownerType), ValueType.VOID));
        initInsn.getArguments().add(thisVar);
        block.getInstructions().add(initInsn);

        InvokeInstruction addInsn = new InvokeInstruction();
        addInsn.setInstance(builderVar);
        addInsn.setType(InvocationType.VIRTUAL);
        addInsn.setMethod(new MethodReference(DomBuilder.class.getName(), "add", ValueType.parse(Fragment.class),
                ValueType.parse(DomBuilder.class)));
        addInsn.getArguments().add(fragmentVar);
        fragmentVar = program.createVariable();
        addInsn.setReceiver(fragmentVar);
        block.getInstructions().add(addInsn);
    }

    private String emitComponentFragmentClass(DirectiveBinding node) {
        String className = context.dependencyAgent.generateClassName();
        ClassHolder fragmentCls = new ClassHolder(className);
        fragmentCls.setParent(Object.class.getName());
        fragmentCls.getInterfaces().add(Fragment.class.getName());

        emitDirectiveFields(fragmentCls, node);
        context.fragmentEmitter.addConstructor(fragmentCls);
        emitDirectiveWorker(fragmentCls, node);

        context.dependencyAgent.submitClass(fragmentCls);
        return className;
    }

    private void emitDirectiveFields(ClassHolder cls, DirectiveBinding directive) {
        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            FieldHolder field = new FieldHolder("var$" + varBinding.getName());
            field.setType(convertValueType(varBinding.getValueType()));
            field.setLevel(AccessLevel.PUBLIC);
            cls.addField(field);
        }
    }

    private void emitDirectiveWorker(ClassHolder cls, DirectiveBinding directive) {
        context.classStack.add(cls.getName());

        MethodHolder method = new MethodHolder("create", ValueType.parse(Component.class));
        method.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        BasicBlock block = program.createBasicBlock();
        Variable thisVar = program.createVariable();

        Variable componentVar = program.createVariable();
        ConstructInstruction constructComponent = new ConstructInstruction();
        constructComponent.setReceiver(componentVar);
        constructComponent.setType(directive.getClassName());
        block.getInstructions().add(constructComponent);

        Variable slotVar = program.createVariable();
        InvokeInstruction createSlot = new InvokeInstruction();
        createSlot.setMethod(new MethodReference(Slot.class.getName(), "create", ValueType.parse(Slot.class)));
        createSlot.setReceiver(slotVar);
        createSlot.setType(InvocationType.SPECIAL);
        block.getInstructions().add(createSlot);

        InvokeInstruction initComponent = new InvokeInstruction();
        initComponent.setInstance(componentVar);
        initComponent.setMethod(new MethodReference(directive.getClassName(), "<init>",
                ValueType.parse(Slot.class), ValueType.VOID));
        initComponent.setType(InvocationType.SPECIAL);
        initComponent.getArguments().add(slotVar);

        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            emitVariable(cls, varBinding, block, thisVar, componentVar);
        }

        for (DirectiveComputationBinding computation : directive.getComputations()) {
            emitComputation(cls, computation, block, thisVar, componentVar);
        }

        if (directive.getContentMethodName() != null) {
            emitContent(cls, directive, block, thisVar, componentVar);
        }

        cls.addMethod(method);

        for (DirectiveVariableBinding varBinding : directive.getVariables()) {
            context.removeVariable(varBinding.getName());
        }
        context.classStack.remove(context.classStack.size() - 1);
    }

    private void emitVariable(ClassHolder cls, DirectiveVariableBinding varBinding, BasicBlock block,
            Variable thisVar, Variable componentVar) {
        Program program = block.getProgram();
        String varClass = emitVariableClass(cls, varBinding);

        Variable varVar = program.createVariable();
        ConstructInstruction constructVar = new ConstructInstruction();
        constructVar.setReceiver(varVar);
        constructVar.setType(varClass);
        block.getInstructions().add(constructVar);

        InvokeInstruction initVar = new InvokeInstruction();
        initVar.setType(InvocationType.SPECIAL);
        initVar.setInstance(varVar);
        initVar.setMethod(new MethodReference(varClass, "<init>", ValueType.object(cls.getName()),
                ValueType.VOID));
        initVar.getArguments().add(thisVar);
        block.getInstructions().add(initVar);

        InvokeInstruction setVar = new InvokeInstruction();
        initVar.setType(InvocationType.SPECIAL);
        initVar.setInstance(componentVar);
        initVar.setMethod(new MethodReference(varBinding.getMethodOwner(), varBinding.getMethodName(),
                ValueType.parse(org.teavm.flavour.templates.Variable.class), ValueType.VOID));
        block.getInstructions().add(setVar);

        context.addVariable(varClass, convertValueType(varBinding.getValueType()));
    }

    private String emitVariableClass(ClassHolder owner, DirectiveVariableBinding varBinding) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(Object.class.getName());
        cls.getInterfaces().add(org.teavm.flavour.templates.Variable.class.getName());

        context.fragmentEmitter.addConstructor(cls);

        MethodHolder setMethod = new MethodHolder("set", ValueType.parse(Object.class), ValueType.VOID);
        setMethod.setLevel(AccessLevel.PUBLIC);
        Program program = new Program();
        Variable thisVar = program.createVariable();
        Variable valueVar = program.createVariable();
        BasicBlock block = program.createBasicBlock();
        ValueType varType = convertValueType(varBinding.getValueType());

        Variable ownerVar = program.createVariable();
        GetFieldInstruction insn = new GetFieldInstruction();
        insn.setField(new FieldReference(cls.getName(), "this$owner"));
        insn.setFieldType(ValueType.object(owner.getName()));
        insn.setReceiver(ownerVar);
        insn.setInstance(thisVar);
        block.getInstructions().add(insn);

        if (!varBinding.getValueType().equals(new GenericClass("java.lang.Object"))) {
            Variable castVar = program.createVariable();
            CastInstruction castInsn = new CastInstruction();
            castInsn.setValue(valueVar);
            castInsn.setReceiver(castVar);
            castInsn.setTargetType(varType);
            block.getInstructions().add(castInsn);
            valueVar = castVar;
        }

        PutFieldInstruction putField = new PutFieldInstruction();
        putField.setField(new FieldReference(owner.getName(), "var$" + varBinding.getName()));
        putField.setFieldType(varType);
        putField.setInstance(ownerVar);
        putField.setValue(valueVar);
        block.getInstructions().add(putField);

        block.getInstructions().add(putField);

        setMethod.setProgram(program);
        cls.addMethod(setMethod);

        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private void emitComputation(ClassHolder cls, DirectiveComputationBinding computation, BasicBlock block,
            Variable thisVar, Variable componentVar) {
        Program program = block.getProgram();
        String computationClass = emitComputationClass(computation);

        Variable computationVar = program.createVariable();
        ConstructInstruction constructComputation = new ConstructInstruction();
        constructComputation.setReceiver(computationVar);
        constructComputation.setType(computationClass);
        block.getInstructions().add(constructComputation);

        InvokeInstruction initComputation = new InvokeInstruction();
        initComputation.setType(InvocationType.SPECIAL);
        initComputation.setInstance(computationVar);
        initComputation.setMethod(new MethodReference(computationClass, "<init>", ValueType.object(cls.getName()),
                ValueType.VOID));
        initComputation.getArguments().add(thisVar);
        block.getInstructions().add(initComputation);

        InvokeInstruction setComputation = new InvokeInstruction();
        initComputation.setType(InvocationType.SPECIAL);
        initComputation.setInstance(componentVar);
        initComputation.setMethod(new MethodReference(computation.getMethodOwner(), computation.getMethodName(),
                ValueType.parse(Computation.class), ValueType.VOID));
        block.getInstructions().add(setComputation);
    }

    private String emitComputationClass(DirectiveComputationBinding computation) {
        return context.exprEmitter.emitComputation(computation.getComputationPlan().getPlan());
    }

    private void emitContent(ClassHolder cls, DirectiveBinding directive, BasicBlock block, Variable thisVar,
            Variable componentVar) {
        Program program = block.getProgram();
        String contentClass = context.fragmentEmitter.emitTemplate(directive.getContentNodes());

        Variable computationVar = program.createVariable();
        ConstructInstruction constructVar = new ConstructInstruction();
        constructVar.setReceiver(computationVar);
        constructVar.setType(contentClass);
        block.getInstructions().add(constructVar);

        InvokeInstruction initContent = new InvokeInstruction();
        initContent.setType(InvocationType.SPECIAL);
        initContent.setInstance(computationVar);
        initContent.setMethod(new MethodReference(contentClass, "<init>", ValueType.object(cls.getName()),
                ValueType.VOID));
        initContent.getArguments().add(thisVar);
        block.getInstructions().add(initContent);

        InvokeInstruction setContent = new InvokeInstruction();
        initContent.setType(InvocationType.SPECIAL);
        initContent.setInstance(componentVar);
        initContent.setMethod(new MethodReference(contentClass, directive.getContentMethodName(),
                ValueType.parse(Fragment.class), ValueType.VOID));
        block.getInstructions().add(setContent);
    }

    private Variable stringConstant(String value) {
        Variable var = program.createVariable();
        StringConstantInstruction tagNameInsn = new StringConstantInstruction();
        tagNameInsn.setReceiver(var);
        tagNameInsn.setConstant(value);
        block.getInstructions().add(tagNameInsn);
        return var;
    }

    private ValueType convertValueType(org.teavm.flavour.expr.type.ValueType type) {
        if (type instanceof Primitive) {
            switch (((Primitive)type).getKind()) {
                case BOOLEAN:
                    return ValueType.BOOLEAN;
                case CHAR:
                    return ValueType.CHARACTER;
                case BYTE:
                    return ValueType.BYTE;
                case SHORT:
                    return ValueType.SHORT;
                case INT:
                    return ValueType.INTEGER;
                case LONG:
                    return ValueType.LONG;
                case FLOAT:
                    return ValueType.FLOAT;
                case DOUBLE:
                    return ValueType.DOUBLE;
                default:
                    throw new AssertionError();
            }
        } else if (type instanceof GenericClass) {
            return ValueType.object(((GenericClass)type).getName());
        } else if (type instanceof GenericArray) {
            return ValueType.arrayOf(convertValueType(((GenericArray)type).getElementType()));
        } else if (type instanceof GenericReference) {
            TypeVar typeVar = ((GenericReference)type).getVar();
            if (typeVar.getUpperBound() == null) {
                return ValueType.object("java.lang.Object");
            } else {
                return convertValueType(typeVar.getUpperBound());
            }
        } else {
            throw new AssertionError("Unsupported type: " + type);
        }
    }
}
