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

import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.dependency.DependencyNode;
import org.teavm.dependency.MethodDependency;
import org.teavm.flavour.routing.Route;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public class RoutingDependencyListener extends AbstractDependencyListener {
    private static final String ROUTING_CLASS = Route.class.getPackage().getName() + ".Routing";
    private static final MethodReference IMPL_METHOD_REF = new MethodReference(ROUTING_CLASS, "getImplementorImpl",
            ValueType.parse(String.class), ValueType.object(RouteImplementorEmitter.PATH_IMPLEMENTOR_CLASS));
    RouteImplementorEmitter emitter;
    DependencyAgent agent;
    private boolean getImplementorReached;

    @Override
    public void started(DependencyAgent agent) {
        this.agent = agent;
        emitter = new RouteImplementorEmitter(agent);
    }

    @Override
    public void methodReached(DependencyAgent agent, MethodDependency method, CallLocation location) {
        MethodReference ref = method.getReference();
        if (!ref.getClassName().equals(ROUTING_CLASS)) {
            return;
        }
        if (ref.getName().equals("getImplementor")) {
            reachGetImplementor(method, location);
        }
    }

    @Override
    public void completing(DependencyAgent agent) {
        if (getImplementorReached) {
            agent.submitMethod(IMPL_METHOD_REF, emitter.emitGetter(IMPL_METHOD_REF));
        }
    }

    private void reachGetImplementor(MethodDependency method, CallLocation location) {
        if (getImplementorReached) {
            return;
        }
        getImplementorReached = true;
        DependencyNode node = method.getVariable(1);

        MethodDependency implMethod = agent.linkMethod(IMPL_METHOD_REF, null);
        node.addConsumer(type -> {
            String implementorType = emitter.emitParser(type.getName(), location);
            if (implementorType != null) {
                implMethod.getResult().propagate(agent.getType(implementorType));
                MethodDependency ctor = agent.linkMethod(new MethodReference(implementorType, "<init>", ValueType.VOID),
                        location);
                ctor.propagate(0, implementorType);
                ctor.getThrown().connect(implMethod.getThrown());
                ctor.use();
            }
        });

        MethodDependency equalsDep = agent.linkMethod(new MethodReference(String.class, "equals", Object.class,
                boolean.class), location);
        equalsDep.getThrown().connect(implMethod.getThrown());
        equalsDep.use();

        MethodDependency hashCodeDep = agent.linkMethod(new MethodReference(String.class, "hashCode", int.class),
                location);
        hashCodeDep.getThrown().connect(implMethod.getThrown());
        hashCodeDep.use();
    }
}
