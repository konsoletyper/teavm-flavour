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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.htmlparser.jericho.Segment;
import org.teavm.dom.html.HTMLElement;
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
import org.teavm.flavour.expr.type.meta.AnnotationList;
import org.teavm.flavour.expr.type.meta.AnnotationString;
import org.teavm.flavour.expr.type.meta.AnnotationValue;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.MethodDescriber;
import org.teavm.flavour.templates.Action;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindDirectiveName;
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

    public Object parse(ClassDescriber cls) {
        AnnotationDescriber annot = cls.getAnnotation(BindDirective.class.getName());
        AnnotationDescriber attrAnnot = cls.getAnnotation(BindAttributeDirective.class.getName());
        if (annot != null) {
            return parseElement(cls, annot);
        } else if (attrAnnot != null) {
            return parseAttribute(cls, attrAnnot);
        } else {
            error("Class " + cls.getName() + " declared by directive package is not marked either by " +
                    BindDirective.class.getName() + " or by " + BindAttributeDirective.class.getName());
            return null;
        }
    }

    private DirectiveMetadata parseElement(ClassDescriber cls, AnnotationDescriber annot) {
        DirectiveMetadata metadata = new DirectiveMetadata();
        metadata.nameRules = parseNames(annot);
        metadata.cls = cls;

        parseConstructor(metadata);
        parseIgnoreContent(metadata);
        GenericClass genericCls = typeNavigator.getGenericClass(cls.getName());
        for (GenericMethod method : collectMethods(genericCls)) {
            parseMethod(metadata, method);
        }

        return metadata;
    }

    private AttributeDirectiveMetadata parseAttribute(ClassDescriber cls, AnnotationDescriber annot) {
        AttributeDirectiveMetadata metadata = new AttributeDirectiveMetadata();
        metadata.nameRules = parseNames(annot);
        metadata.cls = cls;

        parseAttributeConstructor(metadata);
        GenericClass genericCls = typeNavigator.getGenericClass(cls.getName());
        for (GenericMethod method : collectMethods(genericCls)) {
            parseAttributeMethod(metadata, method);
        }

        if (metadata.type == null) {
            return null;
        }
        return metadata;
    }

    private String[] parseNames(AnnotationDescriber annot) {
        List<AnnotationValue> names = ((AnnotationList)annot.getValue("name")).value;
        String[] result = new String[names.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = ((AnnotationString)names.get(i)).value;
        }
        return result;
    }

    private void parseConstructor(DirectiveMetadata metadata) {
        ClassDescriber cls = metadata.cls;
        metadata.constructor = cls.getMethod("<init>", new GenericClass(Slot.class.getName()));
        if (metadata.constructor == null) {
            error("Class " + cls.getName() + " declared by directive package does not have constructor " +
                    "that takes " + Slot.class.getName());
        }
    }

    private void parseAttributeConstructor(AttributeDirectiveMetadata metadata) {
        ClassDescriber cls = metadata.cls;
        metadata.constructor = cls.getMethod("<init>", new GenericClass(HTMLElement.class.getName()));
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

    private List<GenericMethod> collectMethods(GenericClass genericCls) {
        List<GenericMethod> methods = new ArrayList<>();
        collectMethodsRec(genericCls, new HashSet<GenericClass>(), new HashSet<MethodWithParams>(), methods);
        return methods;
    }

    private void collectMethodsRec(GenericClass genericCls, Set<GenericClass> visited,
            Set<MethodWithParams> visitedMethods, List<GenericMethod> methods) {
        if (!visited.add(genericCls)) {
            return;
        }
        collectMethods(genericCls, visitedMethods, methods);
        GenericClass parent = typeNavigator.getParent(genericCls);
        if (parent != null) {
            collectMethodsRec(parent, visited, new HashSet<>(visitedMethods), methods);
        }
        for (GenericClass iface : typeNavigator.getInterfaces(genericCls)) {
            collectMethodsRec(iface, visited, new HashSet<>(visitedMethods), methods);
        }
    }

    private void collectMethods(GenericClass genericCls, Set<MethodWithParams> visitedMethods,
            List<GenericMethod> methods) {
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
            GenericMethod method = new GenericMethod(methodDesc, genericCls, argumentTypes, returnType);
            if (visitedMethods.add(new MethodWithParams(methodDesc.getName(), argumentTypes))) {
                methods.add(method);
            }
        }
    }

    private void parseMethod(DirectiveMetadata metadata, GenericMethod method) {
        Set<AnnotationDescriber> bindings = new HashSet<>();
        parseBindContent(metadata, method, bindings);
        parseBindAttribute(metadata, method, bindings);
        parseBindName(metadata, method);
    }

    private void parseAttributeMethod(AttributeDirectiveMetadata metadata, GenericMethod method) {
        parseBindAttributeContent(metadata, method);
        parseBindName(metadata, method);
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

    private void parseBindAttributeContent(AttributeDirectiveMetadata metadata, GenericMethod method) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindContent.class.getName());
        if (binding == null) {
            return;
        }
        if (metadata.type != null) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindContent.class.getName() +
                    " but another method is alredy bound to content of directive " + metadata.cls.getName() + ": " +
                    methodToString(metadata.setter));
        }
        metadata.setter = method.getDescriber();
        ValueType[] arguments = method.getActualArgumentTypes();
        if (arguments.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked with " +
                    BindContent.class.getName() + " and therefore should take exactly 1 argument, " +
                    "but takes " + arguments.length);
            return;
        }

        DirectiveAttributeMetadata attrMeta = new DirectiveAttributeMetadata();
        if (!parseAttributeType(attrMeta, arguments[0])) {
            error("Method " + methodToString(method.getDescriber()) + " takes argument of type that can't be " +
                    "mapped to an attribute: " + arguments[0]);
        } else {
            metadata.type = attrMeta.type;
            metadata.valueType = attrMeta.valueType;
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

        if (valueType.equals(new GenericClass(Action.class.getName()))) {
            attrMetadata.type = DirectiveAttributeType.ACTION;
            return true;
        }

        return false;
    }

    private void parseBindName(BaseDirectiveMetadata directive, GenericMethod method) {
        AnnotationDescriber annot = method.getDescriber().getAnnotation(BindDirectiveName.class.getName());
        if (annot == null) {
            return;
        }

        if (directive.nameSetter != null) {
            error("Method " + methodToString(method.getDescriber()) + " declares binding to annotation name " +
                    "that is already bound to " + methodToString(directive.nameSetter));
            return;
        }

        ValueType[] args = method.getActualArgumentTypes();
        if (args.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " +
                    BindDirectiveName.class.getName() + " and therefore must take exactly 1 argument, but " +
                    "takes " + args.length);
            return;
        }

        if (!args[0].equals(new GenericClass(String.class.getName()))) {
            error("Method " + methodToString(method.getDescriber()) + " takes argument of type that can't be " +
                    "mapped to directive's name: " + args[0]);
        }
        directive.nameSetter = method.getDescriber();
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
