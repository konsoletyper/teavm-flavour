/*
 *  Copyright 2016 Alexey Andreev.
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

public class NestedComponentBinding extends ComponentPropertyBinding {
    private List<ComponentBinding> components = new ArrayList<>();
    private String componentType;
    private boolean multiple;

    public NestedComponentBinding(String methodOwner, String methodName, String componentType, boolean multiple) {
        super(methodOwner, methodName);
        this.componentType = componentType;
        this.multiple = multiple;
    }

    public List<ComponentBinding> getComponents() {
        return components;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }
}
