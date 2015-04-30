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

import org.teavm.flavour.templates.DomBuilder;
import org.teavm.flavour.templates.tree.*;
import org.teavm.model.*;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.StringConstantInstruction;

/**
 *
 * @author Alexey Andreev
 */
class TemplateNodeEmitter implements TemplateNodeVisitor {
    private TemplateEmitter templateEmitter;
    private Program program;
    private Variable thisVar;
    private Variable builderVar;
    private BasicBlock block;

    @Override
    public void visit(DirectiveBinding node) {

    }

    @Override
    public void visit(DOMElement node) {
        Variable tagNameVar = stringConstant(node.getName());

        InvokeInstruction openInsn = new InvokeInstruction();
        openInsn.setInstance(builderVar);
        openInsn.setMethod(new MethodReference(DomBuilder.class.getName(), "open", ValueType.parse(String.class),
                ValueType.parse(DomBuilder.class)));
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
        builderVar = program.createVariable();
        openInsn.setReceiver(builderVar);
        block.getInstructions().add(closeInsn);
    }

    @Override
    public void visit(DOMText node) {
        Variable textVar = program.createVariable();

    }

    private Variable stringConstant(String value) {
        Variable var = program.createVariable();
        StringConstantInstruction tagNameInsn = new StringConstantInstruction();
        tagNameInsn.setReceiver(var);
        tagNameInsn.setConstant(value);
        block.getInstructions().add(tagNameInsn);
        return var;
    }
}
