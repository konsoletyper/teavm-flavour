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
package org.teavm.flavour.routing.emit;

import static org.teavm.metaprogramming.Metaprogramming.emit;
import static org.teavm.metaprogramming.Metaprogramming.exit;
import static org.teavm.metaprogramming.Metaprogramming.lazy;
import static org.teavm.metaprogramming.Metaprogramming.lazyFragment;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.teavm.flavour.routing.PathSet;
import org.teavm.flavour.routing.Route;
import org.teavm.flavour.routing.metadata.ParameterDescriptor;
import org.teavm.flavour.routing.metadata.RouteDescriber;
import org.teavm.flavour.routing.metadata.RouteDescriptor;
import org.teavm.flavour.routing.metadata.RouteSetDescriptor;
import org.teavm.flavour.routing.parsing.PathBuilder;
import org.teavm.flavour.routing.parsing.PathParser;
import org.teavm.flavour.routing.parsing.PathParserBuilder;
import org.teavm.flavour.routing.parsing.PathParserEmitter;
import org.teavm.flavour.routing.parsing.PathParserResult;
import org.teavm.jso.browser.Window;
import org.teavm.metaprogramming.Diagnostics;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectMethod;

class RouteImplementorEmitter {
    private PathParserEmitter pathParserEmitter;
    private RouteDescriber describer;
    private Diagnostics diagnostics = Metaprogramming.getDiagnostics();
    private static RouteImplementorEmitter instance = new RouteImplementorEmitter();

    private RouteImplementorEmitter() {
        describer = new RouteDescriber(diagnostics);
        pathParserEmitter = new PathParserEmitter();
    }

    public static RouteImplementorEmitter getInstance() {
        return instance;
    }

    public Value<PathImplementor> emitParser(ReflectClass<?> routeType) {
        Set<ReflectClass<?>> pathSets = new HashSet<>();
        getPathSets(routeType, pathSets);
        if (pathSets.size() != 1) {
            return null;
        }
        ReflectClass<?> implType = pathSets.iterator().next();
        return emit(() -> RoutingImpl.getImplementorByClassImpl(implType.asJavaClass()));
    }

    public Value<PathImplementor> emitInterfaceParser(ReflectClass<?> routeType) {
        RouteSetDescriptor descriptor = describer.describeRouteSet(routeType);
        if (descriptor == null) {
            return null;
        }

        Value<PathParser> pathParser = pathParserEmitter.emitWorker(createPathParser(descriptor));
        return proxy(PathImplementor.class, (instance, method, args) -> {
            switch (method.getName()) {
                case "read": {
                    Value<String> path = emit(() -> (String) args[0].get());
                    Value<Route> handler = emit(() -> (Route) args[1].get());
                    Value<Boolean> result = emitReadMethod(pathParser, path, handler, descriptor);
                    exit(() -> result.get());
                    break;
                }
                case "write": {
                    @SuppressWarnings("unchecked")
                    Value<Consumer<String>> consumer = emit(() -> (Consumer<String>) args[0].get());
                    Value<Route> result = emitWriteMethod(consumer, descriptor);
                    exit(() -> result.get());
                    break;
                }
            }
        });
    }

    private Value<Boolean> emitReadMethod(Value<PathParser> pathParser, Value<String> path,
            Value<Route> handler, RouteSetDescriptor descriptor) {
        Value<PathParserResult> parseResult = emit(() -> pathParser.get().parse(path.get()));
        Value<Integer> caseIndex = emit(() -> parseResult.get().getCaseIndex());

        Value<Boolean> result = emit(() -> false);
        for (int i = descriptor.getRoutes().size() - 1; i >= 0; --i) {
            int index = i;
            RouteDescriptor routeDescriptor = descriptor.getRoutes().get(i);
            ReflectMethod method = routeDescriptor.getMethod();
            Value<Boolean> test = lazy(() -> caseIndex.get() == index);

            int parameterCount = method.getParameterCount();
            Value<Boolean> previous = result;
            Value<Boolean> next = lazyFragment(() -> {
                Value<Object[]> values = emit(() -> new Object[parameterCount]);
                boolean[] affectedParams = new boolean[parameterCount];

                for (int j = 0; j < routeDescriptor.pathPartCount() - 1; ++j) {
                    int paramIndex = j;
                    ParameterDescriptor param = routeDescriptor.parameter(j);
                    if (param == null) {
                        continue;
                    }
                    Value<?> paramValue = parseParam(param, emit(() -> {
                        int start = parseResult.get().start(paramIndex);
                        int end = parseResult.get().end(paramIndex);
                        return path.get().substring(start, end);
                    }));

                    int javaIndex = param.getJavaIndex();
                    emit(() -> values.get()[javaIndex] = paramValue.get());
                    affectedParams[javaIndex] = true;
                }
                for (int j = 0; j < parameterCount; ++j) {
                    if (!affectedParams[j]) {
                        int paramIndex = j;
                        emit(() -> values.get()[paramIndex] = null);
                    }
                }

                return emit(() -> {
                    method.invoke(handler, values.get());
                    return true;
                });
            });

            result = lazy(() -> test.get() ? next.get() : previous.get());
        }

        return result;
    }

