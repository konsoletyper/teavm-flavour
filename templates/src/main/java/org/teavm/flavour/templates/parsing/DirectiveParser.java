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
import java.util.stream.Collectors;
import net.htmlparser.jericho.Segment;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.type.GenericArray;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.GenericWildcard;
import org.teavm.flavour.expr.type.MapSubstitutions;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.ValueTypeFormatter;
import org.teavm.flavour.expr.type.meta.AnnotationDescriber;
import org.teavm.flavour.expr.type.meta.AnnotationList;
import org.teavm.flavour.expr.type.meta.AnnotationString;
import org.teavm.flavour.expr.type.meta.AnnotationValue;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.MethodDescriber;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindDirectiveName;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.IgnoreContent;
import org.teavm.flavour.templates.ModifierTarget;
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.Slot;

class DirectiveParser {
    private ClassDescriberRepository classRepository;
    private List<Diagnostic> diagnostics;
    private Segment segment;
    private GenericTypeNavigator typeNavigator;

    DirectiveParser(ClassDescriberRepository classRepository, List<Diagnostic> diagnostics, Segment segment) {
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
            error("Class " + cls.getName() + " declared by directive package is not marked either by "
                    + BindDirective.class.getName() + " or by " + BindAttributeDirective.class.getName());
            return null;
        }
    }

    private DirectiveMetadata parseElement(ClassDescriber cls, AnnotationDescriber annot) {
        return parseElement(typeNavigator.getGenericClass(cls.getName()), annot, true);
    }

    private DirectiveMetadata parseElement(GenericClass genericCls, AnnotationDescriber annot, boolean top) {
        DirectiveMetadata metadata = new DirectiveMetadata();
        metadata.nameRules = parseNames(annot);
        metadata.cls = typeNavigator.getClassRepository().describe(genericCls.getName());

        if (top) {
            parseConstructor(metadata);
        } else {
            parseNestedConstructor(metadata);
        }
        parseIgnoreContent(metadata);
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
        List<AnnotationValue> names = ((AnnotationList) annot.getValue("name")).value;
        String[] result = new String[names.size()];
        for (int i = 0; i < result.length; ++i) {
            result[i] = ((AnnotationString) names.get(i)).value;
        }
        return result;
    }

    private void parseConstructor(DirectiveMetadata metadata) {
        ClassDescriber cls = metadata.cls;
        metadata.constructor = cls.getMethod("<init>", new GenericClass(Slot.class.getName()));
        if (metadata.constructor == null) {
            error("Class " + cls.getName() + " declared by directive package does not have constructor "
                    + "that takes " + Slot.class.getName());
        }
    }

    private void parseNestedConstructor(DirectiveMetadata metadata) {
        ClassDescriber cls = metadata.cls;
        metadata.constructor = cls.getMethod("<init>");
        if (metadata.constructor == null) {
            error("Class " + cls.getName() + " declared by directive package does not have constructor "
                    + "that takes zero arguments");
        }
    }

    private void parseAttributeConstructor(AttributeDirectiveMetadata metadata) {
        ClassDescriber cls = metadata.cls;
        metadata.constructor = cls.getMethod("<init>", new GenericClass(ModifierTarget.class.getName()));
        if (metadata.constructor == null) {
            error("Class " + cls.getName() + " declared by directive package does not have constructor "
                    + "that takes " + ModifierTarget.class.getName());
        }
    }

    private void parseIgnoreContent(DirectiveMetadata metadata) {
        if (metadata.cls.getAnnotation(IgnoreContent.class.getName()) != null) {
            metadata.ignoreContent = true;
        }
    }

    private List<GenericMethod> collectMethods(GenericClass genericCls) {
        List<GenericMethod> methods = new ArrayList<>();
        collectMethodsRec(genericCls, new HashSet<>(), new HashSet<>(), methods);
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
        Map<TypeVar, GenericType> vars = new HashMap<>();
        TypeVar[] typeVars = clsDesc.getTypeVariables();
        for (int i = 0; i < typeVars.length; ++i) {
            vars.put(typeVars[i], genericCls.getArguments().get(i));
        }

        MapSubstitutions subst = new MapSubstitutions(vars);
        for (MethodDescriber methodDesc : clsDesc.getMethods()) {
            ValueType[] argumentTypes = methodDesc.getArgumentTypes();
            for (int i = 0; i < argumentTypes.length; ++i) {
                argumentTypes[i] = argumentTypes[i].substitute(subst);
            }
            ValueType returnType = methodDesc.getReturnType();
            if (returnType != null) {
                returnType = returnType.substitute(subst);
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
        parseBindDirective(metadata, method, bindings);
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
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindContent.class.getName()
                    + " but another method is alredy bound to content of directive " + metadata.cls.getName() + ": "
                    + methodToString(metadata.contentSetter));
            return;
        }
        metadata.contentSetter = method.getDescriber();
        ValueType[] arguments = method.getActualArgumentTypes();
        if (arguments.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked with "
                    + BindContent.class.getName() + " and therefore should take exactly 1 argument, "
                    + "but takes " + arguments.length);
            return;
        }
        if (!arguments[0].equals(new GenericClass(Fragment.class.getName()))) {
            error("Method " + methodToString(method.getDescriber()) + " is marked with "
                    + BindContent.class.getName() + " and therefore should take " + Fragment.class.getName()
                    + " as an argument, but takes " + arguments[0]);
        }
    }

    private void parseBindAttributeContent(AttributeDirectiveMetadata metadata, GenericMethod method) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindContent.class.getName());
        if (binding == null) {
            return;
        }
        if (metadata.type != null) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindContent.class.getName()
                    + " but another method is alredy bound to content of directive " + metadata.cls.getName() + ": "
                    + methodToString(metadata.setter));
        }
        metadata.setter = method.getDescriber();
        ValueType[] arguments = method.getActualArgumentTypes();

        if (arguments.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by "
                    + BindContent.class.getName() + " and therefore must take exactly 1 argument, but "
                    + "takes " + arguments.length);
            return;
        }
        metadata.setter = method.getDescriber();

        DirectiveAttributeMetadata attrMeta = new DirectiveAttributeMetadata();
        if (!parseAttributeType(attrMeta, arguments[0], method.getActualReturnType())) {
            error("Method " + methodToString(method.getDescriber()) + " takes argument of type that can't be "
                    + "mapped to an attribute: " + arguments[0]);
        } else {
            metadata.type = attrMeta.type;
            metadata.valueType = attrMeta.valueType;
            metadata.sam = attrMeta.sam;
        }
    }

    private void parseBindAttribute(DirectiveMetadata metadata, GenericMethod method,
            Set<AnnotationDescriber> bindings) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindAttribute.class.getName());
        if (binding == null) {
            return;
        }
        bindings.add(binding);
        String name = ((AnnotationString) binding.getValue("name")).value;

        DirectiveAttributeMetadata existing = metadata.attributes.get(name);
        if (existing != null) {
            if (!tryBidirectional(existing, method)) {
                error("Method " + methodToString(method.getDescriber()) + " is bound to " + name + " attribute, but "
                        + "it is already bound to another method: " + methodToString(existing.setter));
            }
            return;
        }

        DirectiveAttributeMetadata attrMetadata = new DirectiveAttributeMetadata();
        attrMetadata.name = name;
        metadata.attributes.put(name, attrMetadata);
        attrMetadata.required = method.getDescriber().getAnnotation(OptionalBinding.class.getName()) == null;

        ValueType[] arguments = method.getActualArgumentTypes();
        if (method.getActualReturnType() == null) {
            if (arguments.length != 1) {
                error("Method " + methodToString(method.getDescriber()) + " is marked by "
                        + BindAttribute.class.getName() + " and therefore must take exactly 1 argument, but "
                        + "takes " + arguments.length);
                return;
            }
            attrMetadata.setter = method.getDescriber();
        } else {
            if (arguments.length != 0) {
                error("Method " + methodToString(method.getDescriber()) + " is marked by "
                        + BindAttribute.class.getName() + " and therefore must not take arguments, but "
                        + "takes " + arguments.length);
                return;
            }
            attrMetadata.getter = method.getDescriber();
        }

        if (!parseAttributeType(attrMetadata, arguments.length == 1 ? arguments[0] : null,
                method.getActualReturnType())) {
            error("Method " + methodToString(method.getDescriber()) + " should either take lambda or return value, "
                    + "since it is mapped to an attribute: " + arguments[0]);
        }
    }

    private boolean tryBidirectional(DirectiveAttributeMetadata attribute, GenericMethod method) {
        if (attribute.type != DirectiveAttributeType.FUNCTION) {
            return false;
        }
        if (method.getActualReturnType() != null || method.getActualArgumentTypes().length != 1) {
            return false;
        }
        ValueType valueType = method.getActualArgumentTypes()[0];
        if (!(valueType instanceof GenericClass)) {
            return false;
        }
        GenericMethod sam = typeNavigator.findSingleAbstractMethod((GenericClass) valueType);

        if (isGetterLike(sam) && isSetterLike(attribute.sam)) {
            attribute.type = DirectiveAttributeType.BIDIRECTIONAL;
            attribute.altSam = attribute.sam;
            attribute.altSetter = attribute.setter;
            attribute.altValueType = attribute.valueType;
            attribute.sam = sam;
            attribute.setter = method.getDescriber();
            attribute.valueType = valueType;
            return true;
        } else if (isSetterLike(sam) && isGetterLike(attribute.sam)) {
            attribute.type = DirectiveAttributeType.BIDIRECTIONAL;
            attribute.altSam = sam;
            attribute.altSetter = method.getDescriber();
            attribute.altValueType = valueType;
            return true;
        } else {
            return false;
        }
    }

    private static boolean isGetterLike(GenericMethod sam) {
        return sam.getActualArgumentTypes().length == 0 && sam.getActualReturnType() != null;
    }

    private static boolean isSetterLike(GenericMethod sam) {
        return sam.getActualArgumentTypes().length == 1 && sam.getActualReturnType() == null;
    }

    private void parseBindDirective(DirectiveMetadata metadata, GenericMethod method,
            Set<AnnotationDescriber> bindings) {
        AnnotationDescriber binding = method.getDescriber().getAnnotation(BindDirective.class.getName());
        if (binding == null) {
            return;
        }

        bindings.add(binding);

        method = replaceWildcards(method);
        ValueType[] arguments = method.getActualArgumentTypes();
        if (method.getActualReturnType() != null || arguments.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindDirective.class.getName()
                    + " and therefore must take exactly one parameter and return void");
            return;
        }
        if (!(arguments[0] instanceof GenericType)) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindDirective.class.getName()
                    + " and therefore must not take primitive value");
            return;
        }

        boolean multiple = false;
        GenericType type = (GenericType) arguments[0];
        TypeInference inference = new TypeInference(typeNavigator);
        TypeVar var = new TypeVar();
        if (inference.subtypeConstraint(type, new GenericClass("java.util.List", new GenericReference(var)))) {
             type = inference.getSubstitutions().get(var);
             multiple = true;
        }

        if (!(type instanceof GenericClass)) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by " + BindDirective.class.getName()
                    + " and therefore must take class, not array");
            return;
        }

        NestedDirective nestedDirective = new NestedDirective();
        nestedDirective.multiple = multiple;
        nestedDirective.metadata = parseElement((GenericClass) type, binding, false);
        nestedDirective.setter = method;
        nestedDirective.required = method.getDescriber().getAnnotation(OptionalBinding.class.getName()) == null;
        metadata.nestedDirectives.add(nestedDirective);
    }

    private GenericMethod replaceWildcards(GenericMethod method) {
        ValueType[] args = Arrays.stream(method.getActualArgumentTypes())
                .map(this::replaceWildcards)
                .toArray(sz -> new ValueType[sz]);
        ValueType returnType = method.getActualReturnType();
        if (returnType != null) {
            returnType = replaceWildcards(returnType);
        }
        return new GenericMethod(method.getDescriber(), method.getActualOwner(), args, returnType);
    }

    private ValueType replaceWildcards(ValueType type) {
        if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass) type;
            List<GenericType> args = cls.getArguments().stream()
                    .map(this::replaceWildcards)
                    .map(arg -> (GenericType) arg)
                    .collect(Collectors.toList());
            return new GenericClass(cls.getName(), args);
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray) type;
            return new GenericArray(replaceWildcards(array.getElementType()));
        } else if (type instanceof GenericWildcard) {
            GenericWildcard wildcard = (GenericWildcard) type;
            TypeVar var = new TypeVar();
            if (!wildcard.getLowerBound().isEmpty()) {
                GenericType[] bounds = var.getLowerBound().stream()
                        .map(this::replaceWildcards)
                        .map(arg -> (GenericType) arg)
                        .toArray(sz -> new GenericType[sz]);
                var.withLowerBound(bounds);
            } else if (!wildcard.getUpperBound().isEmpty()) {
                GenericType[] bounds = var.getUpperBound().stream()
                        .map(this::replaceWildcards)
                        .map(arg -> (GenericType) arg)
                        .toArray(sz -> new GenericType[sz]);
                var.withUpperBound(bounds);
            }
            return new GenericReference(var);
        } else {
            return type;
        }
    }

    private boolean parseAttributeType(DirectiveAttributeMetadata attrMetadata, ValueType valueType,
            ValueType returnType) {
        if (valueType == null) {
            attrMetadata.type = DirectiveAttributeType.VARIABLE;
            attrMetadata.valueType = returnType;
            return true;
        }
        if (valueType instanceof GenericClass) {
            GenericMethod sam = typeNavigator.findSingleAbstractMethod((GenericClass) valueType);
            if (sam != null) {
                attrMetadata.sam = sam;
                attrMetadata.type = DirectiveAttributeType.FUNCTION;
                attrMetadata.valueType = sam.getActualOwner();
                return true;
            }
        }

        return false;
    }

    private void parseBindName(BaseDirectiveMetadata directive, GenericMethod method) {
        AnnotationDescriber annot = method.getDescriber().getAnnotation(BindDirectiveName.class.getName());
        if (annot == null) {
            return;
        }

        if (directive.nameSetter != null) {
            error("Method " + methodToString(method.getDescriber()) + " declares binding to annotation name "
                    + "that is already bound to " + methodToString(directive.nameSetter));
            return;
        }

        ValueType[] args = method.getActualArgumentTypes();
        if (args.length != 1) {
            error("Method " + methodToString(method.getDescriber()) + " is marked by "
                    + BindDirectiveName.class.getName() + " and therefore must take exactly 1 argument, but "
                    + "takes " + args.length);
            return;
        }

        if (!args[0].equals(new GenericClass(String.class.getName()))) {
            error("Method " + methodToString(method.getDescriber()) + " takes argument of type that can't be "
                    + "mapped to directive's name: " + args[0]);
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

        MethodWithParams(String name, ValueType[] params) {
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
            MethodWithParams other = (MethodWithParams) obj;
            return name.equals(other.name) && Arrays.equals(params, other.params);
        }
    }
}
