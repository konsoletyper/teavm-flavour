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
import org.teavm.dependency.DependencyAgent;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.deserializer.ArrayDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializer;
import org.teavm.flavour.json.deserializer.JsonDeserializerContext;
import org.teavm.flavour.json.deserializer.ListDeserializer;
import org.teavm.flavour.json.deserializer.MapDeserializer;
import org.teavm.flavour.json.deserializer.SetDeserializer;
import org.teavm.flavour.json.tree.Node;
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
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.emit.PhiEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
public class FactoryEmitter {
    private ResourceModelRepository resourceRepository;
    private DependencyAgent agent;
    private CallLocation location;

    public FactoryEmitter(ResourceModelRepository modelRepository, DependencyAgent agent) {
        this.resourceRepository = modelRepository;
        this.agent = agent;
    }

    public String emitFactory(String className) {
        ClassHolder cls = new ClassHolder(className + "$Flavour_RESTFactory");
        cls.setParent(FactoryTemplate.class.getName());
        cls.setLevel(AccessLevel.PUBLIC);

        emitFactoryConstructor(cls);
        emitFactoryWorker(cls, className);

        agent.submitClass(cls);
        return cls.getName();
    }

    private void emitFactoryConstructor(ClassHolder cls) {
        MethodHolder method = new MethodHolder("<init>", ValueType.VOID);
        method.setLevel(AccessLevel.PUBLIC);

        ProgramEmitter pe = ProgramEmitter.create(method, agent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);
        thisVar.invokeSpecial(FactoryTemplate.class, "<init>");
        pe.exit();

        cls.addMethod(method);
    }

