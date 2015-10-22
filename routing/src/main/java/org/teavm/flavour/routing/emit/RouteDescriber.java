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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.routing.Path;
import org.teavm.flavour.routing.PathParameter;
import org.teavm.model.AnnotationContainerReader;
import org.teavm.model.AnnotationReader;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassReader;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodReader;

/**
 *
 * @author Alexey Andreev
 */
class RouteDescriber {
    private ClassReaderSource classSource;
    private Diagnostics diagnostics;
    private CallLocation location;

    private RouteSetDescriptor parseRouteSet(String className) {
        ClassReader cls = classSource.get(className);
        RouteSetDescriptor descriptor = new RouteSetDescriptor(className);

        for (MethodReader method : cls.getMethods()) {
            if (method.hasModifier(ElementModifier.STATIC) || !method.hasModifier(ElementModifier.ABSTRACT)) {
                continue;
            }

        }

        return descriptor;
    }

    private RouteDescriptor parseRoute(MethodReader method) {
        List<String> pathParts = new ArrayList<>();
        List<ParameterDescriptor> parameters = new ArrayList<>();
        AnnotationReader pathAnnot = method.getAnnotations().get(Path.class.getName());
        if (pathAnnot == null) {
            diagnostics.error(location, "Missing {{c0}} annotation on {{m1}}", Path.class.getName(),
                    method.getReference());
            return null;
        }

        AnnotationContainerReader[] parameterAnnotations = new AnnotationContainerReader[0];
        Map<String, Integer> parameterNames = new HashMap<>();
        for (int i = 0; i < method.parameterCount(); ++i) {
            AnnotationReader parameterAnnot = parameterAnnotations[i].get(PathParameter.class.getName());
            if (parameterAnnot == null) {
                diagnostics.error(location, "Missing {{c0}} annotation on parameter " + (i + 1) + " of {{m1}}",
                        PathParameter.class.getName(), method.getReference());
                continue;
            }

            String alias = parameterAnnot.getValue("value").getString();
            if (!isProperAliasName(alias)) {
                diagnostics.error(location, "Wrong name (" + alias + ") of parameter " + (i + 1) + " of {{m1}}",
                        method.getReference());
            }

            if (parameterNames.containsKey(alias)) {
                diagnostics.error(location, "Same parameter name (" + alias + ") used on both parameters " + (i + 1)
                        + " and "  + (parameterNames.get(alias) + 1) + " of {{m0}}", method.getReference());
                continue;
            }
        }

        String path = pathAnnot.getValue("value").getString();
        int index = 0;
        while (index < path.length()) {
            int next = path.indexOf('{', index);
            if (next < 0) {
                break;
            }
            pathParts.add(path.substring(index, next));
            int sep = next + 1;
            while (sep < path.length()) {
                if (sep == ':') {
                    break;
                } else if (sep == '}') {
                    break;
                }
            }
            if (sep == path.length()) {
                diagnostics.error(location, "Wrong path format for {{m0}}: open curly brace has not matching pair",
                        method.getReference());
                return null;
            }
        }
        pathParts.add(path.substring(index));

        return new RouteDescriptor(method.getDescriptor(),
                pathParts.toArray(new String[0]), parameters.toArray(new ParameterDescriptor[0]));
    }

    private boolean isProperAliasName(String alias) {
        if (!Character.isJavaIdentifierStart(alias.charAt(0))) {
            return false;
        }
        for (int i = 1; i < alias.length(); ++i) {
            if (!Character.isJavaIdentifierPart(alias.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
