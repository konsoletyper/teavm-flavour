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

import java.util.*;
import net.htmlparser.jericho.Segment;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.type.*;
import org.teavm.flavour.expr.type.meta.*;
import org.teavm.flavour.templates.*;

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
    }

    private void parseBindContent(DirectiveMetadata metadata, GenericMethod method,
            Set<AnnotationDescriber> bindings) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindContent.class.getName());
        if (binding == null) {
            return;
        }
        bindings.add(binding);
        if (metadata.contentSetter != null) {
            error("Another method is alredy bound to content of directive " + metadata.cls.getName() + ": " +
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
