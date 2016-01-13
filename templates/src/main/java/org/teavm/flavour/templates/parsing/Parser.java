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
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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
import org.teavm.flavour.expr.Diagnostic;
import org.teavm.flavour.expr.ImportingClassResolver;
import org.teavm.flavour.expr.Location;
import org.teavm.flavour.expr.Scope;
import org.teavm.flavour.expr.TypedPlan;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.plan.LambdaPlan;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.TypeUnifier;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.ClassDescriber;
import org.teavm.flavour.expr.type.meta.ClassDescriberRepository;
import org.teavm.flavour.expr.type.meta.MethodDescriber;
import org.teavm.flavour.templates.tree.AttributeDirectiveBinding;
import org.teavm.flavour.templates.tree.DOMElement;
import org.teavm.flavour.templates.tree.DOMText;
import org.teavm.flavour.templates.tree.DirectiveBinding;
import org.teavm.flavour.templates.tree.DirectiveFunctionBinding;
import org.teavm.flavour.templates.tree.DirectiveVariableBinding;
import org.teavm.flavour.templates.tree.TemplateNode;

/**
 *
 * @author Alexey Andreev
 */
public class Parser {
    private ClassDescriberRepository classRepository;
    private ImportingClassResolver classResolver;
    private ResourceProvider resourceProvider;
    private Map<String, List<DirectiveMetadata>> avaliableDirectives = new HashMap<>();
    private Map<String, List<AttributeDirectiveMetadata>> avaliableAttrDirectives = new HashMap<>();
    private Map<String, DirectiveMetadata> directives = new HashMap<>();
    private Map<String, AttributeDirectiveMetadata> attrDirectives = new HashMap<>();
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
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    public boolean wasSuccessful() {
        return diagnostics.isEmpty();
    }