    private void emitFactoryWorker(ClassHolder cls, String className) {
        MethodHolder method = new MethodHolder("createResourceImpl", ValueType.parse(String.class),
                ValueType.parse(ProxyTemplate.class));
        method.setLevel(AccessLevel.PUBLIC);

        String proxyClass = emitProxy(className, cls.getName());
        ProgramEmitter pe = ProgramEmitter.create(method, agent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter path = pe.var(1, String.class);
        pe.construct(proxyClass, thisVar, path).returnValue();

        cls.addMethod(method);
    }

    private String emitProxy(String className, String factoryClassName) {
        ResourceModel resource = resourceRepository.getResource(className);
        ClassHolder cls = new ClassHolder(className + "$Flavour_RESTProxy");
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent(ProxyTemplate.class.getName());
        cls.getInterfaces().add(className);

        emitProxyConstructor(cls, factoryClassName);
        for (MethodModel methodModel : resource.getMethods().values()) {
            location = new CallLocation(new MethodReference(className, methodModel.getMethod()));
            emitProxyWorker(cls, resource, methodModel);
        }

        agent.submitClass(cls);
        return cls.getName();
    }

    private void emitProxyConstructor(ClassHolder cls, String factoryClassName) {
        MethodHolder method = new MethodHolder("<init>", ValueType.object(factoryClassName),
                ValueType.parse(String.class), ValueType.VOID);
        method.setLevel(AccessLevel.PUBLIC);

        ProgramEmitter pe = ProgramEmitter.create(method, agent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter param = pe.var(1, ValueType.object(factoryClassName));
        ValueEmitter prefix = pe.var(2, String.class);
        thisVar.invokeSpecial(ProxyTemplate.class, "<init>", param.cast(FactoryTemplate.class), prefix);
        pe.exit();

        cls.addMethod(method);
    }

    private void emitProxyWorker(ClassHolder cls, ResourceModel resource, MethodModel model) {
        MethodHolder method = new MethodHolder(model.getMethod());
        method.setLevel(AccessLevel.PUBLIC);

        ProgramEmitter pe = ProgramEmitter.create(method, agent.getClassSource());
        ValueEmitter thisVar = pe.var(0, cls);
        ValueEmitter httpMethod = emitHttpMethod(model, pe);
        ValueEmitter url = emitRequestUrl(resource, model, pe);
        ValueEmitter request = pe.construct(RequestImpl.class, httpMethod, url);
        request = emitHeaders(request, model, pe);
        request = emitContent(request, model, pe);

        ValueEmitter response = thisVar.invokeVirtual("send", ResponseImpl.class, request);
        response.invokeVirtual("defaultAction");
        if (method.getResultType() == ValueType.VOID) {
            pe.exit();
        } else {
            ValueEmitter responseContent = response.invokeVirtual("getContent", Node.class);
            deserialize(resource, model, responseContent, method.getResultType()).returnValue();
        }

        cls.addMethod(method);
    }

    private ValueEmitter emitHttpMethod(MethodModel model, ProgramEmitter pe) {
        pe.initClass(HttpMethod.class.getName());
        return pe.getField(HttpMethod.class, model.getHttpMethod().name(), HttpMethod.class);
    }

    private ValueEmitter emitRequestUrl(ResourceModel resource, MethodModel model, ProgramEmitter pe) {
        ValueEmitter sb = pe.construct(StringBuilder.class);
        if (!resource.getPath().isEmpty()) {
            sb = appendUrlPattern(resource.getPath(), model, sb);
            if (!model.getPath().isEmpty()) {
                sb = sb.invokeVirtual("append", StringBuilder.class, pe.constant("/"));
            }
        }
        sb = appendUrlPattern(model.getPath(), model, sb);
        ValueEmitter sep = pe.constant("?");
        for (ValuePath queryParam : model.getQueryParameters().values()) {
            ValueEmitter value = getParameter(pe, queryParam);
            if (value.getType() instanceof ValueType.Primitive) {
                sb = sb.invokeVirtual("append", StringBuilder.class, sep)
                        .invokeVirtual("append", StringBuilder.class, pe.constant(queryParam.getName() + "="));
                sb = appendValue(sb, getParameter(pe, queryParam));
                sep = pe.constant("&");
            } else {
                BasicBlock current = pe.getBlock();
                BasicBlock joint = pe.prepareBlock();
                pe.enter(joint);
                PhiEmitter nextSep = pe.phi(String.class);
                pe.enter(current);
                ValueEmitter localSb = sb;
                ValueEmitter localSep = sep;
                pe.when(value.isNotNull()).thenDo(() -> {
                    localSb.invokeVirtual("append", StringBuilder.class, localSep)
                            .invokeVirtual("append", StringBuilder.class, pe.constant(queryParam.getName() + "="));
                    appendValue(localSb, getParameter(pe, queryParam));
                    pe.constant("&").propagateTo(nextSep);
                    pe.jump(joint);
                }).elseDo(() -> {
                    localSep.propagateTo(nextSep);
                    pe.jump(joint);
                });
                pe.enter(joint);
                sep = nextSep.getValue();
            }

        }
        return sb.invokeVirtual("toString", String.class);
    }

    private ValueEmitter appendUrlPattern(String pattern, MethodModel model, ValueEmitter sb) {
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
            int sep = pattern.indexOf(':', next);
            if (sep < 0 || sep > end) {
                sep = end;
            }
            String name = pattern.substring(next + 1, sep);

            ValuePath value = model.getPathParameters().get(name);
            if (value == null) {
                agent.getDiagnostics().error(location, "Unknown parameter referred by path: " + name);
            } else {
                sb = appendValue(sb, getParameter(sb.getProgramEmitter(), value));
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

    private ValueEmitter emitHeaders(ValueEmitter request, MethodModel model, ProgramEmitter pe) {
        for (ValuePath header : model.getHeaderParameters().values()) {
            request = request.invokeVirtual("setHeader", RequestImpl.class, pe.constant(header.getName()),
                    valueToString(getParameter(pe, header)));
        }
        return request;
    }

    private ValueEmitter emitContent(ValueEmitter request, MethodModel model, ProgramEmitter pe) {
        if (model.getBody() != null) {
            ValueEmitter content = serialize(getParameter(pe, model.getBody()));
            request = request.invokeVirtual("setContent", RequestImpl.class, content);
        }
        return request;
    }

    private ValueEmitter getParameter(ProgramEmitter pe, ValuePath value) {
        class EmittingVisitor implements ValuePathVisitor {
            ValueEmitter current;
            @Override
            public void visit(PropertyValuePath path) {
                path.getParent().acceptVisitor(this);
                PropertyModel property = path.getProperty();
                if (property.getGetter() != null) {
                    current = current.invokeVirtual(property.getGetter().getName(), property.getType());
                } else {
                    current = current.getField(property.getField().getName(), property.getType());
                }
            }
            @Override
            public void visit(RootValuePath path) {
                current = pe.var(path.getParameter().getIndex() + 1, path.getType());
            }
        }
        EmittingVisitor visitor = new EmittingVisitor();
        value.acceptVisitor(visitor);
        return visitor.current;
    }


    private ValueEmitter appendConstant(ValueEmitter sb, String constant) {
        if (!constant.isEmpty()) {
            sb = sb.invokeVirtual("append", StringBuilder.class, sb.getProgramEmitter().constant(constant));
        }
        return sb;
    }

    private ValueEmitter appendValue(ValueEmitter sb, ValueEmitter value) {
        ProgramEmitter pe = value.getProgramEmitter();
        value = valueToString(value);
        value = pe.invoke(Window.class, "encodeURIComponent", String.class, value);
        return sb.invokeVirtual("append", StringBuilder.class, value);
    }

    private ValueEmitter valueToString(ValueEmitter value) {
        ProgramEmitter pe = value.getProgramEmitter();
        if (!(value.getType() instanceof ValueType.Primitive)) {
            value = value.cast(Object.class);
        } else {
            switch (((ValueType.Primitive) value.getType()).getKind()) {
                case BYTE:
                case SHORT:
                    value = value.cast(int.class);
                    break;
                default:
                    break;
            }
        }
        value = pe.invoke(String.class, "valueOf", String.class, value);
        return value;
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

    private ValueEmitter serialize(ValueEmitter value) {
        return value.getProgramEmitter().invoke(JSON.class, "serialize", Node.class, box(value).cast(Object.class));
    }

    private ValueEmitter box(ValueEmitter value) {
        ProgramEmitter pe = value.getProgramEmitter();
        if (value.getType() instanceof ValueType.Primitive) {
            switch (((ValueType.Primitive) value.getType()).getKind()) {
                case BOOLEAN:
                    return pe.invoke(Boolean.class, "valueOf", Boolean.class, value);
                case BYTE:
                    return pe.invoke(Byte.class, "valueOf", Byte.class, value);
                case SHORT:
                    return pe.invoke(Short.class, "valueOf", Short.class, value);
                case CHARACTER:
                    return pe.invoke(Character.class, "valueOf", Character.class, value);
                case INTEGER:
                    return pe.invoke(Integer.class, "valueOf", Integer.class, value);
                case LONG:
                    return pe.invoke(Long.class, "valueOf", Long.class, value);
                case FLOAT:
                    return pe.invoke(Float.class, "valueOf", Float.class, value);
                case DOUBLE:
                    return pe.invoke(Double.class, "valueOf", Double.class, value);
            }
        }
        return value;
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
