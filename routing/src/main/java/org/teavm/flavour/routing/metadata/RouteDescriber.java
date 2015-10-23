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
package org.teavm.flavour.routing.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.parsing.RegexParseException;
import org.teavm.flavour.routing.Path;
import org.teavm.flavour.routing.PathParameter;
import org.teavm.flavour.routing.Pattern;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class RouteDescriber {
    private ClassReaderSource classSource;
    private Diagnostics diagnostics;
    private CallLocation location;

    public RouteDescriber(ClassReaderSource classSource, Diagnostics diagnostics, CallLocation location) {
        this.classSource = classSource;
        this.diagnostics = diagnostics;
        this.location = location;
    }

    public RouteSetDescriptor parseRouteSet(String className) {
        ClassReader cls = classSource.get(className);
        RouteSetDescriptor descriptor = new RouteSetDescriptor(className);

        for (MethodReader method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC) || !method.hasModifier(ElementModifier.ABSTRACT)) {
                continue;
            }
            descriptor.routes.add(parseRoute(method));
        }

        return descriptor;
    }

    private RouteDescriptor parseRoute(MethodReader method) {
        List<String> pathParts = new ArrayList<>();
        List<ParameterDescriptor> parameters = new ArrayList<>();
        Map<String, Integer> parameterNames = parseParameterNames(method);
        if (parsePath(method, pathParts, parameters, parameterNames)) {
            parseAnnotationsAndTypes(method, parameters);
            return new RouteDescriptor(method.getDescriptor(),
                    pathParts.toArray(new String[0]), parameters.toArray(new ParameterDescriptor[0]));
        } else {
            return null;
        }
    }

    private Map<String, Integer> parseParameterNames(MethodReader method) {
        AnnotationContainerReader[] parameterAnnotations = new AnnotationContainerReader[0];
        Map<String, Integer> parameterNames = new HashMap<>();
        for (int i = 0; i < method.parameterCount(); ++i) {
            AnnotationReader parameterAnnot = parameterAnnotations[i].get(PathParameter.class.getName());
            if (parameterAnnot == null) {
                diagnostics.error(location, "Missing {{c0}} annotation on parameter " + (i + 1) + " of {{m1}}",
                        PathParameter.class.getName(), method.getReference());
                continue;
            }

            String alias = parameterAnnot.getValue("value").getString();
            if (!isProperAliasName(alias)) {
                diagnostics.error(location, "Wrong name (" + alias + ") of parameter " + (i + 1) + " of {{m1}}",
                        method.getReference());
            }

            if (parameterNames.containsKey(alias)) {
                diagnostics.error(location, "Same parameter name (" + alias + ") used on both parameters " + (i + 1)
                        + " and "  + (parameterNames.get(alias) + 1) + " of {{m0}}", method.getReference());
                continue;
            }

            parameterNames.put(alias, i);
        }

        return parameterNames;
    }

    private boolean parsePath(MethodReader method, List<String> pathParts, List<ParameterDescriptor> parameters,
            Map<String, Integer> parameterNames) {
        AnnotationReader pathAnnot = method.getAnnotations().get(Path.class.getName());
        if (pathAnnot == null) {
            diagnostics.error(location, "Missing {{c0}} annotation on {{m1}}", Path.class.getName(),
                    method.getReference());
            return false;
        }

        String path = pathAnnot.getValue("value").getString();
        int index = 0;
        while (index < path.length()) {
            int next = path.indexOf('{', index);
            if (next < 0) {
                break;
            }
            pathParts.add(path.substring(index, next));
            int end = path.indexOf('}', next + 1);
            if (end < 0) {
                diagnostics.error(location, "Missing closing parenthesis on path of {{m0}}", method.getReference());
                return false;
            }
            String alias = path.substring(next + 1, end);
            index = next + 1;

            Integer paramIndex = parameterNames.get(alias);
            if (paramIndex == null) {
                diagnostics.error(location, "No parameter " + alias + ", referred by path, found in {{m0}}",
                        method.getReference());
            }
            ParameterDescriptor param = new ParameterDescriptor(paramIndex, alias);
            param.index = parameters.size();
            parameters.add(param);
        }
        pathParts.add(path.substring(index));

        return true;
    }

    private void parseAnnotationsAndTypes(MethodReader method, List<ParameterDescriptor> parameters) {
        AnnotationContainerReader[] annotations = method.getParameterAnnotations();
        for (ParameterDescriptor param : parameters) {
            ValueType paramType = method.parameterType(param.javaIndex);
            AnnotationContainerReader paramAnnotations = annotations[param.javaIndex];
            param.type = convertType(paramType);
            if (param.type == null) {
                diagnostics.error(location, "Wrong parameter type {{t0}} on method {{m1}}", method.getReference());
                continue;
            }

            AnnotationReader patternAnnot = paramAnnotations.get(Pattern.class.getName());
            if (patternAnnot != null) {
                if (param.type != ParameterType.STRING) {
                    diagnostics.error(location, "Parameter " + (param.index + 1) + " of {{m0}} is marked with "
                            + "{{c1}} annotation, and its type is not {{c2}", method.getReference(),
                            patternAnnot.getType(), "java.lang.String");
                } else {
                    try {
                        param.pattern = Node.parse(patternAnnot.getValue("value").getString());
                    } catch (RegexParseException e) {
                        diagnostics.error(location, "Parameter " + (param.index + 1) + " of {{m0}} is marked with "
                                + "{{c1}} annotation with syntax error in regex at position " + e.getIndex(),
                                method.getReference(), patternAnnot.getType());
                    }
                }
            }

            if (param.pattern != null) {
                param.effectivePattern = param.pattern;
            } else {
                param.effectivePattern = getDefaultPattern(param);
            }
        }
    }

    private Node getDefaultPattern(ParameterDescriptor parameter) {
        switch (parameter.type) {
            case STRING:
                return Node.atLeast(1, Node.range('\0', Character.MAX_VALUE));
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case BIG_INTEGER:
                return Node.atLeast(1, Node.range('0', '9'));
            case DOUBLE:
            case FLOAT:
            case BIG_DECIMAL: {
                Node digits = Node.parse("\\d+");
                Node exponent = Node.parse("[eE]");
                Node frac = Node.concat(Node.character('.'), digits);
                Node fracWithExponent = Node.concat(frac, Node.optional(exponent));
                return Node.concat(digits, Node.optional(Node.oneOf(fracWithExponent, exponent)));
            }
            case DATE:
                return Node.parse("\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2})?)?");
        }
        return null;
    }

    private ParameterType convertType(ValueType paramType) {
        if (paramType instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) paramType).getKind()) {
                case BYTE:
                    return ParameterType.BYTE;
                case SHORT:
                    return ParameterType.SHORT;
                case INTEGER:
                    return ParameterType.INTEGER;
                case LONG:
                    return ParameterType.LONG;
                case FLOAT:
                    return ParameterType.FLOAT;
                case DOUBLE:
                    return ParameterType.DOUBLE;
                default:
                    break;
            }
        } else if (paramType instanceof ValueType.Object) {
            String className = ((ValueType.Object) paramType).getClassName();
            switch (className) {
                case "java.lang.String":
                    return ParameterType.STRING;
                case "java.math.BigDecimal":
                    return ParameterType.BIG_DECIMAL;
                case "java.math.BigInteger":
                    return ParameterType.BIG_INTEGER;
                case "java.util.Date":
                    return ParameterType.DATE;
                default:
                    break;
            }
            if (classSource.isSuperType("java.lang.Enum", className).isPresent()) {
                return ParameterType.ENUM;
            }
        }
        return null;
    }

    private boolean isProperAliasName(String alias) {
        if (!Character.isJavaIdentifierStart(alias.charAt(0))) {
            return false;
        }
        for (int i = 1; i < alias.length(); ++i) {
            if (!Character.isJavaIdentifierPart(alias.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
