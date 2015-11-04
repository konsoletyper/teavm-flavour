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

import org.teavm.dependency.DependencyAgent;
import org.teavm.flavour.json.JSON;
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
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHolder;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
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
            deserialize(responseContent, method.getResultType()).returnValue();
        }

        cls.addMethod(method);
    }

    private ValueEmitter emitHttpMethod(MethodModel model, ProgramEmitter pe) {
        return pe.getField(HttpMethod.class, model.getHttpMethod().name(), HttpMethod.class);
    }

    private ValueEmitter emitRequestUrl(ResourceModel resource, MethodModel model, ProgramEmitter pe) {
        ValueEmitter sb = pe.construct(StringBuilder.class);
        if (!resource.getPath().isEmpty()) {
            sb = appendUrlPattern(resource.getPath(), model, sb);
            sb = sb.invokeVirtual("append", StringBuilder.class, pe.constant("/"));
        }
        sb = appendUrlPattern(model.getPath(), model, sb);
        boolean first = true;
        for (ValuePath queryParam : model.getQueryParameters().values()) {
            sb = sb.invokeVirtual("append", StringBuilder.class, pe.constant((first ? "?" : "&")
                    + queryParam.getName() + "="));
            sb = appendValue(sb, getParameter(pe, queryParam));
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
            int end = findClosingBracket(pattern, index + 1);
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

    private ValueEmitter deserialize(ValueEmitter value, ValueType target) {
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
            return pe.invoke(JSON.class, "deserialize", Object.class, value, pe.constant(target)).cast(target);
        }
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
}
