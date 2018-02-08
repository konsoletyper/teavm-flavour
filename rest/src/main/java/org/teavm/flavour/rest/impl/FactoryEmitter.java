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

import static org.teavm.metaprogramming.Metaprogramming.emit;
import static org.teavm.metaprogramming.Metaprogramming.exit;
import static org.teavm.metaprogramming.Metaprogramming.proxy;
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
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.ListDeserializer;
import org.teavm.flavour.json.deserializer.MapDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.tree.Node;
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
import org.teavm.metaprogramming.Diagnostics;
import org.teavm.metaprogramming.Metaprogramming;
import org.teavm.metaprogramming.ReflectClass;
import org.teavm.metaprogramming.SourceLocation;
import org.teavm.metaprogramming.Value;
import org.teavm.metaprogramming.reflect.ReflectField;
import org.teavm.metaprogramming.reflect.ReflectMethod;

public class FactoryEmitter {
    private Diagnostics diagnostics;
    private ClassLoader classLoader;
    private ResourceModelRepository resourceRepository;
    private SourceLocation location;
    private static FactoryEmitter instance;

    private FactoryEmitter() {
        BeanRepository beanRepository = new BeanRepository();
        ResourceModelRepository modelRepository = new ResourceModelRepository(beanRepository);
        this.diagnostics = Metaprogramming.getDiagnostics();
        this.classLoader = Metaprogramming.getClassLoader();
        this.resourceRepository = modelRepository;
    }

    public static FactoryEmitter getInstance() {
        if (instance == null) {
            instance = new FactoryEmitter();
        }
        return instance;
    }

    public Value<? extends ResourceFactory<?>> emitFactory(ReflectClass<?> cls) {
        return proxy(FactoryTemplate.class, (instance, methods, args) -> {
            Value<String> path = emit(() -> (String) args[0].get());
            Value<Object> result = emitFactoryWorker(instance, path, cls.asSubclass(Object.class));
            exit(() -> result.get());
        });
    }

    private Value<Object> emitFactoryWorker(Value<FactoryTemplate> factory, Value<String> path,
            ReflectClass<Object> cls) {
        ResourceModel resource = resourceRepository.getResource(cls);
        Value<ProxyTemplate> template = emit(() -> new ProxyTemplate(factory.get(), path.get()));
        return proxy(cls, (instance, method, args) -> {
            MethodModel model = resource.getMethods().get(new MethodKey(method));
            location = new SourceLocation(method);

            Value<HttpMethod> httpMethod = emitHttpMethod(model);
            Value<String> url = emitRequestUrl(resource, model, args);
            Value<RequestImpl> request = emit(() -> new RequestImpl(httpMethod.get(), url.get()));
            request = emitHeaders(request, model, args);
            request = emitContent(request, model, args);

            Value<RequestImpl> localRequest = request;
            Value<ResponseImpl> response = emit(() -> template.get().send(localRequest.get()));
            emit(() -> response.get().defaultAction());

            if (method.getReturnType() != Metaprogramming.findClass(void.class)) {
                ReflectClass<?> returnType = method.getReturnType();
                Value<Node> responseContent = emit(() -> response.get().getContent());
                Value<Object> result = deserialize(model, responseContent, returnType);
                exit(() -> result.get());
            }
        });
    }

    private Value<HttpMethod> emitHttpMethod(MethodModel model) {
        ReflectClass<?> httpMethodClass = Metaprogramming.findClass(HttpMethod.class);
        ReflectField field = httpMethodClass.getDeclaredField(model.getHttpMethod().name());
        return emit(() -> (HttpMethod) field.get(null));
    }

