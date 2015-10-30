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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.teavm.dependency.DependencyAgent;
import org.teavm.flavour.routing.Route;
import org.teavm.flavour.routing.metadata.ParameterDescriptor;
import org.teavm.flavour.routing.metadata.RouteDescriptor;
import org.teavm.flavour.routing.metadata.RouteSetDescriptor;
import org.teavm.jso.browser.Window;
import org.teavm.model.AccessLevel;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.FieldHolder;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
public class RouteProxyEmitter {
    public static final String ROUTING_CLASS = Route.class.getPackage().getName() + ".Routing";
    private DependencyAgent agent;
    private ClassReaderSource classSource;
    private int suffixGenerator;

    public RouteProxyEmitter(DependencyAgent agent, ClassReaderSource classSource) {
        this.agent = agent;
        this.classSource = classSource;
    }

    public String emitProxy(RouteSetDescriptor descriptor) {
        ClassHolder cls = new ClassHolder(Route.class.getPackage().getName() + ".RouteProxy" + suffixGenerator++);
        cls.setLevel(AccessLevel.PUBLIC);
        cls.setParent("java.lang.Object");
        cls.getInterfaces().add(descriptor.getClassName());

        FieldHolder consumerField = new FieldHolder("consumer");
        consumerField.setLevel(AccessLevel.PRIVATE);
        consumerField.setType(ValueType.parse(Consumer.class));
        cls.addField(consumerField);

        cls.addMethod(createConstructor(cls.getName()));
        for (MethodHolder method : createWorkers(cls.getName(), descriptor)) {
            cls.addMethod(method);
        }

        agent.submitClass(cls);
        return cls.getName();
    }

    private MethodHolder createConstructor(String className) {
        MethodHolder ctor = new MethodHolder("<init>", ValueType.parse(Consumer.class), ValueType.VOID);
        ctor.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(ctor, classSource);

        ValueEmitter thisVar = pe.var(0, ValueType.object(className));
        ValueEmitter consumerVar = pe.var(1, Consumer.class);
        thisVar.invokeSpecial("<init>");
        thisVar.setField("consumer", consumerVar);
        pe.exit();

        return ctor;
    }

    private Collection<MethodHolder> createWorkers(String className, RouteSetDescriptor descriptor) {
        List<MethodHolder> workers = new ArrayList<>();
        for (RouteDescriptor routeDescriptor : descriptor.getRoutes()) {
            workers.add(createWorker(className, routeDescriptor));
        }
        return workers;
    }

    private MethodHolder createWorker(String className, RouteDescriptor descriptor) {
        MethodDescriptor methodDesc = descriptor.getMethod();
        MethodHolder method = new MethodHolder(methodDesc);
        method.setLevel(AccessLevel.PUBLIC);
        ProgramEmitter pe = ProgramEmitter.create(method, classSource);
        ValueEmitter thisVar = pe.var(0, ValueType.object(className));
        ValueEmitter sb = pe.construct(StringBuilder.class);

        sb = sb.invokeVirtual("append", StringBuilder.class, pe.constant(descriptor.pathPart(0)));
        for (int i = 1; i < descriptor.pathPartCount(); ++i) {
            ParameterDescriptor param = descriptor.parameter(i - 1);
            if (param != null) {
                ValueEmitter paramVar = pe.var(param.getJavaIndex() + 1, param.getValueType());
                sb = sb.invokeVirtual("append", StringBuilder.class, emitParam(paramVar, param));
            }

            sb = sb.invokeVirtual("append", StringBuilder.class, pe.constant(descriptor.pathPart(i)));
        }

        thisVar.getField("consumer", Consumer.class).invokeVirtual("accept",
                sb.invokeVirtual("toString", String.class).cast(Object.class));
        pe.exit();
        return method;
    }

    private ValueEmitter emitParam(ValueEmitter var, ParameterDescriptor param) {
        ProgramEmitter pe = var.getProgramEmitter();
        switch (param.getType()) {
            case STRING:
                return pe.invoke(Window.class, "encodeURIComponent", String.class, var);
            case BYTE:
                return pe.invoke(Byte.class, "toString", String.class, var);
            case SHORT:
                return pe.invoke(Short.class, "toString", String.class, var);
            case INTEGER:
                return pe.invoke(Integer.class, "toString", String.class, var);
            case LONG:
                return pe.invoke(Long.class, "toString", String.class, var);
            case FLOAT:
                return pe.invoke(Float.class, "toString", String.class, var);
            case DOUBLE:
                return pe.invoke(Double.class, "toString", String.class, var);
            case DATE:
                return pe.invoke(ROUTING_CLASS, "dateToString", ValueType.parse(String.class),
                        var.invokeVirtual("getTime", long.class));
            case ENUM: {
                ValueEmitter enumValue = var.invokeVirtual("name", String.class);
                return pe.invoke(Window.class, "encodeURIComponent", String.class, enumValue);
            }
            case BIG_DECIMAL:
                return var.invokeVirtual("toString", String.class);
            case BIG_INTEGER:
                return var.invokeVirtual("toString", String.class);
            default:
                throw new AssertionError("Unknown type: " + param.getType());
        }
    }
}
