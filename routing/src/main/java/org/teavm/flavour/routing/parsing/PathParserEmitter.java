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
package org.teavm.flavour.routing.parsing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.teavm.dependency.DependencyAgent;
import org.teavm.flavour.regex.Matcher;
import org.teavm.flavour.regex.Pattern;
import org.teavm.flavour.regex.automata.Dfa;
import org.teavm.flavour.regex.bytecode.MatcherClassBuilder;
import org.teavm.flavour.routing.parsing.PathParser.PathParserCase;
import org.teavm.flavour.routing.parsing.PathParser.PathParserElement;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;
import org.teavm.parsing.Parser;

/**
 *
 * @author Alexey Andreev
 */
public class PathParserEmitter {
    private DependencyAgent agent;
    private int dfaIndexGenerator;

    public PathParserEmitter(DependencyAgent agent) {
        this.agent = agent;
    }

    public ValueEmitter emitWorker(ProgramEmitter pe, PathParser pathParser) {
        ValueEmitter cases = pe.constructArray(PathParserCase.class, pathParser.cases.length);
        for (int i = 0; i < pathParser.cases.length; ++i) {
            PathParserCase parserCase = pathParser.cases[i];
            ValueEmitter parserCaseVar = pe.construct(PathParserCase.class);
            parserCaseVar.setField("prefixLength", pe.constant(parserCase.prefixLength));

            ValueEmitter elementsVar = pe.constructArray(PathParserElement.class, parserCase.elements.length);
            for (int j = 0; j < parserCase.elements.length; ++j) {
                PathParserElement element = parserCase.elements[j];
                ValueEmitter elementVar = pe.construct(PathParserElement.class);
                elementVar.setField("pattern", createPattern(pe, element.patternDfa));
                elementVar.setField("suffixLength", pe.constant(element.suffixLength));
                elementsVar.setElement(pe.constant(j), elementVar);
            }
            parserCaseVar.setField("elements", elementsVar);

            cases.setElement(pe.constant(i), parserCaseVar);
        }

        ValueEmitter caseSelector = createPattern(pe, pathParser.caseSelectorDfa);
        return pe.construct(PathParser.class, cases, caseSelector.cast(Pattern.class));
    }

    public ValueEmitter createPattern(ProgramEmitter pe, Dfa dfa) {
        return pe.construct(createPatternClass(dfa).getName());
    }

    public ClassHolder createPatternClass(Dfa dfa) {
        int index = dfaIndexGenerator++;
        ClassHolder matcherClass = createDfaClass(dfa, index);
        agent.submitClass(matcherClass);

        ClassHolder patternClass = new ClassHolder("org.teavm.flavour.internal.Pattern" + index);
        patternClass.setParent("java.lang.Object");
        patternClass.getInterfaces().add(Pattern.class.getName());
        patternClass.setLevel(AccessLevel.PUBLIC);

        MethodHolder ctor = new MethodHolder("<init>", ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(ctor, agent.getClassSource());
        pe.var(0, patternClass).invokeSpecial(Object.class, "<init>");
        pe.exit();
        patternClass.addMethod(ctor);

        MethodHolder worker = new MethodHolder("matcher", ValueType.parse(Matcher.class));
        worker.setLevel(AccessLevel.PUBLIC);
        pe = ProgramEmitter.create(worker, agent.getClassSource());
        pe.construct(matcherClass.getName()).returnValue();
        patternClass.addMethod(worker);

        agent.submitClass(patternClass);
        return patternClass;
    }

    private ClassHolder createDfaClass(Dfa dfa, int index) {
        MatcherClassBuilder classBuilder = new MatcherClassBuilder();
        byte[] classFile = classBuilder.build("org.teavm.flavour.routing.internal.Matcher" + index, dfa);
        ClassReader reader = new ClassReader(classFile);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return Parser.parseClass(classNode);
    }

}
