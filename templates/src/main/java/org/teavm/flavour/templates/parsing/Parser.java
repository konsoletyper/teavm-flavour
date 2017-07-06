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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.htmlparser.jericho.Attribute;
import net.htmlparser.jericho.CharacterReference;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.Segment;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StartTagType;
import net.htmlparser.jericho.Tag;
import org.apache.commons.lang3.StringUtils;
import org.teavm.flavour.expr.ClassResolver;
import org.teavm.flavour.expr.Compiler;
import org.teavm.flavour.expr.CompilerCommons;
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.ImportingClassResolver;
import org.teavm.flavour.expr.Location;
import org.teavm.flavour.expr.Scope;
import org.teavm.flavour.expr.TypeEstimator;
import org.teavm.flavour.expr.TypedPlan;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.ast.LambdaExpr;
import org.teavm.flavour.expr.ast.ObjectEntry;
import org.teavm.flavour.expr.ast.ObjectExpr;
import org.teavm.flavour.expr.plan.LambdaPlan;
import org.teavm.flavour.expr.plan.ObjectPlan;
import org.teavm.flavour.expr.plan.ObjectPlanEntry;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericMethod;
import org.teavm.flavour.expr.type.GenericReference;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.MapSubstitutions;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.TypeVar;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.MethodDescriber;
import org.teavm.flavour.templates.OptionalBinding;
import org.teavm.flavour.templates.SettingsObject;
import org.teavm.flavour.templates.tree.AttributeComponentBinding;
import org.teavm.flavour.templates.tree.ComponentBinding;
import org.teavm.flavour.templates.tree.ComponentFunctionBinding;
import org.teavm.flavour.templates.tree.ComponentVariableBinding;
import org.teavm.flavour.templates.tree.DOMElement;
import org.teavm.flavour.templates.tree.DOMText;
import org.teavm.flavour.templates.tree.NestedComponentBinding;
import org.teavm.flavour.templates.tree.TemplateNode;

public class Parser {
    private ClassDescriberRepository classRepository;
    private ImportingClassResolver classResolver;
    private ResourceProvider resourceProvider;
    private GenericTypeNavigator typeNavigator;
    private Map<String, List<ElementComponentMetadata>> avaliableComponents = new HashMap<>();
    private Map<String, List<AttributeComponentMetadata>> avaliableAttrComponents = new HashMap<>();
    private Map<String, ElementComponentMetadata> components = new HashMap<>();
    private Map<String, AttributeComponentMetadata> attrComponents = new HashMap<>();
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private Map<String, Deque<ValueType>> variables = new HashMap<>();
    private Source source;
    private int position;

