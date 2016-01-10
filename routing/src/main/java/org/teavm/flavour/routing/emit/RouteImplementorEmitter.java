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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.teavm.flavour.mp.Choice;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectMethod;
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

/**
 *
 * @author Alexey Andreev
 */
class RouteImplementorEmitter {
    public static final String PATH_IMPLEMENTOR_CLASS = Route.class.getPackage().getName() + ".PathImplementor";
    public static final String ROUTING_CLASS = Route.class.getPackage().getName() + ".Routing";
    private PathParserEmitter pathParserEmitter;
    private RouteDescriber describer;
    private EmitterDiagnostics diagnostics;
    int suffixGenerator;
    private static Map<EmitterContext, RouteImplementorEmitter> instances = new WeakHashMap<>();

    private RouteImplementorEmitter(EmitterContext context) {
        describer = new RouteDescriber(context);
        pathParserEmitter = new PathParserEmitter();
    }

    public static RouteImplementorEmitter getInstance(EmitterContext context) {
        return instances.computeIfAbsent(context, ctx -> new RouteImplementorEmitter(ctx));
    }

    public Value<PathImplementor> emitParser(Emitter<?> em, ReflectClass<?> routeType) {
        this.diagnostics = em.getContext().getDiagnostics();
        SourceLocation location = em.getContext().getLocation();
        Set<ReflectClass<?>> pathSets = new HashSet<>();
        getPathSets(routeType, pathSets, new HashSet<>());
        if (pathSets.isEmpty()) {
            diagnostics.error(location, "Given handler {{t0}} does not implement path set", routeType);
            return em.emit(() -> null);
        } else if (pathSets.size() > 1) {
            Iterator<ReflectClass<?>> iter = pathSets.iterator();
            ReflectClass<?> example1 = iter.next();
            ReflectClass<?> example2 = iter.next();
            diagnostics.error(location, "Given handler {{t0}} implements several path sets. Examples are {{t1}} "
                    + "and {{t2}}", routeType, example1, example2);
            return em.emit(() -> null);
        }
        ReflectClass<?> implType = pathSets.iterator().next();
        return em.emit(() -> RoutingImpl.getImplementorByClassImpl(implType.asJavaClass()));
    }

    public Value<PathImplementor> emitInterfaceParser(Emitter<?> em, ReflectClass<?> routeType) {
        RouteSetDescriptor descriptor = describer.describeRouteSet(routeType);
        if (descriptor == null) {
            return em.emit(() -> null);
        }

        Value<PathParser> pathParser = pathParserEmitter.emitWorker(em, createPathParser(descriptor));
        return em.proxy(PathImplementor.class, (body, instance, method, args) -> {
            switch (method.getName()) {
                case "read": {
                    Value<String> path = body.emit(() -> (String) args[0].get());
                    Value<Route> handler = body.emit(() -> (Route) args[1].get());
                    body.returnValue(emitReadMethod(body, pathParser, path, handler, descriptor));
                    break;
                }
                case "write": {
                    @SuppressWarnings("unchecked")
                    Value<Consumer<String>> consumer = body.emit(() -> (Consumer<String>) args[0].get());
                    body.returnValue(emitWriteMethod(body, consumer, descriptor));
                    break;
                }
            }
        });
    }

    private Value<Boolean> emitReadMethod(Emitter<?> em, Value<PathParser> pathParser, Value<String> path,
            Value<Route> handler, RouteSetDescriptor descriptor) {
        Value<PathParserResult> parseResult = em.emit(() -> pathParser.get().parse(path.get()));
        Value<Integer> caseIndex = em.emit(() -> parseResult.get().getCaseIndex());
        Choice<Boolean> choice = em.choose(Boolean.class);

        for (int i = 0; i < descriptor.getRoutes().size(); ++i) {
            int index = i;
            RouteDescriptor routeDescriptor = descriptor.getRoutes().get(i);
            ReflectMethod method = routeDescriptor.getMethod();
            Emitter<Boolean> body = choice.option(() -> caseIndex.get().intValue() == index);

            int parameterCount = method.getParameterCount();
            Value<Object[]> values = body.emit(() -> new Object[parameterCount]);
            boolean[] affectedParams = new boolean[parameterCount];

            for (int j = 0; j < routeDescriptor.pathPartCount() - 1; ++j) {
                int paramIndex = j;
                ParameterDescriptor param = routeDescriptor.parameter(j);
                if (param == null) {
                    continue;
                }
                Value<? extends Object> paramValue = parseParam(body, param, body.emit(() -> {
                    int start = parseResult.get().start(paramIndex);
                    int end = parseResult.get().end(paramIndex);
                    return path.get().substring(start, end);
                }));

                int javaIndex = param.getJavaIndex();
                body.emit(() -> values.get()[javaIndex] = paramValue.get());
                affectedParams[javaIndex] = true;
            }
            for (int j = 0; j < parameterCount; ++j) {
                if (!affectedParams[j]) {
                    int paramIndex = j;
                    body.emit(() -> values.get()[paramIndex] = null);
                }
            }
            body.emit(() -> method.invoke(handler, values.get()));
            body.returnValue(() -> true);
        }

        choice.defaultOption().returnValue(() -> false);
        return choice.getValue();
    }

