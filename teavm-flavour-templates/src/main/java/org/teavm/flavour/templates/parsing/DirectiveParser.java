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
package org.teavm.flavour.templates.parsing;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.htmlparser.jericho.Segment;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.TypeUnifier;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.ValueTypeFormatter;
import org.teavm.flavour.expr.type.meta.AnnotationBoolean;
import org.teavm.flavour.expr.type.meta.AnnotationDescriber;
import org.teavm.flavour.expr.type.meta.AnnotationString;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.MethodDescriber;
import org.teavm.flavour.templates.Action;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.IgnoreContent;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.Variable;

/**
 *
 * @author Alexey Andreev
 */
class DirectiveParser {
    private ClassDescriberRepository classRepository;
    private List<Diagnostic> diagnostics;
    private Segment segment;
    private GenericTypeNavigator typeNavigator;

    public DirectiveParser(ClassDescriberRepository classRepository, List<Diagnostic> diagnostics, Segment segment) {
        this.classRepository = classRepository;
        this.diagnostics = diagnostics;
        this.segment = segment;
        this.typeNavigator = new GenericTypeNavigator(classRepository);
    }

    public DirectiveMetadata parse(ClassDescriber cls) {
        AnnotationDescriber annot = cls.getAnnotation(BindDirective.class.getName());
        if (annot == null) {
            error("Class " + cls.getName() + " declared by directive package is not marked by " +
                    BindDirective.class.getName());
            return null;
        }

        DirectiveMetadata metadata = new DirectiveMetadata();
        metadata.name = ((AnnotationString)annot.getValue("name")).value;
        metadata.cls = cls;

        parseConstructor(metadata);
        parseIgnoreContent(metadata);
        GenericClass genericCls = typeNavigator.getGenericClass(cls.getName());
        parseMethodsRec(metadata, genericCls, new HashSet<GenericClass>(), new HashSet<MethodWithParams>());

        return metadata;
    }

    private void parseConstructor(DirectiveMetadata metadata) {
        ClassDescriber cls = metadata.cls;
        metadata.constructor = cls.getMethod("<init>", new GenericClass(Slot.class.getName()));
        if (metadata.constructor == null) {
            error("Class " + cls.getName() + " declared by directive package does not have constructor " +
                    "that takes " + Slot.class.getName());
        }
    }

    private void parseIgnoreContent(DirectiveMetadata metadata) {
        if (metadata.cls.getAnnotation(IgnoreContent.class.getName()) != null) {
            metadata.ignoreContent = true;
        }
    }

    private void parseMethodsRec(DirectiveMetadata metadata, GenericClass genericCls, Set<GenericClass> visited,
            Set<MethodWithParams> visitedMethods) {
        if (!visited.add(genericCls)) {
            return;
        }
        parseMethods(metadata, genericCls, visitedMethods);
        GenericClass parent = typeNavigator.getParent(genericCls);
        if (parent != null) {
            parseMethodsRec(metadata, parent, visited, new HashSet<>(visitedMethods));
        }
        for (GenericClass iface : typeNavigator.getInterfaces(genericCls)) {
            parseMethodsRec(metadata, iface, visited, new HashSet<>(visitedMethods));
        }
    }

    private void parseMethods(DirectiveMetadata metadata, GenericClass genericCls,
            Set<MethodWithParams> visitedMethods) {
        ClassDescriber clsDesc = classRepository.describe(genericCls.getName());
        if (clsDesc == null) {
            return;
        }
        Map<TypeVar, GenericType> substitutions = new HashMap<>();
        TypeVar[] typeVars = clsDesc.getTypeVariables();
        for (int i = 0; i < typeVars.length; ++i) {
            substitutions.put(typeVars[i], genericCls.getArguments().get(i));
        }

        for (MethodDescriber methodDesc : clsDesc.getMethods()) {
            ValueType[] argumentTypes = methodDesc.getArgumentTypes();
            for (int i = 0; i < argumentTypes.length; ++i) {
                if (argumentTypes[i] instanceof GenericType) {
                    argumentTypes[i] = ((GenericType)argumentTypes[i]).substitute(substitutions);
                }
            }
            ValueType returnType = methodDesc.getReturnType();
            if (returnType instanceof GenericType) {
                returnType = ((GenericType)returnType).substitute(substitutions);
            }
            GenericMethod method = new GenericMethod(methodDesc, argumentTypes, returnType);
            if (visitedMethods.add(new MethodWithParams(methodDesc.getName(), argumentTypes))) {
                parseMethod(metadata, method);
            }
        }
    }

    private void parseMethod(DirectiveMetadata metadata, GenericMethod method) {
        Set<AnnotationDescriber> bindings = new HashSet<>();
        parseBindContent(metadata, method, bindings);
        parseBindAttribute(metadata, method, bindings);
    }