    public List<TemplateNode> parse(Reader reader, String className) throws IOException {
        source = new Source(reader);
        use(source, "std", "org.teavm.flavour.directives.standard");
        use(source, "event", "org.teavm.flavour.directives.events");
        use(source, "attr", "org.teavm.flavour.directives.attributes");
        use(source, "html", "org.teavm.flavour.directives.html");
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
                }
            }
        }
    }

    private TemplateNode parseElement(Element elem) {
        if (elem.getName().indexOf(':') > 0) {
            return parseDirective(elem);
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
                AttributeDirectiveBinding directive = parseAttributeDirective(attr);
                if (directive != null) {
                    templateElem.getAttributeDirectives().add(directive);
                }
            } else {
                templateElem.setAttribute(attr.getName(), attr.getValue(),
                        new Location(attr.getBegin(), attr.getEnd()));
            }
        }

        Set<String> vars = new HashSet<>();
        for (AttributeDirectiveBinding attrDirective : templateElem.getAttributeDirectives()) {
            for (DirectiveVariableBinding var : attrDirective.getVariables()) {
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

    private TemplateNode parseDirective(Element elem) {
        int prefixLength = elem.getName().indexOf(':');
        String prefix = elem.getName().substring(0, prefixLength);
        String name = elem.getName().substring(prefixLength + 1);
        String fullName = prefix + ":" + name;
        DirectiveMetadata directiveMeta = resolveDirective(prefix, name);
        if (directiveMeta == null) {
            error(elem.getStartTag().getNameSegment(), "Undefined directive " + fullName);
            return null;
        }

        return parseDirective(directiveMeta, prefix, name, elem);
    }

    private TemplateNode parseDirective(DirectiveMetadata directiveMeta, String prefix, String name, Element elem) {
        DirectiveBinding directive = new DirectiveBinding(directiveMeta.cls.getName(), name);
        directive.setLocation(new Location(elem.getBegin(), elem.getEnd()));
        if (directiveMeta.nameSetter != null) {
            directive.setDirectiveNameMethodName(directiveMeta.nameSetter.getName());
        }

        TypeUnifier unifier = new TypeUnifier(classRepository);
        Map<String, ValueType> declaredVars = new HashMap<>();
        for (DirectiveAttributeMetadata attrMeta : directiveMeta.attributes.values()) {
            Attribute attr = elem.getAttributes().get(attrMeta.name);
            if (attr == null) {
                if (attrMeta.required) {
                    error(elem.getStartTag(), "Missing required attribute: " + attrMeta.name);
                }
                continue;
            }
            MethodDescriber getter = attrMeta.getter;
            MethodDescriber setter = attrMeta.setter;
            switch (attrMeta.type) {
                case VARIABLE: {
                    String varName = attr.getValue();
                    if (declaredVars.containsKey(varName)) {
                        error(attr.getValueSegment(), "Variable " + varName + " is already used by the same "
                                + "directive");
                    } else {
                        declaredVars.put(varName, attrMeta.valueType);
                    }
                    DirectiveVariableBinding varBinding = new DirectiveVariableBinding(
                            getter.getOwner().getName(), getter.getName(), varName, getter.getRawReturnType(),
                            attrMeta.valueType);
                    directive.getVariables().add(varBinding);
                    break;
                }
                case FUNCTION: {
                    TypedPlan plan = compileExpr(attr.getValueSegment(), (GenericClass) attrMeta.valueType);
                    if (plan != null) {
                        DirectiveFunctionBinding computationBinding = new DirectiveFunctionBinding(
                                setter.getOwner().getName(), setter.getName(), (LambdaPlan) plan.getPlan(),
                                attrMeta.sam.getActualOwner().getName());
                        directive.getComputations().add(computationBinding);
                        unifier.unify(attrMeta.sam.getActualOwner(), (GenericType) plan.getType(), false);
                    }
                    break;
                }
            }
        }

        for (Map.Entry<String, ValueType> varEntry : declaredVars.entrySet()) {
            ValueType type = varEntry.getValue();
            if (type instanceof GenericType) {
                type = ((GenericType) type).substitute(unifier.getSubstitutions());
            }
            pushVar(varEntry.getKey(), type);
        }

        for (Attribute attr : elem.getAttributes()) {
            if (!directiveMeta.attributes.containsKey(attr.getName())) {
                error(attr, "Unknown attribute " + attr.getName() + " for directive " + prefix + ":" + name);
            }
        }

        parseSegment(elem.getEnd(), directive.getContentNodes(), child -> {
            if (elem.getName().indexOf(':') > 0) {
                int nestedPrefixLength = child.getName().indexOf(':');
                String nestedPrefix = child.getName().substring(0, nestedPrefixLength);
                String nestedName = child.getName().substring(nestedPrefixLength + 1);
                if (nestedPrefix.equals(prefix)) {
                    NestedDirective nested = resolveNestedDirective(directiveMeta, nestedName);
                    if (nested != null) {
                        parseDirective(nested.metadata, prefix, nestedName, child);
                        // TODO: assign to outer directive
                        return true;
                    }
                }
            }
            return false;
        });
        if (directiveMeta.contentSetter != null) {
            directive.setContentMethodName(directiveMeta.contentSetter.getName());
        } else if (!directiveMeta.ignoreContent) {
            if (!directive.getContentNodes().isEmpty()) {
                error(elem, "Directive " + directiveMeta.cls.getName() + " should not have any content");
            }
        }

        for (String varName : declaredVars.keySet()) {
            popVar(varName);
        }

        return directive;
    }

    private AttributeDirectiveBinding parseAttributeDirective(Attribute attr) {
        int prefixLength = attr.getName().indexOf(':');
        String prefix = attr.getName().substring(0, prefixLength);
        String name = attr.getName().substring(prefixLength + 1);
        String fullName = prefix + ":" + name;
        AttributeDirectiveMetadata directiveMeta = resolveAttrDirective(prefix, name);
        if (directiveMeta == null) {
            error(attr.getNameSegment(), "Undefined directive " + fullName);
            return null;
        }

        AttributeDirectiveBinding directive = new AttributeDirectiveBinding(directiveMeta.cls.getName(), name);
        directive.setLocation(new Location(attr.getBegin(), attr.getEnd()));
        if (directiveMeta.nameSetter != null) {
            directive.setDirectiveNameMethodName(directiveMeta.nameSetter.getName());
        }

        MethodDescriber getter = directiveMeta.getter;
        MethodDescriber setter = directiveMeta.setter;
        switch (directiveMeta.type) {
            case VARIABLE: {
                String varName = attr.getValue();
                DirectiveVariableBinding varBinding = new DirectiveVariableBinding(
                        setter.getOwner().getName(), getter.getName(), varName, getter.getRawReturnType(),
                        directiveMeta.valueType);
                directive.getVariables().add(varBinding);
                break;
            }
            case FUNCTION: {
                TypedPlan plan = compileExpr(attr.getValueSegment(), directiveMeta.sam.getActualOwner());
                if (plan != null) {
                    DirectiveFunctionBinding functionBinding = new DirectiveFunctionBinding(
                            setter.getOwner().getName(), setter.getName(), (LambdaPlan) plan.getPlan(),
                            directiveMeta.sam.getDescriber().getOwner().getName());
                    directive.getFunctions().add(functionBinding);
                }
                break;
            }
        }

        return directive;
    }

    private TypedPlan compileExpr(Segment segment, GenericClass type) {
        boolean hasErrors = false;
        org.teavm.flavour.expr.Parser exprParser = new org.teavm.flavour.expr.Parser(classResolver);
        Expr<Void> expr = exprParser.parse(segment.toString());
        int offset = segment.getBegin();
        for (Diagnostic diagnostic : exprParser.getDiagnostics()) {
            diagnostic = new Diagnostic(offset + diagnostic.getStart(), offset + diagnostic.getEnd(),
                    diagnostic.getMessage());
            diagnostics.add(diagnostic);
            hasErrors = true;
        }
        Compiler compiler = new Compiler(classRepository, classResolver, new TemplateScope());
        TypedPlan result = compiler.compileLambda(expr, type);
        PlanOffsetVisitor offsetVisitor = new PlanOffsetVisitor(segment.getBegin());
        result.getPlan().acceptVisitor(offsetVisitor);
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

    private void pushVar(String name, ValueType type) {
        Deque<ValueType> stack = variables.get(name);
        if (stack == null) {
            stack = new ArrayDeque<>();
            variables.put(name, stack);
        }
        stack.push(type);
    }

    private void popVar(String name) {
        Deque<ValueType> stack = variables.get(name);
        if (stack != null) {
            stack.pop();
            if (stack.isEmpty()) {
                variables.remove(stack);
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

    private DirectiveMetadata resolveDirective(String prefix, String name) {
        String fullName = prefix + ":" + name;
        DirectiveMetadata directive = directives.get(fullName);
        if (directive == null) {
            List<DirectiveMetadata> byPrefix = avaliableDirectives.get(prefix);
            if (byPrefix != null) {
                directive: for (DirectiveMetadata testDirective : byPrefix) {
                    for (String rule : testDirective.nameRules) {
                        if (matchRule(rule, name)) {
                            directive = testDirective;
                            break directive;
                        }
                    }
                }
            }
            directives.put(fullName, directive);
        }
        return directive;
    }

    private NestedDirective resolveNestedDirective(DirectiveMetadata outer, String name) {
        for (NestedDirective nested : outer.nestedDirectives) {
            if (Arrays.stream(nested.metadata.nameRules).anyMatch(rule -> matchRule(rule, name))) {
                return nested;
            }
        }
        return null;
    }

    private AttributeDirectiveMetadata resolveAttrDirective(String prefix, String name) {
        String fullName = prefix + ":" + name;
        AttributeDirectiveMetadata directive = attrDirectives.get(fullName);
        if (directive == null) {
            List<AttributeDirectiveMetadata> byPrefix = avaliableAttrDirectives.get(prefix);
            if (byPrefix != null) {
                directive: for (AttributeDirectiveMetadata testDirective : byPrefix) {
                    for (String rule : testDirective.nameRules) {
                        if (matchRule(rule, name)) {
                            directive = testDirective;
                            break directive;
                        }
                    }
                }
            }
            attrDirectives.put(fullName, directive);
        }
        return directive;
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
        String resourceName = "META-INF/flavour/directive-packages/" + packageName;
        try (InputStream input = resourceProvider.openResource(resourceName)) {
            if (input == null) {
                error(segment, "Directive package was not found: " + packageName);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            List<DirectiveMetadata> directiveList = new ArrayList<>();
            List<AttributeDirectiveMetadata> attributeDirectiveList = new ArrayList<>();
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
                    error(segment, "Class " + className + " declared by directive package was not found");
                    continue;
                }

                DirectiveParser directiveParser = new DirectiveParser(classRepository, diagnostics, segment);
                Object directiveMetadata = directiveParser.parse(cls);
                if (directiveMetadata instanceof DirectiveMetadata) {
                    DirectiveMetadata elemDirectiveMeta = (DirectiveMetadata) directiveMetadata;
                    directiveList.add(elemDirectiveMeta);
                } else if (directiveMetadata instanceof AttributeDirectiveMetadata) {
                    AttributeDirectiveMetadata attrDirectiveMeta = (AttributeDirectiveMetadata) directiveMetadata;
                    attributeDirectiveList.add(attrDirectiveMeta);
                }
            }
            avaliableDirectives.put(prefix, directiveList);
            avaliableAttrDirectives.put(prefix, attributeDirectiveList);
        } catch (IOException e) {
            throw new RuntimeException("IO exception occured parsing HTML input", e);
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
