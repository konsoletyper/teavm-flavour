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
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.ListDeserializer;
import org.teavm.flavour.json.deserializer.MapDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.tree.Node;
import org.teavm.flavour.mp.CompileTime;
import org.teavm.flavour.mp.Emitter;
import org.teavm.flavour.mp.EmitterDiagnostics;
import org.teavm.flavour.mp.ReflectClass;
import org.teavm.flavour.mp.SourceLocation;
import org.teavm.flavour.mp.Value;
import org.teavm.flavour.mp.reflect.ReflectField;
import org.teavm.flavour.mp.reflect.ReflectMethod;
import org.teavm.flavour.rest.ResourceFactory;
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
import org.teavm.model.CallLocation;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
@CompileTime
public class FactoryEmitter {
    private EmitterDiagnostics diagnostics;
    private ResourceModelRepository resourceRepository;
    private SourceLocation location;

    public FactoryEmitter(ResourceModelRepository modelRepository) {
        this.resourceRepository = modelRepository;
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
            MethodModel model = resource.getMethods().get(method);
            location = new SourceLocation(method);

            Value<HttpMethod> httpMethod = emitHttpMethod(body, model);
            Value<String> url = emitRequestUrl(body, resource, model, args);
            Value<RequestImpl> request = body.emit(() -> new RequestImpl(httpMethod.get(), url.get()));
            request = emitHeaders(body, request, model, args);
            request = emitContent(body, request, model, args);

            Value<ResponseImpl> response = body.emit(() -> template.get().send(request.get()));
            body.emit(() -> response.get().defaultAction());

            if (!method.getReturnType().isPrimitive() || !method.getReturnType().getName().equals("void")) {
                ReflectClass<?> returnType = method.getReturnType();
                Value<Node> responseContent = body.emit(() -> response.get().getContent());
                body.returnValue(deserialize(resource, model, responseContent, returnType));
            }
        });
    }

    private Value<HttpMethod> emitHttpMethod(Emitter<?> em, MethodModel model) {
        ReflectClass<?> httpMethodClass = em.getContext().findClass(HttpMethod.class);
        ReflectField field = httpMethodClass.getDeclaredField(HttpMethod.class.getName());
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
            Value<Object> value = getParameter(em, queryParam, args);
            Value<StringBuilder> localSb = sb;
            em.emit(() -> {
                StringBuilder innerSb = localSb.get();
                if (value.get() != null) {
                    innerSb = innerSb.append(sep.get()[0]).append('=');
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
                Value<Object> localCurrent = current;
                path.getParent().acceptVisitor(this);
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
            sb = em.emit(() -> sb.get().append(constant));
        }
        return sb;
    }

    private Value<StringBuilder> appendValue(Emitter<?> em, Value<StringBuilder> sb, Value<Object> value) {
        return em.emit(() -> sb.get().append(Window.encodeURIComponent(String.valueOf(value.get()))));
    }

    private ValueEmitter deserialize(ResourceModel resource, MethodModel method, ValueEmitter value,
            ValueType target) {
        ProgramEmitter pe = value.getProgramEmitter();
        if (target instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) target).getKind()) {
                case BOOLEAN:
                    return pe.invoke(JSON.class, "deserializeBoolean", boolean.class, value);
                case BYTE:
                    return pe.invoke(JSON.class, "deserializeByte", byte.class, value);
                case SHORT:
                    return pe.invoke(JSON.class, "deserializeShort", short.class, value);
                case CHARACTER:
                    return pe.invoke(JSON.class, "deserializeChar", char.class, value);
                case INTEGER:
                    return pe.invoke(JSON.class, "deserializeInt", int.class, value);
                case LONG:
                    return pe.invoke(JSON.class, "deserializeLong", long.class, value);
                case FLOAT:
                    return pe.invoke(JSON.class, "deserializeFloat", float.class, value);
                case DOUBLE:
                    return pe.invoke(JSON.class, "deserializeDouble", double.class, value);
            }
            throw new AssertionError();
        } else {
            Method javaMethod = findMethod(new MethodReference(resource.getClassName(), method.getMethod()));
            if (javaMethod == null) {
                return pe.constantNull(target);
            }
            ValueEmitter deserializer = createDeserializer(javaMethod.getGenericReturnType(), pe);
            value = deserializer.invokeVirtual("deserialize", Object.class,
                    pe.construct(JsonDeserializerContext.class), value);
            return value.cast(target);
        }
    }

    private ValueEmitter createDeserializer(Type type, ProgramEmitter pe) {
        if (type instanceof Class<?>) {
            return createObjectDeserializer((Class<?>) type, pe);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) type;
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (paramType.getRawType().equals(Map.class)) {
                return createMapDeserializer(typeArgs[0], typeArgs[1], pe);
            } else if (paramType.getRawType().equals(List.class)) {
                return createListDeserializer(typeArgs[0], pe);
            } else if (paramType.getRawType().equals(Set.class)) {
                return createSetDeserializer(typeArgs[0], pe);
            } else {
                return createDeserializer(paramType.getRawType(), pe);
            }
        } else if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            Type upperBound = wildcard.getUpperBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(upperCls, pe);
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tyvar = (TypeVariable<?>) type;
            Type upperBound = tyvar.getBounds()[0];
            Class<?> upperCls = Object.class;
            if (upperBound instanceof Class<?>) {
                upperCls = (Class<?>) upperBound;
            }
            return createObjectDeserializer(upperCls, pe);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType array = (GenericArrayType) type;
            return createArrayDeserializer(array, pe);
        } else {
            return createObjectDeserializer(Object.class, pe);
        }
    }

    private ValueEmitter createMapDeserializer(Type keyType, Type valueType, ProgramEmitter pe) {
        ValueEmitter keyDeserializer = createDeserializer(keyType, pe).cast(JsonDeserializer.class);
        ValueEmitter valueDeserializer = createDeserializer(valueType, pe).cast(JsonDeserializer.class);
        return pe.construct(MapDeserializer.class, keyDeserializer, valueDeserializer);
    }

    private ValueEmitter createListDeserializer(Type itemType, ProgramEmitter pe) {
        ValueEmitter itemDeserializer = createDeserializer(itemType, pe).cast(JsonDeserializer.class);
        return pe.construct(ListDeserializer.class, itemDeserializer);
    }

    private ValueEmitter createSetDeserializer(Type itemType, ProgramEmitter pe) {
        ValueEmitter itemDeserializer = createDeserializer(itemType, pe).cast(JsonDeserializer.class);
        return pe.construct(SetDeserializer.class, itemDeserializer);
    }

    private ValueEmitter createArrayDeserializer(GenericArrayType type, ProgramEmitter pe) {
        ValueEmitter itemDeserializer = createDeserializer(type.getGenericComponentType(), pe)
                .cast(JsonDeserializer.class);
        return pe.construct(ArrayDeserializer.class, pe.constant(Object.class), itemDeserializer);
    }

    private ValueEmitter createObjectDeserializer(Class<?> type, ProgramEmitter pe) {
        return pe.invoke(JSON.class, "getClassDeserializer", JsonDeserializer.class, pe.constant(type));
    }

    private Method findMethod(MethodReference reference) {
        Class<?> owner = findClass(reference.getClassName());
        Class<?>[] params = new Class<?>[reference.parameterCount()];
        for (int i = 0; i < params.length; ++i) {
            params[i] = convertType(reference.parameterType(i));
        }
        while (owner != null) {
            try {
                return owner.getDeclaredMethod(reference.getName(), params);
            } catch (NoSuchMethodException e) {
                owner = owner.getSuperclass();
            }
        }
        agent.getDiagnostics().error(new CallLocation(reference), "Corresponding Java method not found");
        return null;
    }

    private Class<?> convertType(ValueType type) {
        if (type instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) type).getKind()) {
                case BOOLEAN:
                    return boolean.class;
                case BYTE:
                    return byte.class;
                case SHORT:
                    return short.class;
                case CHARACTER:
                    return char.class;
                case INTEGER:
                    return int.class;
                case LONG:
                    return long.class;
                case FLOAT:
                    return float.class;
                case DOUBLE:
                    return double.class;
            }
        } else if (type instanceof ValueType.Array) {
            Class<?> itemCls = convertType(((ValueType.Array) type).getItemType());
            return Array.newInstance(itemCls, 0).getClass();
        } else if (type instanceof ValueType.Void) {
            return void.class;
        } else if (type instanceof ValueType.Object) {
            String className = ((ValueType.Object) type).getClassName();
            return findClass(className);
        }
        throw new AssertionError("Can't convert type: " + type);
    }

    private Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, agent.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Can't find class " + name, e);
        }
    }
}