    public Parser(ClassDescriberRepository classRepository, ClassResolver classResolver,
            ResourceProvider resourceProvider) {
        this.classRepository = classRepository;
        this.classResolver = new ImportingClassResolver(classResolver);
        this.resourceProvider = resourceProvider;
        this.classResolver.importPackage("java.lang");
        this.typeNavigator = new GenericTypeNavigator(classRepository);
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public boolean wasSuccessful() {
        return diagnostics.isEmpty();
    }

    public List<TemplateNode> parse(Reader reader, String className) throws IOException {
        source = new Source(reader);
        use(source, "std", "org.teavm.flavour.components.standard");
        use(source, "event", "org.teavm.flavour.components.events");
        use(source, "attr", "org.teavm.flavour.components.attributes");
        use(source, "html", "org.teavm.flavour.components.html");
        pushVar("this", new GenericClass(className));
        position = source.getBegin();
        List<TemplateNode> nodes = new ArrayList<>();
        parseSegment(source.getEnd(), nodes, elem -> true);
        popVar("this");
        source = null;
        return nodes;
    }

    private void parseSegment(int limit, List<TemplateNode> result, Predicate<Element> filter) {
        while (position < limit) {
            Tag tag = source.getNextTag(position);
            if (tag == null || tag.getBegin() > limit) {
                if (position < limit) {
                    DOMText text = new DOMText(parseText(position, limit));
                    text.setLocation(new Location(position, limit));
                    result.add(text);
                }
                position = limit;
                break;
            }

            if (position < tag.getBegin()) {
                DOMText text = new DOMText(parseText(position, tag.getBegin()));
                text.setLocation(new Location(position, tag.getBegin()));
                result.add(text);
            }
            position = tag.getEnd();
            parseTag(tag, result, filter);
        }
    }

    private String parseText(int start, int end) {
        StringBuilder sb = new StringBuilder();
        while (start < end) {
            CharacterReference ref = source.getNextCharacterReference(start);
            if (ref == null || ref.getBegin() >= end) {
                break;
            }
            sb.append(source.subSequence(start, ref.getBegin()));
            sb.append(ref.getChar());
            start = ref.getEnd();
        }
        sb.append(source.subSequence(start, end));
        return sb.toString();
    }

    private void parseTag(Tag tag, List<TemplateNode> result, Predicate<Element> filter) {
        if (tag instanceof StartTag) {
            StartTag startTag = (StartTag) tag;
            if (startTag.getStartTagType() == StartTagType.XML_PROCESSING_INSTRUCTION) {
                parseProcessingInstruction(startTag);
            } else if (startTag.getStartTagType() == StartTagType.NORMAL) {
                if (filter.test(tag.getElement())) {
                    TemplateNode node = parseElement(tag.getElement());
                    if (node != null) {
                        result.add(node);
                    }
                } else {
                    position = tag.getElement().getEnd();
                }
            }
        }
    }

    private TemplateNode parseElement(Element elem) {
        if (elem.getName().indexOf(':') > 0) {
            return parseComponent(elem);
        } else {
            return parseDomElement(elem);
        }
    }

    private TemplateNode parseDomElement(Element elem) {
        DOMElement templateElem = new DOMElement(elem.getName());
        templateElem.setLocation(new Location(elem.getBegin(), elem.getEnd()));
        for (int i = 0; i < elem.getAttributes().size(); ++i) {
            Attribute attr = elem.getAttributes().get(i);
            if (attr.getName().indexOf(':') > 0) {
                AttributeComponentBinding component = parseAttributeComponent(attr);
                if (component != null) {
                    templateElem.getAttributeComponents().add(component);
                }
            } else {
                templateElem.setAttribute(attr.getName(), attr.getValue(),
                        new Location(attr.getBegin(), attr.getEnd()));
            }
        }

        Set<String> vars = new HashSet<>();
        for (AttributeComponentBinding attrComponent : templateElem.getAttributeComponents()) {
            for (ComponentVariableBinding var : attrComponent.getVariables()) {
                vars.add(var.getName());
                pushVar(var.getName(), var.getValueType());
            }
        }

        parseSegment(elem.getEnd(), templateElem.getChildNodes(), child -> true);

        for (String var : vars) {
            popVar(var);
        }

        return templateElem;
    }

    private TemplateNode parseComponent(Element elem) {
        int prefixLength = elem.getName().indexOf(':');
        String prefix = elem.getName().substring(0, prefixLength);
        String name = elem.getName().substring(prefixLength + 1);
        String fullName = prefix + ":" + name;
        ElementComponentMetadata componentMeta = resolveComponent(prefix, name);
        if (componentMeta == null) {
            error(elem.getStartTag().getNameSegment(), "Undefined component " + fullName);
            return null;
        }

        List<PostponedComponentParse> postponedList = new ArrayList<>();
        TemplateNode node = parseComponent(componentMeta, prefix, name, elem, postponedList,
                new MapSubstitutions(new HashMap<>()));
        completeComponentParsing(postponedList, componentMeta, elem);
        position = elem.getEnd();
        return node;
    }

    private ComponentBinding parseComponent(ElementComponentMetadata componentMeta, String prefix, String name,
            Element elem, List<PostponedComponentParse> postponed, MapSubstitutions typeVars) {
        ComponentBinding component = new ComponentBinding(componentMeta.cls.getName(), name);
        component.setLocation(new Location(elem.getBegin(), elem.getEnd()));
        if (componentMeta.nameSetter != null) {
            component.setElementNameMethodName(componentMeta.nameSetter.getName());
        }

        List<GenericType> typeVarsBackup = new ArrayList<>();
        Map<TypeVar, TypeVar> freshVarsMap = new HashMap<>();
        for (TypeVar typeVar : componentMeta.typeVarsToRefresh) {
            TypeVar freshVar = new TypeVar();
            freshVarsMap.put(typeVar, freshVar);
            typeVarsBackup.add(typeVars.getMap().put(typeVar, new GenericReference(freshVar)));
        }

        for (TypeVar typeVar : componentMeta.typeVarsToRefresh) {
            TypeVar freshVar = freshVarsMap.get(typeVar);
            if (!typeVar.getLowerBound().isEmpty()) {
                freshVar.withLowerBound(typeVar.getLowerBound().stream()
                        .map(bound -> bound.substitute(typeVars))
                        .toArray(GenericType[]::new));
            } else {
                freshVar.withUpperBound(typeVar.getUpperBound().stream()
                        .map(bound -> bound.substitute(typeVars))
                        .toArray(GenericType[]::new));
            }
        }

        List<PostponedAttributeParse> attributesParse = new ArrayList<>();
        for (ComponentAttributeMetadata attrMeta : componentMeta.attributes.values()) {
            Attribute attr = elem.getAttributes().get(attrMeta.name);
            if (attr == null) {
                if (attrMeta.required) {
                    error(elem.getStartTag(), "Missing required attribute: " + attrMeta.name);
                }
                continue;
            }
            if (attrMeta.type == null || attrMeta.valueType == null) {
                continue;
            }
            PostponedAttributeParse attrParse = new PostponedAttributeParse();
            attrParse.meta = attrMeta;
            attrParse.node = attr;
            if (attrMeta.type == ComponentAttributeType.FUNCTION
                    || attrMeta.type == ComponentAttributeType.BIDIRECTIONAL) {
                if (attrMeta.type == ComponentAttributeType.FUNCTION && isSettingsObject(attrMeta.valueType)) {
                    attrParse.objectExpr = parseObject(attr.getValueSegment());
                    attrParse.type = attrMeta.valueType;
                } else {
                    attrParse.expr = parseExpr(attr.getValueSegment());
                }
            }

            if (attrParse.meta.valueType != null) {
                attrParse.type = attrParse.meta.valueType;
                if (attrParse.type instanceof GenericType) {
                    attrParse.type = ((GenericType) attrParse.type).substitute(typeVars);
                }
            }
            if (attrParse.meta.sam != null) {
                attrParse.sam = attrParse.meta.sam.substitute(typeVars);
            }
            if (attrParse.meta.altSam != null) {
                attrParse.altSam = attrParse.meta.altSam.substitute(typeVars);
            }
            attributesParse.add(attrParse);
        }

        for (Attribute attr : elem.getAttributes()) {
            if (!componentMeta.attributes.containsKey(attr.getName())) {
                error(attr, "Unknown attribute " + attr.getName() + " for component " + prefix + ":" + name);
            }
        }

        PostponedComponentParse componentParse = new PostponedComponentParse(position, componentMeta, component, elem);
        componentParse.attributes.addAll(attributesParse);
        componentParse.freshTypeVars.addAll(freshVarsMap.values());
        postponed.add(componentParse);

        parseSegment(elem.getEnd(), new ArrayList<>(), child -> {
            int nestedPrefixLength = child.getName().indexOf(':');
            if (nestedPrefixLength > 0) {
                String nestedPrefix = child.getName().substring(0, nestedPrefixLength);
                String nestedName = child.getName().substring(nestedPrefixLength + 1);
                if (nestedPrefix.equals(prefix)) {
                    NestedComponent nested = resolveNestedComponent(componentMeta, nestedName);
                    if (nested != null) {
                        ComponentBinding nestedNode = parseComponent(nested.metadata, prefix, nestedName, child,
                                postponed, typeVars);
                        NestedComponentBinding binding = getNestedComponentBinding(component, nested);
                        binding.getComponents().add(nestedNode);
                    }
                }
            }
            return false;
        });
        validateNestedComponents(component, componentMeta, elem, prefix);

        for (int i = 0; i < componentMeta.typeVarsToRefresh.size(); i++) {
            TypeVar typeVar = componentMeta.typeVarsToRefresh.get(i);
            if (typeVarsBackup.get(i) != null) {
                typeVars.getMap().put(typeVar, typeVarsBackup.get(i));
            } else {
                typeVars.getMap().remove(typeVar);
            }
        }

        return component;
    }

    private boolean isSettingsObject(ValueType type) {
        if (!(type instanceof GenericClass)) {
            return false;
        }
        GenericMethod sam = typeNavigator.findSingleAbstractMethod((GenericClass) type);
        if (sam == null) {
            return false;
        }

        type = sam.getActualReturnType();
        if (type instanceof GenericClass) {
            String className = ((GenericClass) type).getName();
            ClassDescriber cls = classRepository.describe(className);
            return cls.getAnnotation(SettingsObject.class.getName()) != null;
        } else {
            return false;
        }
    }

    private void completeComponentParsing(List<PostponedComponentParse> postponed,
            BaseComponentMetadata componentMetadata, Segment segment) {
        // Attempting to infer values for component's type parameters
        TypeInference inference = new TypeInference(typeNavigator);
        inference.addVariables(Arrays.asList(componentMetadata.cls.getTypeVariables()));

        TypeEstimator estimator = new TypeEstimator(inference, classResolver, typeNavigator, new TemplateScope());
        boolean inferenceFailed = false;
        for (PostponedComponentParse parse : postponed) {
            inference.addVariables(parse.freshTypeVars);
            for (PostponedAttributeParse attrParse : parse.attributes) {
                if (attrParse.expr != null && attrParse.meta.type == ComponentAttributeType.FUNCTION) {
                    if (attrParse.expr instanceof LambdaExpr) {
                        attrParse.typeEstimate = estimator.estimateLambda((LambdaExpr) attrParse.expr, attrParse.sam);
                    } else {
                        ValueType expectedType = attrParse.sam.getActualReturnType();
                        attrParse.typeEstimate = estimator.estimate(attrParse.expr, expectedType);
                    }
                    if (attrParse.typeEstimate != null && !inferenceFailed) {
                        if (!inference.subtypeConstraint(attrParse.typeEstimate, attrParse.sam.getActualReturnType())) {
                            inferenceFailed = true;
                        }
                    }
                }
            }
        }

        if (!inference.resolve()) {
            error(segment, "Could not infer component type");
            inferenceFailed = true;
        }

        if (inferenceFailed) {
            inference = new TypeInference(typeNavigator);
            inference.addVariables(Arrays.asList(componentMetadata.cls.getTypeVariables()));
        }

        // Process functions
        TypeInference varInference = new TypeInference(typeNavigator);
        varInference.addVariables(Arrays.asList(componentMetadata.cls.getTypeVariables()));
        for (PostponedComponentParse parse : postponed) {
            varInference.addVariables(parse.freshTypeVars);

            for (PostponedAttributeParse attrParse : parse.attributes) {
                if (attrParse.expr == null && attrParse.objectExpr == null) {
                    continue;
                }
                MethodDescriber setter = attrParse.meta.setter;
                GenericType type = attrParse.sam.getActualOwner().substitute(inference.getSubstitutions());
                TypedPlan plan = attrParse.expr != null
                        ? compileExpr(attrParse.node.getValueSegment(), attrParse.expr, (GenericClass) type)
                        : compileSettingsObject(attrParse.node.getValueSegment(), attrParse.objectExpr,
                                (GenericClass) type);
                if (plan == null) {
                    continue;
                }
                ComponentFunctionBinding computationBinding = new ComponentFunctionBinding(
                        setter.getOwner().getName(), setter.getName(), (LambdaPlan) plan.getPlan(),
                        attrParse.sam.getActualOwner().getName());
                parse.component.getComputations().add(computationBinding);
                if (!varInference.equalConstraint(plan.getType(), attrParse.sam.getActualOwner())) {
                    inferenceFailed = true;
                }

                if (attrParse.meta.type == ComponentAttributeType.BIDIRECTIONAL) {
                    setter = attrParse.meta.altSetter;
                    type = attrParse.altSam.getActualOwner().substitute(inference.getSubstitutions());
                    plan = compileExpr(attrParse.node.getValueSegment(), attrParse.expr, (GenericClass) type);
                    computationBinding = new ComponentFunctionBinding(
                            setter.getOwner().getName(), setter.getName(), (LambdaPlan) plan.getPlan(),
                            attrParse.altSam.getActualOwner().getName());
                    parse.component.getComputations().add(computationBinding);
                }
            }
        }

        if (!varInference.resolve() && !inferenceFailed) {
            error(segment, "Could not infer component type");
        }

        // Process variables
        Map<String, ValueType> declaredVars = new HashMap<>();
        for (PostponedComponentParse parse : postponed) {
            for (PostponedAttributeParse attrParse : parse.attributes) {
                if (attrParse.meta.type != ComponentAttributeType.VARIABLE) {
                    continue;
                }
                MethodDescriber getter = attrParse.meta.getter;
                String varName = attrParse.node.getValue();
                ValueType type = attrParse.type;
                if (type instanceof GenericType) {
                    type = ((GenericType) type).substitute(varInference.getSubstitutions());
                }
                if (declaredVars.containsKey(varName)) {
                    error(attrParse.node.getValueSegment(), "Variable " + varName + " is already used by "
                            + "the same component");
                } else {
                    declaredVars.put(varName, type);
                    pushVar(varName, type);
                }
                ComponentVariableBinding varBinding = new ComponentVariableBinding(
                        getter.getOwner().getName(), getter.getName(), varName, getter.getRawReturnType(), type);
                parse.component.getVariables().add(varBinding);
            }
        }

        // Process bodies
        Set<Element> elementsToSkip = postponed.stream().map(parse -> parse.elem).collect(Collectors.toSet());
        for (PostponedComponentParse parse : postponed) {
            position = parse.position;
            parseSegment(parse.elem.getEnd(), parse.component.getContentNodes(),
                    child -> !elementsToSkip.contains(child));

            if (parse.metadata.contentSetter != null) {
                parse.component.setContentMethodName(parse.metadata.contentSetter.getName());
            } else {
                if (!parse.metadata.ignoreContent && !isEmptyContent(parse.component.getContentNodes())) {
                    error(parse.elem, "Component " + parse.metadata.cls.getName() + " should not have any content");
                }
                parse.component.getContentNodes().clear();
            }
        }

        for (String varName : declaredVars.keySet()) {
            popVar(varName);
        }
    }

    static class PostponedComponentParse {
        int position;
        ElementComponentMetadata metadata;
        ComponentBinding component;
        Element elem;
        List<PostponedAttributeParse> attributes = new ArrayList<>();
        List<TypeVar> freshTypeVars = new ArrayList<>();

        PostponedComponentParse(int position, ElementComponentMetadata metadata, ComponentBinding component,
                Element elem) {
            this.position = position;
            this.metadata = metadata;
            this.component = component;
            this.elem = elem;
        }
    }

    static class PostponedAttributeParse {
        ComponentAttributeMetadata meta;
        Attribute node;
        Expr expr;
        ObjectExpr objectExpr;
        ValueType type;
        ValueType typeEstimate;
        GenericMethod sam;
        GenericMethod altSam;
    }

    private void validateNestedComponents(ComponentBinding component, ElementComponentMetadata metadata,
            Element elem, String prefix) {
        for (NestedComponent nestedMetadata : metadata.nestedComponents) {
            NestedComponentBinding nestedComponent = findNestedComponentBinding(component, nestedMetadata);

            String[] nameRules = nestedMetadata.metadata.nameRules;
            String name = nameRules.length == 1
                    ? nameRules[0]
                    : "{" + Arrays.stream(nameRules).collect(Collectors.joining("|")) + "}";

            if (nestedMetadata.required) {
                if (nestedComponent == null) {
                    error(elem, "Nested component " + prefix + ":" + name + " required but none encountered");
                }
            } else if (!nestedMetadata.multiple) {
                if (nestedComponent.getComponents().size() > 1) {
                    error(elem, "Nested component " + prefix + ":" + name + " should encounter only once");
                }
            }
        }
    }

    private NestedComponentBinding getNestedComponentBinding(ComponentBinding component, NestedComponent metadata) {
        NestedComponentBinding binding = findNestedComponentBinding(component, metadata);
        if (binding == null) {
            binding = new NestedComponentBinding(metadata.setter.getDescriber().getOwner().getName(),
                    metadata.setter.getDescriber().getName(),
                    metadata.metadata.cls.getName(), metadata.multiple);
            component.getNestedComponents().add(binding);
        }
        return binding;
    }

    private NestedComponentBinding findNestedComponentBinding(ComponentBinding component, NestedComponent metadata) {
        for (NestedComponentBinding binding : component.getNestedComponents()) {
            if (binding.getMethodName().equals(metadata.setter.getDescriber().getName())
                    && binding.getMethodOwner().equals(metadata.setter.getDescriber().getOwner().getName())) {
                return binding;
            }
        }
        return null;
    }

    private boolean isEmptyContent(List<TemplateNode> nodes) {
        for (TemplateNode node : nodes) {
            if (node instanceof DOMText) {
                DOMText text = (DOMText) node;
                if (!isEmptyText(text.getValue())) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean isEmptyText(String text) {
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && c != '\r' && c != '\n' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private AttributeComponentBinding parseAttributeComponent(Attribute attr) {
        int prefixLength = attr.getName().indexOf(':');
        String prefix = attr.getName().substring(0, prefixLength);
        String name = attr.getName().substring(prefixLength + 1);
        String fullName = prefix + ":" + name;
        AttributeComponentMetadata componentMeta = resolveAttrComponent(prefix, name);
        if (componentMeta == null) {
            error(attr.getNameSegment(), "Undefined component " + fullName);
            return null;
        }

        AttributeComponentBinding component = new AttributeComponentBinding(componentMeta.cls.getName(), name);
        component.setLocation(new Location(attr.getBegin(), attr.getEnd()));
        if (componentMeta.nameSetter != null) {
            component.setElementNameMethodName(componentMeta.nameSetter.getName());
        }

        MethodDescriber getter = componentMeta.getter;
        MethodDescriber setter = componentMeta.setter;
        switch (componentMeta.type) {
            case VARIABLE: {
                String varName = attr.getValue();
                ComponentVariableBinding varBinding = new ComponentVariableBinding(setter.getOwner().getName(),
                        getter.getName(), varName, getter.getRawReturnType(), componentMeta.valueType);
                component.getVariables().add(varBinding);
                break;
            }
            case FUNCTION: {
                TypeInference inference = new TypeInference(typeNavigator);
                inference.addVariables(Arrays.asList(componentMeta.cls.getTypeVariables()));
                TypeEstimator estimator = new TypeEstimator(inference, classResolver, typeNavigator,
                        new TemplateScope());

                TypedPlan plan;
                if (isSettingsObject(componentMeta.valueType)) {
                    ObjectExpr expr = parseObject(attr.getValueSegment());
                    if (expr == null) {
                        break;
                    }
                    plan = compileSettingsObject(attr.getValueSegment(), expr, componentMeta.sam.getActualOwner());
                    if (plan == null) {
                        break;
                    }
                } else {
                    Expr expr = parseExpr(attr.getValueSegment());
                    if (expr == null) {
                        break;
                    }

                    if (expr instanceof LambdaExpr) {
                        estimator.estimateLambda((LambdaExpr) expr, componentMeta.sam);
                    } else {
                        estimator.estimate(expr, componentMeta.sam.getActualReturnType());
                    }

                    if (!inference.resolve()) {
                        error(attr.getValueSegment(), "Could not infer type");
                        return component;
                    }

                    plan = compileExpr(attr.getValueSegment(), expr,
                            componentMeta.sam.getActualOwner().substitute(inference.getSubstitutions()));
                    if (plan == null) {
                        break;
                    }
                }
                ComponentFunctionBinding functionBinding = new ComponentFunctionBinding(setter.getOwner().getName(),
                        setter.getName(), (LambdaPlan) plan.getPlan(),
                        componentMeta.sam.getDescriber().getOwner().getName());
                component.getFunctions().add(functionBinding);
                break;
            }
            case BIDIRECTIONAL: {
                diagnostics.add(new Diagnostic(attr.getBegin(), attr.getEnd(), "Bidirectional attributes "
                        + "are not supported yet"));
                break;
            }
        }

        return component;
    }

    private Expr parseExpr(Segment segment) {
        boolean hasErrors = false;
        org.teavm.flavour.expr.Parser exprParser = new org.teavm.flavour.expr.Parser(classResolver);
        Expr expr = exprParser.parse(segment.toString());
        int offset = segment.getBegin();
        for (Diagnostic diagnostic : exprParser.getDiagnostics()) {
            diagnostic = new Diagnostic(offset + diagnostic.getStart(), offset + diagnostic.getEnd(),
                    diagnostic.getMessage());
            diagnostics.add(diagnostic);
            hasErrors = true;
        }
        if (hasErrors) {
            return null;
        }
        return expr;
    }

    private ObjectExpr parseObject(Segment segment) {
        boolean hasErrors = false;
        org.teavm.flavour.expr.Parser exprParser = new org.teavm.flavour.expr.Parser(classResolver);
        ObjectExpr expr = exprParser.parseObject(segment.toString());
        int offset = segment.getBegin();
        for (Diagnostic diagnostic : exprParser.getDiagnostics()) {
            diagnostic = new Diagnostic(offset + diagnostic.getStart(), offset + diagnostic.getEnd(),
                    diagnostic.getMessage());
            diagnostics.add(diagnostic);
            hasErrors = true;
        }
        if (hasErrors) {
            return null;
        }
        return expr;
    }

    private TypedPlan compileExpr(Segment segment, Expr expr, GenericClass type) {
        boolean hasErrors = false;
        Compiler compiler = new Compiler(classRepository, classResolver, new TemplateScope());
        TypedPlan result = compiler.compileLambda(expr, type);
        PlanOffsetVisitor offsetVisitor = new PlanOffsetVisitor(segment.getBegin());
        result.getPlan().acceptVisitor(offsetVisitor);
        int offset = segment.getBegin();
        for (Diagnostic diagnostic : compiler.getDiagnostics()) {
            diagnostic = new Diagnostic(offset + diagnostic.getStart(), offset + diagnostic.getEnd(),
                    diagnostic.getMessage());
            diagnostics.add(diagnostic);
            hasErrors = true;
        }

        if (hasErrors) {
            return null;
        }
        return result;
    }

    private TypedPlan compileSettingsObject(Segment segment, ObjectExpr expr, GenericClass type) {
        boolean hasErrors = false;
        Compiler compiler = new Compiler(classRepository, classResolver, new TemplateScope());

        GenericMethod sam = typeNavigator.findSingleAbstractMethod(type);
        if (sam.getActualParameterTypes().length != 0 || !(sam.getActualReturnType() instanceof GenericClass)) {
            diagnostics.add(new Diagnostic(segment.getBegin(), segment.getEnd(), "Wrong target lambda type"));
            return null;
        }

        GenericClass objectType = (GenericClass) sam.getActualReturnType();
        ObjectPlan objectPlan = new ObjectPlan(objectType.getName());
        Set<String> requiredFields = collectRequiredFields(objectType.getName());
        for (ObjectEntry entry : expr.getEntries()) {
            GenericMethod setter = findSetter(segment, objectType, entry.getKey());
            if (setter != null) {
                requiredFields.remove(entry.getKey());
                TypedPlan valuePlan = compiler.compile(entry.getValue(), setter.getActualParameterTypes()[0]);
                ObjectPlanEntry planEntry = new ObjectPlanEntry(setter.getDescriber().getName(),
                        CompilerCommons.methodToDesc(setter.getDescriber()), valuePlan.getPlan());
                objectPlan.getEntries().add(planEntry);
            }
        }

        LambdaPlan plan = new LambdaPlan(objectPlan, type.getName(), sam.getDescriber().getName(),
                CompilerCommons.methodToDesc(sam.getDescriber()), Collections.emptyList());

        TypedPlan result = new TypedPlan(plan, type);

        if (!requiredFields.isEmpty()) {
            diagnostics.add(new Diagnostic(segment.getBegin(), segment.getEnd(), "Required field not set: "
                    + requiredFields.iterator().next()));
        }

        PlanOffsetVisitor offsetVisitor = new PlanOffsetVisitor(segment.getBegin());
        plan.acceptVisitor(offsetVisitor);
        int offset = segment.getBegin();
        for (Diagnostic diagnostic : compiler.getDiagnostics()) {
            diagnostic = new Diagnostic(offset + diagnostic.getStart(), offset + diagnostic.getEnd(),
                    diagnostic.getMessage());
            diagnostics.add(diagnostic);
            hasErrors = true;
        }

        if (hasErrors) {
            return null;
        }
        return result;
    }

    private Set<String> collectRequiredFields(String className) {
        ClassDescriber cls = classRepository.describe(className);
        Set<String> fields = new LinkedHashSet<>();
        for (MethodDescriber method : cls.getMethods()) {
            if (method.getName().startsWith("set") && method.getName().length() > 3
                    && Character.isUpperCase(method.getName().charAt(3))
                    && method.getParameterTypes().length == 1) {
                if (method.getAnnotation(OptionalBinding.class.getName()) == null) {
                    char firstChar = Character.toLowerCase(method.getName().charAt(3));
                    fields.add(firstChar + method.getName().substring(4));
                }
            }
        }
        return fields;
    }

    private GenericMethod findSetter(Segment segment, GenericClass cls, String name) {
        String methodName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        GenericMethod[] candidates = typeNavigator.findMethods(cls, methodName, 1);
        if (candidates.length == 0) {
            diagnostics.add(new Diagnostic(segment.getBegin(), segment.getEnd(), "Setter not found for key: "
                    + name));
            return null;
        } else if (candidates.length > 1) {
            diagnostics.add(new Diagnostic(segment.getBegin(), segment.getEnd(), "Ambiguous key: "  + name));
            return null;
        } else {
            return candidates[0];
        }
    }

    private void pushVar(String name, ValueType type) {
        Deque<ValueType> stack = variables.computeIfAbsent(name, k -> new ArrayDeque<>());
        stack.push(type);
    }

    private void popVar(String name) {
        Deque<ValueType> stack = variables.get(name);
        if (stack != null) {
            stack.pop();
            if (stack.isEmpty()) {
                variables.remove(name);
            }
        }
    }

    class TemplateScope implements Scope {
        @Override
        public ValueType variableType(String variableName) {
            Deque<ValueType> stack = variables.get(variableName);
            return stack != null && !stack.isEmpty() ? stack.peek() : null;
        }
    }

    private ElementComponentMetadata resolveComponent(String prefix, String name) {
        String fullName = prefix + ":" + name;
        ElementComponentMetadata component = components.get(fullName);
        if (component == null) {
            List<ElementComponentMetadata> byPrefix = avaliableComponents.get(prefix);
            if (byPrefix != null) {
                component: for (ElementComponentMetadata testComponent : byPrefix) {
                    for (String rule : testComponent.nameRules) {
                        if (matchRule(rule, name)) {
                            component = testComponent;
                            break component;
                        }
                    }
                }
            }
            components.put(fullName, component);
        }
        return component;
    }

    private NestedComponent resolveNestedComponent(ElementComponentMetadata outer, String name) {
        for (NestedComponent nested : outer.nestedComponents) {
            if (Arrays.stream(nested.metadata.nameRules).anyMatch(rule -> matchRule(rule, name))) {
                return nested;
            }
        }
        return null;
    }

    private AttributeComponentMetadata resolveAttrComponent(String prefix, String name) {
        String fullName = prefix + ":" + name;
        AttributeComponentMetadata component = attrComponents.get(fullName);
        if (component == null) {
            List<AttributeComponentMetadata> byPrefix = avaliableAttrComponents.get(prefix);
            if (byPrefix != null) {
                component: for (AttributeComponentMetadata testComponent : byPrefix) {
                    for (String rule : testComponent.nameRules) {
                        if (matchRule(rule, name)) {
                            component = testComponent;
                            break component;
                        }
                    }
                }
            }
            attrComponents.put(fullName, component);
        }
        return component;
    }

    private boolean matchRule(String rule, String name) {
        int index = rule.indexOf('*');
        if (index < 0) {
            return name.equals(rule);
        }
        String prefix = rule.substring(0, index);
        String suffix = rule.substring(index + 1);
        return name.startsWith(prefix) && name.endsWith(suffix) && prefix.length() + suffix.length() < name.length();
    }

    private void parseProcessingInstruction(StartTag tag) {
        if (tag.getName().equals("?import")) {
            parseImport(tag);
        } else if (tag.getName().equals("?use")) {
            parseUse(tag);
        }
    }

    private void parseImport(StartTag tag) {
        String importedName = normalizeQualifiedName(tag.getTagContent().toString());
        if (importedName.endsWith(".*")) {
            classResolver.importPackage(importedName);
        } else {
            if (classResolver.findClass(importedName) == null) {
                error(tag.getTagContent(), "Class was not found: " + importedName);
            } else {
                classResolver.importClass(importedName);
            }
        }
    }

    private void parseUse(StartTag tag) {
        String content = tag.getTagContent().toString();
        String[] parts = StringUtils.split(content, ":", 2);
        if (parts.length != 2) {
            error(tag.getTagContent(), "Illegal syntax for 'use' instruction");
            return;
        }

        String prefix = parts[0].trim();
        String packageName = normalizeQualifiedName(parts[1]);
        use(tag.getTagContent(), prefix, packageName);
    }

    private void use(Segment segment, String prefix, String packageName) {
        String resourceName = "META-INF/flavour/component-packages/" + packageName;
        try (InputStream input = resourceProvider.openResource(resourceName)) {
            if (input == null) {
                error(segment, "Component package was not found: " + packageName);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            List<ElementComponentMetadata> componentList = new ArrayList<>();
            List<AttributeComponentMetadata> attributeComponentList = new ArrayList<>();
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String className = packageName + "." + line;

                ClassDescriber cls = classRepository.describe(className);
                if (cls == null) {
                    error(segment, "Class " + className + " declared by component package was not found");
                    continue;
                }

                ComponentParser componentParser = new ComponentParser(classRepository, diagnostics, segment);
                Object componentMetadata = componentParser.parse(cls);
                if (componentMetadata instanceof ElementComponentMetadata) {
                    ElementComponentMetadata elemComponentMeta = (ElementComponentMetadata) componentMetadata;
                    componentList.add(elemComponentMeta);
                } else if (componentMetadata instanceof AttributeComponentMetadata) {
                    AttributeComponentMetadata attrComponentMeta = (AttributeComponentMetadata) componentMetadata;
                    attributeComponentList.add(attrComponentMeta);
                }
            }
            avaliableComponents.put(prefix, componentList);
            avaliableAttrComponents.put(prefix, attributeComponentList);
        } catch (IOException e) {
            throw new RuntimeException("IO exception occurred parsing HTML input", e);
        }
    }

    private String normalizeQualifiedName(String text) {
        String[] parts = StringUtils.split(text.trim(), '.');
        for (int i = 0; i < parts.length; ++i) {
            parts[i] = parts[i].trim();
        }
        return StringUtils.join(parts, '.');
    }

    private void error(Segment segment, String message) {
        diagnostics.add(new Diagnostic(segment.getBegin(), segment.getEnd(), message));
    }
}
