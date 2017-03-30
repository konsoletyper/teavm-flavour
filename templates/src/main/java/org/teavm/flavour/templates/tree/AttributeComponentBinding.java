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
package org.teavm.flavour.templates.tree;

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.expr.Location;

public class AttributeComponentBinding {
    private Location location;
    private String className;
    private String name;
    private String elementNameMethodName;
    private List<ComponentVariableBinding> variables = new ArrayList<>();
    private List<ComponentFunctionBinding> computations = new ArrayList<>();

    public AttributeComponentBinding(String className, String name) {
        this.className = className;
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getElementNameMethodName() {
        return elementNameMethodName;
    }

    public void setElementNameMethodName(String elementNameMethodName) {
        this.elementNameMethodName = elementNameMethodName;
    }

    public List<ComponentVariableBinding> getVariables() {
        return variables;
    }

    public List<ComponentFunctionBinding> getFunctions() {
        return computations;
    }
}
