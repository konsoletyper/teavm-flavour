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
package org.teavm.flavour.rest.impl;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.ListDeserializer;
import org.teavm.flavour.json.deserializer.MapDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.EmitterContext;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.flavour.rest.ResourceFactory;
import org.teavm.flavour.rest.impl.model.BeanRepository;
import org.teavm.flavour.rest.impl.model.MethodKey;
import org.teavm.flavour.rest.impl.model.MethodModel;
import org.teavm.flavour.rest.impl.model.PropertyModel;
import org.teavm.flavour.rest.impl.model.PropertyValuePath;
import org.teavm.flavour.rest.impl.model.ResourceModel;
import org.teavm.flavour.rest.impl.model.ResourceModelRepository;
import org.teavm.flavour.rest.impl.model.RootValuePath;
import org.teavm.flavour.rest.impl.model.ValuePath;
import org.teavm.flavour.rest.impl.model.ValuePathVisitor;
import org.teavm.flavour.rest.processor.HttpMethod;
import org.teavm.jso.browser.Window;

/**
 *
 * @author Alexey Andreev
 */
public class FactoryEmitter {
    private EmitterDiagnostics diagnostics;
    private ClassLoader classLoader;
    private ResourceModelRepository resourceRepository;
    private SourceLocation location;
    private static Map<EmitterContext, FactoryEmitter> instances = new WeakHashMap<>();

    private FactoryEmitter(EmitterContext context, ResourceModelRepository modelRepository) {
        this.diagnostics = context.getDiagnostics();
        this.classLoader = context.getClassLoader();
        this.resourceRepository = modelRepository;
    }

    public static FactoryEmitter getInstance(EmitterContext context) {
        return instances.computeIfAbsent(context, ctx -> {
            BeanRepository beanRepository = new BeanRepository(context);
            ResourceModelRepository modelRepository = new ResourceModelRepository(context, beanRepository);
            return new FactoryEmitter(context, modelRepository);
        });
    }

    public Value<? extends ResourceFactory<?>> emitFactory(Emitter<?> em, ReflectClass<?> cls) {
        return em.proxy(FactoryTemplate.class, (body, instance, methods, args) -> {
            Value<String> path = body.emit(() -> (String) args[0].get());
            body.returnValue(emitFactoryWorker(body, instance, path, cls.asSubclass(Object.class)));
        });
    }

    private Value<Object> emitFactoryWorker(Emitter<?> em, Value<FactoryTemplate> factory, Value<String> path,
            ReflectClass<Object> cls) {
        ResourceModel resource = resourceRepository.getResource(cls);
        Value<ProxyTemplate> template = em.emit(() -> new ProxyTemplate(factory.get(), path.get()));
        return em.proxy(cls, (body, instance, method, args) -> {
            MethodModel model = resource.getMethods().get(new MethodKey(method));
            location = new SourceLocation(method);

            Value<HttpMethod> httpMethod = emitHttpMethod(body, model);
            Value<String> url = emitRequestUrl(body, resource, model, args);
            Value<RequestImpl> request = body.emit(() -> new RequestImpl(httpMethod.get(), url.get()));
            request = emitHeaders(body, request, model, args);
            request = emitContent(body, request, model, args);

            Value<RequestImpl> localRequest = request;
            Value<ResponseImpl> response = body.emit(() -> template.get().send(localRequest.get()));
            body.emit(() -> response.get().defaultAction());

            if (method.getReturnType() != em.getContext().findClass(void.class)) {
                ReflectClass<?> returnType = method.getReturnType();
                Value<Node> responseContent = body.emit(() -> response.get().getContent());
                body.returnValue(deserialize(body, model, responseContent, returnType));
            }
        });
    }

    private Value<HttpMethod> emitHttpMethod(Emitter<?> em, MethodModel model) {
        ReflectClass<?> httpMethodClass = em.getContext().findClass(HttpMethod.class);
        ReflectField field = httpMethodClass.getDeclaredField(model.getHttpMethod().name());
        return em.emit(() -> (HttpMethod) field.get(null));
    }