    private Value<Route> emitWriteMethod(Emitter<?> em, Value<Consumer<String>> consumer,
            RouteSetDescriptor descriptor) {
        Map<ReflectMethod, RouteDescriptor> methodMap = descriptor.getRoutes().stream()
                .collect(Collectors.toMap(route -> route.getMethod(), route -> route));

        ReflectClass<Route> routeType = descriptor.getType().asSubclass(Route.class);
        return em.proxy(routeType, (body, instance, method, args) -> {
            RouteDescriptor route = methodMap.get(method);
            if (route == null) {
                return;
            }
            String firstPart = route.pathPart(0);
            Value<StringBuilder> sb = body.emit(() -> new StringBuilder(firstPart));
            for (int i = 1; i < route.pathPartCount(); ++i) {
                ParameterDescriptor param = route.parameter(i - 1);
                if (param != null) {
                    int paramIndex = param.getJavaIndex();
                    Value<String> paramString = emitParam(body, param, args[paramIndex]);
                    Value<StringBuilder> localSb = sb;
                    sb = body.emit(() -> localSb.get().append(paramString.get()));
                }

                String part = route.pathPart(i);
                Value<StringBuilder> localSb = sb;
                sb = body.emit(() -> localSb.get().append(part));
            }

            Value<StringBuilder> localSb = sb;
            body.emit(() -> consumer.get().accept(localSb.get().toString()));
        });
    }

    private Value<? extends Object> parseParam(Emitter<?> em, ParameterDescriptor param, Value<String> string) {
        Value<String> decodedString = em.emit(() -> Window.decodeURIComponent(string.get()));
        switch (param.getType()) {
            case STRING:
                return decodedString;
            case BYTE:
                return em.emit(() -> Byte.parseByte(decodedString.get()));
            case SHORT:
                return em.emit(() -> Short.parseShort(decodedString.get()));
            case INTEGER:
                return em.emit(() -> Integer.parseInt(decodedString.get()));
            case LONG:
                return em.emit(() -> Long.parseLong(decodedString.get()));
            case FLOAT:
                return em.emit(() -> Float.parseFloat(decodedString.get()));
            case DOUBLE:
                return em.emit(() -> Double.parseDouble(decodedString.get()));
            case DATE:
                return em.emit(() -> new Date(RoutingImpl.parseDate(decodedString.get())));
            case ENUM: {
                ReflectMethod method = param.getValueType().getDeclaredJMethod("valueOf", String.class);
                return em.emit(() -> method.invoke(null, decodedString.get()));
            }
            case BIG_DECIMAL:
                return em.emit(() -> new BigDecimal(decodedString.get()));
            case BIG_INTEGER:
                return em.emit(() -> new BigInteger(decodedString.get()));
            default:
                throw new AssertionError("Unknown type: " + param.getType());
        }
    }

    private Value<String> emitParam(Emitter<?> em, ParameterDescriptor param, Value<Object> value) {
        switch (param.getType()) {
            case STRING:
                return em.emit(() -> Window.encodeURIComponent((String) value.get()));
            case BYTE:
            case SHORT:
            case INTEGER:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BIG_DECIMAL:
            case BIG_INTEGER:
                return em.emit(() -> value.get().toString());
            case DATE:
                return em.emit(() -> RoutingImpl.dateToString(((Date) value.get()).getTime()));
            case ENUM:
                return em.emit(() -> {
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

    private void getPathSets(ReflectClass<?> cls, Set<ReflectClass<?>> pathSets, Set<ReflectClass<?>> visited) {
        if (cls.getAnnotation(PathSet.class) != null) {
            pathSets.add(cls);
            return;
        }

        if (cls.getSuperclass() != null) {
            getPathSets(cls.getSuperclass(), pathSets, visited);
        }
        for (ReflectClass<?> iface : cls.getInterfaces()) {
            getPathSets(iface, pathSets, visited);
        }
    }
}