    private Value<String> emitRequestUrl(ResourceModel resource, MethodModel model, Value<Object>[] args) {
        Value<StringBuilder> sb = emit(() -> new StringBuilder());
        if (!resource.getPath().isEmpty()) {
            sb = appendUrlPattern(resource.getPath(), model, sb, args);
            if (!model.getPath().isEmpty()) {
                Value<StringBuilder> localSb = sb;
                sb = emit(() -> localSb.get().append("/"));
            }
        }
        sb = appendUrlPattern(model.getPath(), model, sb, args);
        Value<String[]> sep = emit(() -> new String[] { "?" });
        for (ValuePath queryParam : model.getQueryParameters().values()) {
            String paramName = queryParam.getName();
            Value<Object> value = getParameter(queryParam, args);
            Value<StringBuilder> localSb = sb;
            emit(() -> {
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
        return emit(() -> localSb.get().toString());
    }

    private Value<StringBuilder> appendUrlPattern(String pattern, MethodModel model,
            Value<StringBuilder> sb, Value<Object>[] args) {
        int index = 0;
        while (index < pattern.length()) {
            int next = pattern.indexOf('{', index);
            if (next < 0) {
                sb = appendConstant(sb, pattern.substring(index));
                break;
            }
            int end = findClosingBracket(pattern, next + 1);
            if (end < 0) {
                sb = appendConstant(sb, pattern.substring(index));
                break;
            }
            
            sb = appendConstant(sb, pattern.substring(index, next));
            
            int sep = pattern.indexOf(':', next);
            if (sep < 0 || sep > end) {
                sep = end;
            }
            String name = pattern.substring(next + 1, sep);

            ValuePath value = model.getPathParameters().get(name);
            if (value == null) {
                diagnostics.error(location, "Unknown parameter referred by path: " + name);
            } else {
                sb = appendValue(sb, getParameter(value, args));
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

    private Value<RequestImpl> emitHeaders(Value<RequestImpl> request, MethodModel model, Value<Object>[] args) {
        for (ValuePath header : model.getHeaderParameters().values()) {
            Value<RequestImpl> localRequest = request;
            String name = header.getName();
            Value<Object> value = getParameter(header, args);
            request = emit(() -> localRequest.get().setHeader(name,
                    Window.encodeURIComponent(value.get().toString())));
        }
        return request;
    }

    private Value<RequestImpl> emitContent(Value<RequestImpl> request, MethodModel model, Value<Object>[] args) {
        if (model.getBody() != null) {
            Value<RequestImpl> localRequest = request;
            Value<Object> content = getParameter(model.getBody(), args);
            request = emit(() -> localRequest.get().setContent(JSON.serialize(content.get())));
        }
        return request;
    }

    private Value<Object> getParameter(ValuePath value, Value<Object>[] args) {
        class EmittingVisitor implements ValuePathVisitor {
            Value<Object> current;
            @Override
            public void visit(PropertyValuePath path) {
                path.getParent().acceptVisitor(this);
                Value<Object> localCurrent = current;
                PropertyModel property = path.getProperty();
                if (property.getGetter() != null) {
                    ReflectMethod getter = property.getGetter();
                    current = emit(() -> getter.invoke(localCurrent.get()));
                } else {
                    ReflectField field = property.getField();
                    current = emit(() -> field.get(localCurrent.get()));
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


    private Value<StringBuilder> appendConstant(Value<StringBuilder> sb, String constant) {
        if (!constant.isEmpty()) {
            Value<StringBuilder> localSb = sb;
            sb = emit(() -> localSb.get().append(constant));
        }
        return sb;
    }

    private Value<StringBuilder> appendValue(Value<StringBuilder> sb, Value<Object> value) {
        return emit(() -> sb.get().append(Window.encodeURIComponent(String.valueOf(value.get()))));
    }

    private Value<Object> deserialize(MethodModel method, Value<Node> value, ReflectClass<?> target) {
        if (target.isPrimitive()) {
            switch (target.getName()) {
                case "boolean":
                    return emit(() -> JSON.deserializeBoolean(value.get()));
                case "byte":
                    return emit(() -> JSON.deserializeByte(value.get()));
                case "short":
                    return emit(() -> JSON.deserializeShort(value.get()));
                case "char":
                    return emit(() -> JSON.deserializeChar(value.get()));
                case "int":
                    return emit(() -> JSON.deserializeInt(value.get()));
                case "long":
                    return emit(() -> JSON.deserializeLong(value.get()));
                case "float":
                    return emit(() -> JSON.deserializeFloat(value.get()));
                case "double":
                    return emit(() -> JSON.deserializeDouble(value.get()));
            }
            throw new AssertionError();
        } else {
            Method javaMethod = findMethod(method.getMethod());
            if (javaMethod == null) {
                return emit(() -> null);
            }
            Value<JsonDeserializer> deserializer = createDeserializer(javaMethod.getGenericReturnType());
            return emit(() -> deserializer.get().deserialize(new JsonDeserializerContext(), value.get()));
        }
    }

    private Value<JsonDeserializer> createDeserializer(Type type) {
        if (type instanceof Class<?>) {
            return createObjectDeserializer((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapDeserializer(typeArgs[0], typeArgs[1]);
            } else if (paramType.getRawType().equals(List.class)) {
                return createListDeserializer(typeArgs[0]);
            } else if (paramType.getRawType().equals(Set.class)) {
                return createSetDeserializer(typeArgs[0]);
            } else {
                return createDeserializer(paramType.getRawType());
            }
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(upperCls);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>) type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(upperCls);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            return createArrayDeserializer(array);
        } else {
            return createObjectDeserializer(Object.class);
        }
    }

    private Value<JsonDeserializer> createMapDeserializer(Type keyType, Type valueType) {
        Value<JsonDeserializer> keyDeserializer = createDeserializer(keyType);
        Value<JsonDeserializer> valueDeserializer = createDeserializer(valueType);
        return emit(() -> new MapDeserializer(keyDeserializer.get(), valueDeserializer.get()));
    }

    private Value<JsonDeserializer> createListDeserializer(Type itemType) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(itemType);
        return emit(() -> new ListDeserializer(itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createSetDeserializer(Type itemType) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(itemType);
        return emit(() -> new SetDeserializer(itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createArrayDeserializer(GenericArrayType type) {
        Value<JsonDeserializer> itemDeserializer = createDeserializer(type.getGenericComponentType());
        return emit(() -> new ArrayDeserializer(Object.class, itemDeserializer.get()));
    }

    private Value<JsonDeserializer> createObjectDeserializer(Class<?> type) {
        return emit(() -> JSON.getClassDeserializer(type));
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
