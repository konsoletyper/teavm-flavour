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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.teavm.dependency.DependencyAgent;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.routing.PathSet;
import org.teavm.flavour.routing.metadata.ParameterDescriptor;
import org.teavm.flavour.routing.metadata.RouteDescriber;
import org.teavm.flavour.routing.metadata.RouteDescriptor;
import org.teavm.flavour.routing.metadata.RouteSetDescriptor;
import org.teavm.flavour.routing.parsing.PathBuilder;
import org.teavm.flavour.routing.parsing.PathParser;
import org.teavm.flavour.routing.parsing.PathParserBuilder;
import org.teavm.flavour.routing.parsing.PathParserEmitter;
import org.teavm.flavour.routing.parsing.PathParserResult;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ChooseEmitter;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.emit.ValueEmitter;

/**
 *
 * @author Alexey Andreev
 */
class RouteParserEmitter {
    private DependencyAgent agent;
    private ClassReaderSource classSource;
    private Diagnostics diagnostics;
    private PathParserEmitter pathParserEmitter;
    private RouteDescriber describer;
    private CallLocation location;
    private Map<String, String> routeParsers = new HashMap<>();

    public RouteParserEmitter(DependencyAgent agent) {
        this.agent = agent;
        classSource = agent.getClassSource();
        diagnostics = agent.getDiagnostics();
        pathParserEmitter = new PathParserEmitter(agent);
    }

    public void emitParser(String className, CallLocation location) {
        this.location = location;
        Set<ClassReader> pathSets = new HashSet<>();
        getPathSets(className, pathSets, new HashSet<>());
        if (pathSets.isEmpty()) {
            diagnostics.error(location, "Given handler {{c0}} does not implement path set", className);
            return;
        } else if (pathSets.size() > 1) {
            Iterator<ClassReader> iter = pathSets.iterator();
            ClassReader example1 = iter.next();
            ClassReader example2 = iter.next();
            diagnostics.error(location, "Given handler {{c0}} implements several path sets. Examples are {{c1}} "
                    + "and {{c2}}", className, example1, example2);
            return;
        }

        ClassReader pathSet = pathSets.iterator().next();
        if (routeParsers.containsKey(pathSet.getName())) {
            return;
        }

        describer = new RouteDescriber(classSource, diagnostics, location);
        RouteSetDescriptor descriptor = describer.describeRouteSet(pathSet.getName());
        if (descriptor == null) {
            routeParsers.put(pathSet.getName(), "");
            return;
        }


    }

    private void emitConstructor(ProgramEmitter pe, String className, RouteSetDescriptor descriptor) {
        PathParser pathParser = createPathParser(descriptor);
        ValueEmitter thisVar = pe.var(0, ValueType.object(className));
        thisVar.invokeSpecial(Object.class, "<init>");
        thisVar.setField("pathParser", pathParserEmitter.emitWorker(pe, pathParser));
        pe.exit();
    }

    private void emitWorker(ProgramEmitter pe, String className, String routeType, RouteSetDescriptor descriptor) {
        ValueEmitter thisVar = pe.var(0, ValueType.object(className));
        ValueEmitter pathVar = pe.var(1, String.class);
        ValueEmitter routeVar = pe.var(2, ValueType.object(routeType));
        ValueEmitter parserVar = thisVar.getField("pathParser", PathParser.class);

        ValueEmitter parseResultVar = parserVar.invokeVirtual("parse", PathParserResult.class, pathVar);
        ValueEmitter caseVar = parseResultVar.invokeVirtual("getCaseIndex", int.class);
        pe.when(caseVar.isLessThan(pe.constant(0)))
                .thenDo(() -> pe.constant(0).returnValue());

        ChooseEmitter choice = pe.choice(caseVar);
        for (int i = 0; i < descriptor.getRoutes().size(); ++i) {
            RouteDescriptor routeDescriptor = descriptor.getRoutes().get(i);
            MethodDescriptor method = routeDescriptor.getMethod();
            ValueEmitter[] values = new ValueEmitter[method.parameterCount()];
            for (int j = 1; j < routeDescriptor.pathPartCount(); ++j) {
                ParameterDescriptor param = routeDescriptor.parameter(j - 1);
            }
        }
        choice.otherwise(() -> pe.constant(0).returnValue());

        pe.constant(1).returnValue();
    }

    private PathParser createPathParser(RouteSetDescriptor descriptor) {
        PathParserBuilder pathParserBuilder = new PathParserBuilder();
        for (RouteDescriptor routeDescriptor : descriptor.getRoutes()) {
            PathBuilder pathBuilder = pathParserBuilder.path();
            pathBuilder.text(routeDescriptor.pathPart(0));
            for (int i = 1; i < routeDescriptor.pathPartCount(); ++i) {
                ParameterDescriptor param = routeDescriptor.parameter(i - 1);
                pathBuilder.escapedRegex(param.getEffectivePattern());
                pathBuilder.text(routeDescriptor.pathPart(i));
            }
        }
        return pathParserBuilder.buildUnprepared();
    }

    private void getPathSets(String className, Set<ClassReader> pathSets, Set<String> visited) {
        if (!visited.add(className)) {
            return;
        }

        ClassReader cls = classSource.get(className);
        if (cls == null) {
            return;
        }

        if (cls.getAnnotations().get(PathSet.class.getName()) != null) {
            pathSets.add(cls);
            return;
        }

        if (!cls.getParent().equals(cls.getName()) && cls.getParent() != null) {
            getPathSets(cls.getParent(), pathSets, visited);
        }
        for (String iface : cls.getInterfaces()) {
            getPathSets(iface, pathSets, visited);
        }
    }
}
