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
import org.teavm.model.ClassHolder;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

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
        context.addConstructor(cls, null);

        MethodHolder method = new MethodHolder("create", ValueType.parse(Component.class));
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, context.dependencyAgent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);

        String workerCls = emitWorkerClass(fragment);
        ValueType ownerType = ValueType.object(ownerCls);

        pe.construct(workerCls, thisVar.getField("this$owner", ownerType))
                .invokeVirtual("create", Component.class)
                .returnValue();

        cls.addMethod(method);
        context.dependencyAgent.submitClass(cls);
        return cls.getName();
    }

    private String emitWorkerClass(List<TemplateNode> fragment) {
        ClassHolder cls = new ClassHolder(context.dependencyAgent.generateClassName());
        cls.setParent(DomFragment.class.getName());
        context.addConstructor(cls, null);
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
        ProgramEmitter pe = ProgramEmitter.create(buildDomMethod, context.dependencyAgent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter builderVar = pe.var(1, DomBuilder.class);

        TemplateNodeEmitter nodeEmitter = new TemplateNodeEmitter(context, pe, thisVar, builderVar);
        context.classStack.add(cls.getName());
        for (TemplateNode node : fragment) {
            node.acceptVisitor(nodeEmitter);
        }
        context.classStack.remove(context.classStack.size() - 1);
        pe.exit();

        cls.addMethod(buildDomMethod);
    }

    private void emitVariableCache(ClassHolder cls, Map<String, EmittedVariable> vars) {
        MethodHolder updateMethod = new MethodHolder("update", ValueType.VOID);
        updateMethod.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(updateMethod, context.dependencyAgent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);

        for (EmittedVariable var : vars.values()) {
            FieldHolder field = new FieldHolder("cache$" + var.name);
            field.setType(var.type);
            cls.addField(field);
            thisVar.setField(field.getName(), emitVariableReader(thisVar, var).cast(field.getType()));
        }
        pe.exit();

        cls.addMethod(updateMethod);
    }

    private ValueEmitter emitVariableReader(ValueEmitter thisVar, EmittedVariable emitVar) {
        for (int i = context.classStack.size() - 1; i >= emitVar.depth; --i) {
            thisVar = thisVar.getField("this$owner", ValueType.object(context.classStack.get(i)));
        }
        return thisVar.getField("var$" + emitVar.name, emitVar.type);
    }
}