    private Value<Route> emitWriteMethod(Value<Consumer<String>> consumer, RouteSetDescriptor descriptor) {
        Map<ReflectMethod, RouteDescriptor> methodMap = descriptor.getRoutes().stream()
                .collect(Collectors.toMap(route -> route.getMethod(), route -> route));

        ReflectClass<Route> routeType = descriptor.getType().asSubclass(Route.class);
        return proxy(routeType, (instance, method, args) -> {
            RouteDescriptor route = methodMap.get(method);
            if (route == null) {
                return;
            }
            String firstPart = route.pathPart(0);
            Value<StringBuilder> sb = emit(() -> new StringBuilder(firstPart));
            for (int i = 1; i < route.pathPartCount(); ++i) {
                ParameterDescriptor param = route.parameter(i - 1);
                if (param != null) {
                    int paramIndex = param.getJavaIndex();
                    Value<String> paramString = emitParam(param, args[paramIndex]);
                    Value<StringBuilder> localSb = sb;
                    sb = emit(() -> localSb.get().append(paramString.get()));
                }

                String part = route.pathPart(i);
                Value<StringBuilder> localSb = sb;
                sb = emit(() -> localSb.get().append(part));
            }

            Value<StringBuilder> localSb = sb;
            emit(() -> consumer.get().accept(localSb.get().toString()));
        });
    }

    private Value<?> parseParam(ParameterDescriptor param, Value<String> string) {
        Value<String> decodedString = emit(() -> Window.decodeURIComponent(string.get()));
        switch (param.getType()) {
            case STRING:
                return decodedString;
            case BYTE:
                return emit(() -> Byte.parseByte(decodedString.get()));
            case SHORT:
                return emit(() -> Short.parseShort(decodedString.get()));
            case INTEGER:
                return emit(() -> Integer.parseInt(decodedString.get()));
            case LONG:
                return emit(() -> Long.parseLong(decodedString.get()));
            case FLOAT:
                return emit(() -> Float.parseFloat(decodedString.get()));
            case DOUBLE:
                return emit(() -> Double.parseDouble(decodedString.get()));
            case DATE:
                return emit(() -> new Date(RoutingImpl.parseDate(decodedString.get())));
            case ENUM: {
                ReflectMethod method = param.getValueType().getDeclaredJMethod("valueOf", String.class);
                return emit(() -> method.invoke(null, decodedString.get()));
            }
            case BIG_DECIMAL:
                return emit(() -> new BigDecimal(decodedString.get()));
            case BIG_INTEGER:
                return emit(() -> new BigInteger(decodedString.get()));
            default:
                throw new AssertionError("Unknown type: " + param.getType());
        }
    }

    private Value<String> emitParam(ParameterDescriptor param, Value<Object> value) {
        switch (param.getType()) {
            case STRING:
                return emit(() -> Window.encodeURIComponent((String) value.get()));
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BIG_INTEGER:
                return emit(() -> value.get().toString());
            case DATE:
                return emit(() -> RoutingImpl.dateToString(((Date) value.get()).getTime()));
            case ENUM:
                return emit(() -> {
                    Enum<?> e = (Enum<?>) value.get();
                    return Window.encodeURIComponent(e.name());
                });
            default:
                throw new AssertionError("Unknown type: " + param.getType());
        }
    }


    private PathParser createPathParser(RouteSetDescriptor descriptor) {
        PathParserBuilder pathParserBuilder = new PathParserBuilder();
        for (RouteDescriptor routeDescriptor : descriptor.getRoutes()) {
            PathBuilder pathBuilder = pathParserBuilder.path();
            pathBuilder.text(routeDescriptor.pathPart(0));
            for (int i = 1; i < routeDescriptor.pathPartCount(); ++i) {
                ParameterDescriptor param = routeDescriptor.parameter(i - 1);
                if (param != null && param.getType() != null) {
                    pathBuilder.escapedRegex(param.getEffectivePattern());
                    pathBuilder.text(routeDescriptor.pathPart(i));
                }
            }
        }
        return pathParserBuilder.buildUnprepared();
    }

    private void getPathSets(ReflectClass<?> cls, Set<ReflectClass<?>> pathSets) {
        if (cls.getAnnotation(PathSet.class) != null) {
            pathSets.add(cls);
            return;
        }

        if (cls.getSuperclass() != null) {
            getPathSets(cls.getSuperclass(), pathSets);
        }
        for (ReflectClass<?> iface : cls.getInterfaces()) {
            getPathSets(iface, pathSets);
        }
    }
}
