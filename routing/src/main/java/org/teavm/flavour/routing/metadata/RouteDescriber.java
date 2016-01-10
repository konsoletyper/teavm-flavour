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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.reflect.ReflectAnnotatedElement;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.flavour.regex.ast.Node;
import org.teavm.flavour.regex.parsing.RegexParseException;
import org.teavm.flavour.routing.Path;
import org.teavm.flavour.routing.PathParameter;
import org.teavm.flavour.routing.Pattern;

/**
 *
 * @author Alexey Andreev
 */
public class RouteDescriber {
    private EmitterDiagnostics diagnostics;

    public RouteDescriber(EmitterContext context) {
        this.diagnostics = context.getDiagnostics();
    }

    public RouteSetDescriptor describeRouteSet(ReflectClass<?> cls) {
        RouteSetDescriptor descriptor = new RouteSetDescriptor(cls);

        for (ReflectMethod method : cls.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || !Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            descriptor.routes.add(describeRoute(method));
        }

        return descriptor;
    }

    private RouteDescriptor describeRoute(ReflectMethod method) {
        List<String> pathParts = new ArrayList<>();
        List<ParameterDescriptor> parameters = new ArrayList<>();
        Map<String, Integer> parameterNames = parseParameterNames(method);
        if (parsePath(method, pathParts, parameters, parameterNames)) {
            parseAnnotationsAndTypes(method, parameters);
            return new RouteDescriptor(method, pathParts.toArray(new String[0]),
                    parameters.toArray(new ParameterDescriptor[0]));
        } else {
            return null;
        }
    }

    private Map<String, Integer> parseParameterNames(ReflectMethod method) {
        SourceLocation location = new SourceLocation(method);
        Map<String, Integer> parameterNames = new HashMap<>();
        for (int i = 0; i < method.getParameterCount(); ++i) {
            PathParameter parameterAnnot = method.getParameterAnnotations(i).getAnnotation(PathParameter.class);
            if (parameterAnnot == null) {
                diagnostics.error(location, "Missing {{t0}} annotation on parameter " + (i + 1) + " of {{m1}}",
                        PathParameter.class, method);
                continue;
            }

            String alias = parameterAnnot.value();
            if (!isProperAliasName(alias)) {
                diagnostics.error(location, "Wrong name (" + alias + ") of parameter " + (i + 1) + " of {{m1}}",
                        method);
            }

            if (parameterNames.containsKey(alias)) {
                diagnostics.error(location, "Same parameter name (" + alias + ") used on both parameters " + (i + 1)
                        + " and "  + (parameterNames.get(alias) + 1) + " of {{m0}}", method);
                continue;
            }

            parameterNames.put(alias, i);
        }

        return parameterNames;
    }

    private boolean parsePath(ReflectMethod method, List<String> pathParts, List<ParameterDescriptor> parameters,
            Map<String, Integer> parameterNames) {
        SourceLocation location = new SourceLocation(method);
        Path pathAnnot = method.getAnnotation(Path.class);
        if (pathAnnot == null) {
            diagnostics.error(location, "Missing {{t0}} annotation on {{m1}}", Path.class, method);
            return false;
        }

        String path = pathAnnot.value();
        int index = 0;
        while (index < path.length()) {
            int next = path.indexOf('{', index);
            if (next < 0) {
                break;
            }
            pathParts.add(path.substring(index, next));
            int end = path.indexOf('}', next + 1);
            if (end < 0) {
                diagnostics.error(location, "Missing closing parenthesis on path of {{m0}}", method);
                return false;
            }
            String alias = path.substring(next + 1, end);

            Integer paramIndex = parameterNames.get(alias);
            if (paramIndex == null) {
                diagnostics.error(location, "No parameter " + alias + ", referred by path, found in {{m0}}", method);
                parameters.add(null);
            } else {
                ParameterDescriptor param = new ParameterDescriptor(paramIndex, alias);
                param.index = parameters.size();
                parameters.add(param);
            }
            index = end + 1;
        }
        pathParts.add(path.substring(index));

        return true;
    }

    private void parseAnnotationsAndTypes(ReflectMethod method, List<ParameterDescriptor> parameters) {
        SourceLocation location = new SourceLocation(method);
        for (ParameterDescriptor param : parameters) {
            if (param == null) {
                continue;
            }
            ReflectClass<?> paramType = method.getParameterType(param.javaIndex);
            param.valueType = paramType;
            ReflectAnnotatedElement paramAnnotations = method.getParameterAnnotations(param.javaIndex);
            param.type = convertType(paramType);
            if (param.type == null) {
                diagnostics.error(location, "Wrong parameter type {{t0}} on method {{m1}}", paramType, method);
                continue;
            }

            Pattern patternAnnot = paramAnnotations.getAnnotation(Pattern.class);
            if (patternAnnot != null) {
                if (param.type != ParameterType.STRING) {
                    diagnostics.error(location, "Parameter " + (param.index + 1) + " of {{m0}} is marked with "
                            + "{{t1}} annotation, and its type is not {{t2}", method, patternAnnot, String.class);
                } else {
                    try {
                        param.pattern = Node.parse(patternAnnot.value());
                    } catch (RegexParseException e) {
                        diagnostics.error(location, "Parameter " + (param.index + 1) + " of {{m0}} is marked with "
                                + "{{t1}} annotation with syntax error in regex at position " + e.getIndex(),
                                method, Pattern.class);
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
            case ENUM:
                return getDefaultPatternForEnum(parameter);
            default:
                throw new AssertionError("Unknown parameter type: " + parameter.type);
        }
    }

    private Node getDefaultPatternForEnum(ParameterDescriptor parameter) {
        ReflectClass<?> cls = parameter.valueType;
        return Node.oneOf(Arrays.stream(cls.getDeclaredFields())
                .filter(field -> field.isEnumConstant())
                .map(field -> Node.text(field.getName()))
                .<Node>toArray(sz -> new Node[sz]));
    }

    private ParameterType convertType(ReflectClass<?> paramType) {
        if (paramType.isPrimitive()) {
            switch (paramType.getName()) {
                case "byte":
                    return ParameterType.BYTE;
                case "short":
                    return ParameterType.SHORT;
                case "int":
                    return ParameterType.INTEGER;
                case "long":
                    return ParameterType.LONG;
                case "float":
                    return ParameterType.FLOAT;
                case "double":
                    return ParameterType.DOUBLE;
                default:
                    break;
            }
        } else if (!paramType.isArray()) {
            switch (paramType.getName()) {
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
            if (paramType.isEnum()) {
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