    private void parseBindContent(DirectiveMetadata metadata, GenericMethod method,
            Set<AnnotationDescriber> bindings) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindContent.class.getName());
        if (binding == null) {
            return;
        }
        bindings.add(binding);
        if (metadata.contentSetter != null) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindContent.class.getName() +
                    " but another method is alredy bound to content of directive " + metadata.cls.getName() + ": " +
                    methodToString(metadata.contentSetter));
            return;
        }
        metadata.contentSetter = method.getDescriber();
        ValueType[] arguments = method.getActualArgumentTypes();
        if (arguments.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked with " +
                    BindContent.class.getName() + " and therefore should take exactly 1 argument, " +
                    "but takes " + arguments.length);
            return;
        }
        if (!arguments[0].equals(new GenericClass(Fragment.class.getName()))) {
            error("Method " + methodToString(method.getDescriber()) + " is marked with " +
                    BindContent.class.getName() + " and therefore should take " + Fragment.class.getName() +
                    " as an argument, but takes " + arguments[0]);
        }
    }

    private void parseBindAttribute(DirectiveMetadata metadata, GenericMethod method,
            Set<AnnotationDescriber> bindings) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindAttribute.class.getName());
        if (binding == null) {
            return;
        }
        bindings.add(binding);
        String name = ((AnnotationString)binding.getValue("name")).value;

        DirectiveAttributeMetadata existing = metadata.attributes.get(name);
        if (existing != null) {
            error("Method " + methodToString(method.getDescriber()) + " is bound to " + name + " attribute, but " +
                    "it is already bound to another method: " + methodToString(existing.setter));
            return;
        }

        DirectiveAttributeMetadata attrMetadata = new DirectiveAttributeMetadata();
        attrMetadata.name = name;
        metadata.attributes.put(name, attrMetadata);
        attrMetadata.setter = method.getDescriber();

        AnnotationBoolean optionalValue = (AnnotationBoolean)binding.getValue("optional");
        attrMetadata.required = optionalValue == null || !optionalValue.value;

        ValueType[] arguments = method.getActualArgumentTypes();
        if (arguments.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " +
                    BindAttribute.class.getName() + " and therefore must take exactly 1 argument, but " +
                    "takes " + arguments.length);
            return;
        }

        if (!parseAttributeType(attrMetadata, arguments[0])) {
            error("Method " + methodToString(method.getDescriber()) + " takes argument of type that can't be " +
                    "mapped to an attribute: " + arguments[0]);
        }
    }

    private boolean parseAttributeType(DirectiveAttributeMetadata attrMetadata, ValueType valueType) {
        if (!(valueType instanceof GenericType)) {
            return false;
        }

        TypeVar typeVar = new TypeVar();

        TypeUnifier unifier = new TypeUnifier(classRepository);
        GenericClass computationType = new GenericClass(Computation.class.getName(),
                new GenericReference(typeVar));
        if (unifier.unify(computationType, (GenericType)valueType, false)) {
            attrMetadata.type = DirectiveAttributeType.COMPUTATION;
            attrMetadata.valueType = unifier.getSubstitutions().get(typeVar);
            return true;
        }

        unifier = new TypeUnifier(classRepository);
        GenericClass variableType = new GenericClass(Variable.class.getName(), new GenericReference(typeVar));
        if (unifier.unify(variableType, (GenericType)valueType, false)) {
            attrMetadata.type = DirectiveAttributeType.VARIABLE;
            attrMetadata.valueType = unifier.getSubstitutions().get(typeVar);
            return true;
        }

        if (!valueType.equals(new GenericClass(Action.class.getName()))) {
            attrMetadata.type = DirectiveAttributeType.ACTION;
            return true;
        }

        return false;
    }

    private String methodToString(MethodDescriber method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getOwner().getName()).append('.').append(method.getName()).append('(');
        ValueTypeFormatter formatter = new ValueTypeFormatter();
        ValueType[] args = method.getArgumentTypes();
        for (int i = 0; i < args.length; ++i) {
            if (i > 0) {
                sb.append(", ");
            }
            formatter.format(args[i], sb);
        }
        return sb.toString();
    }

    private void error(String message) {
        diagnostics.add(new Diagnostic(segment.getBegin(), segment.getEnd(), message));
    }

    static class MethodWithParams {
        final String name;
        final ValueType[] params;

        public MethodWithParams(String name, ValueType[] params) {
            this.name = name;
            this.params = params.clone();
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + Arrays.hashCode(params);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MethodWithParams)) {
                return false;
            }
            MethodWithParams other = (MethodWithParams)obj;
            return name.equals(other.name) && Arrays.equals(params, other.params);
        }
    }
}