    private Value<String> emitRequestUrl(Emitter<?> em, ResourceModel resource, MethodModel model,
            Value<Object>[] args) {
        Value<StringBuilder> sb = em.emit(() -> new StringBuilder());
        if (!resource.getPath().isEmpty()) {
            sb = appendUrlPattern(em, resource.getPath(), model, sb, args);
            if (!model.getPath().isEmpty()) {
                Value<StringBuilder> localSb = sb;
                sb = em.emit(() -> localSb.get().append("/"));
            }
        }
        sb = appendUrlPattern(em, model.getPath(), model, sb, args);
        Value<String[]> sep = em.emit(() -> new String[] { "?" });
        for (ValuePath queryParam : model.getQueryParameters().values()) {
            String paramName = queryParam.getName();
            Value<Object> value = getParameter(em, queryParam, args);
            Value<StringBuilder> localSb = sb;
            em.emit(() -> {
                StringBuilder innerSb = localSb.get();
                if (value.get() != null) {
                    innerSb = innerSb.append(sep.get()[0]).append(paramName).append("=");
                    innerSb = innerSb.append(Window.encodeURIComponent(String.valueOf(value.get())));
                    sep.get()[0] = "&";
                }
                return innerSb;
            });
        }
        Value<StringBuilder> localSb = sb;
        return em.emit(() -> localSb.get().toString());
    }

    private Value<StringBuilder> appendUrlPattern(Emitter<?> em, String pattern, MethodModel model,
            Value<StringBuilder> sb, Value<Object>[] args) {
        int index = 0;
        while (index < pattern.length()) {
            int next = pattern.indexOf('{', index);
            if (next < 0) {
                sb = appendConstant(em, sb, pattern.substring(index));
                break;
            }
            int end = findClosingBracket(pattern, next + 1);
            if (end < 0) {
                sb = appendConstant(em, sb, pattern.substring(index));
                break;
            }
            int sep = pattern.indexOf(':', next);
            if (sep < 0 || sep > end) {
                sep = end;
            }
            String name = pattern.substring(next + 1, sep);

            ValuePath value = model.getPathParameters().get(name);
            if (value == null) {
                diagnostics.error(location, "Unknown parameter referred by path: " + name);
            } else {
                sb = appendValue(em, sb, getParameter(em, value, args));
            }

            index = end + 1;
        }
        return sb;
    }

    private int findClosingBracket(String text, int index) {
        int open = 1;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == '}') {
                if (--open == 0) {
                    return index;
                }
            } else if (c == '{') {
                ++open;
            } else if (c == '\\' && index + 1 < text.length() && text.charAt(index + 1) == '{') {
                ++index;
            }
            ++index;
        }
        return -1;
    }

    private Value<RequestImpl> emitHeaders(Emitter<?> em, Value<RequestImpl> request, MethodModel model,
            Value<Object>[] args) {
        for (ValuePath header : model.getHeaderParameters().values()) {
            Value<RequestImpl> localRequest = request;
            String name = header.getName();
            Value<Object> value = getParameter(em, header, args);
            request = em.emit(() -> localRequest.get().setHeader(name,
                    Window.encodeURIComponent(value.get().toString())));
        }
        return request;
    }

    private Value<RequestImpl> emitContent(Emitter<?> em, Value<RequestImpl> request, MethodModel model,
            Value<Object>[] args) {
        if (model.getBody() != null) {
            Value<RequestImpl> localRequest = request;
            Value<Object> content = getParameter(em, model.getBody(), args);
            request = em.emit(() -> localRequest.get().setContent(JSON.serialize(content.get())));
        }
        return request;
    }

    private Value<Object> getParameter(Emitter<?> em, ValuePath value, Value<Object>[] args) {
        class EmittingVisitor implements ValuePathVisitor {
            Value<Object> current;
            @Override
            public void visit(PropertyValuePath path) {
                path.getParent().acceptVisitor(this);
                Value<Object> localCurrent = current;
                PropertyModel property = path.getProperty();
                if (property.getGetter() != null) {
                    ReflectMethod getter = property.getGetter();
                    current = em.emit(() -> getter.invoke(localCurrent.get()));
                } else {
                    ReflectField field = property.getField();
                    current = em.emit(() -> field.get(localCurrent.get()));
                }
            }
            @Override
            public void visit(RootValuePath path) {
                current = args[path.getParameter().getIndex()];
            }
        }
        EmittingVisitor visitor = new EmittingVisitor();
        value.acceptVisitor(visitor);
        return visitor.current;
    }


    private Value<StringBuilder> appendConstant(Emitter<?> em, Value<StringBuilder> sb, String constant) {
        if (!constant.isEmpty()) {
            Value<StringBuilder> localSb = sb;
            sb = em.emit(() -> localSb.get().append(constant));
        }
        return sb;
    }

    private Value<StringBuilder> appendValue(Emitter<?> em, Value<StringBuilder> sb, Value<Object> value) {
        return em.emit(() -> sb.get().append(Window.encodeURIComponent(String.valueOf(value.get()))));
    }

    private Value<Object> deserialize(Emitter<?> em, MethodModel method, Value<Node> value,
            ReflectClass<?> target) {
        if (target.isPrimitive()) {
            switch (target.getName()) {
                case "boolean":
                    return em.emit(() -> JSON.deserializeBoolean(value.get()));
                case "byte":
                    return em.emit(() -> JSON.deserializeByte(value.get()));
                case "short":
                    return em.emit(() -> JSON.deserializeShort(value.get()));
                case "char":
                    return em.emit(() -> JSON.deserializeChar(value.get()));
                case "int":
                    return em.emit(() -> JSON.deserializeInt(value.get()));
                case "long":
                    return em.emit(() -> JSON.deserializeLong(value.get()));
                case "float":
                    return em.emit(() -> JSON.deserializeFloat(value.get()));
                case "double":
                    return em.emit(() -> JSON.deserializeDouble(value.get()));
            }
            throw new AssertionError();
        } else {
            Method javaMethod = findMethod(method.getMethod());
            if (javaMethod == null) {
                return em.emit(() -> null);
            }
            Value<JsonDeserializer> deserializer = createDeserializer(em, javaMethod.getGenericReturnType());
            return em.emit(() -> deserializer.get().deserialize(new JsonDeserializerContext(), value.get()));
        }
    }

    private Value<JsonDeserializer> createDeserializer(Emitter<?> em, Type type) {
        if (type instanceof Class<?>) {
            return createObjectDeserializer(em, (Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapDeserializer(em, typeArgs[0], typeArgs[1]);
            } else if (paramType.getRawType().equals(List.class)) {
                return createListDeserializer(em, typeArgs[0]);
            } else if (paramType.getRawType().equals(Set.class)) {
                return createSetDeserializer(em, typeArgs[0]);
            } else {
                return createDeserializer(em, paramType.getRawType());
            }
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(em, upperCls);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>) type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(em, upperCls);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            return createArrayDeserializer(em, array);
        } else {
            return createObjectDeserializer(em, Object.class);
        }
    }

    private Value<JsonDeserializer> createMapDeserializer(Emitter<?> em,  Type keyType, Type valueType) {
        Value<JsonDeserializer> keyDeserializer = createDeserializer(em, keyType);
        Value<JsonDeserializer> valueDeserializer = createDeserializer(em, valueType);
        return em.emit(() -> new MapDeserializer(keyDeserializer.get(), valueDeserializer.get()));
    }

    private Value<JsonDeserializer> createListDeserializer(Emitter<?> em, Type itemType) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, itemType);
        return em.emit(() -> new ListDeserializer(itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createSetDeserializer(Emitter<?> em, Type itemType) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, itemType);
        return em.emit(() -> new SetDeserializer(itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createArrayDeserializer(Emitter<?> em, GenericArrayType type) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(em, type.getGenericComponentType());
        return em.emit(() -> new ArrayDeserializer(Object.class, itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createObjectDeserializer(Emitter<?> em, Class<?> type) {
        return em.emit(() -> JSON.getClassDeserializer(type));
    }

    private Method findMethod(ReflectMethod method) {
        Class<?> owner = findClass(method.getDeclaringClass().getName());
        Class<?>[] params = new Class<?>[method.getParameterCount()];
        for (int i = 0; i < params.length; ++i) {
            params[i] = convertType(method.getParameterType(i));
        }
        while (owner != null) {
            try {
                return owner.getDeclaredMethod(method.getName(), params);
            } catch (NoSuchMethodException e) {
                owner = owner.getSuperclass();
            }
        }
        diagnostics.error(new SourceLocation(method), "Corresponding Java method not found");
        return null;
    }

    private Class<?> convertType(ReflectClass<?> type) {
        if (type.isPrimitive()) {
            switch (type.getName()) {
                case "boolean":
                    return boolean.class;
                case "byte":
                    return byte.class;
                case "short":
                    return short.class;
                case "char":
                    return char.class;
                case "int":
                    return int.class;
                case "long":
                    return long.class;
                case "float":
                    return float.class;
                case "double":
                    return double.class;
                case "void":
                    return void.class;
            }
        } else if (type.isArray()) {
            Class<?> itemCls = convertType(type.getComponentType());
            return Array.newInstance(itemCls, 0).getClass();
        } else {
            return findClass(type.getName());
        }
        throw new AssertionError("Can't convert type: " + type);
    }

    private Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Can't find class " + name, e);
        }
    }
}
