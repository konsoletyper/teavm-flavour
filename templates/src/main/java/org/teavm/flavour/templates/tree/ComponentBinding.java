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

public class ComponentBinding extends TemplateNode {
    private String className;
    private String name;
    private List<TemplateNode> contentNodes = new ArrayList<>();
    private String contentMethodName;
    private String elementNameMethodName;
    private List<ComponentVariableBinding> variables = new ArrayList<>();
    private List<ComponentFunctionBinding> computations = new ArrayList<>();
    private List<NestedComponentBinding> nestedComponents = new ArrayList<>();

    public ComponentBinding(String className, String name) {
        this.className = className;
        this.name = name;
    }

    public List<ComponentVariableBinding> getVariables() {
        return variables;
    }

    public List<ComponentFunctionBinding> getComputations() {
        return computations;
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

    public List<TemplateNode> getContentNodes() {
        return contentNodes;
    }

    public String getContentMethodName() {
        return contentMethodName;
    }

    public void setContentMethodName(String contentMethodName) {
        this.contentMethodName = contentMethodName;
    }

    public String getElementNameMethodName() {
        return elementNameMethodName;
    }

    public void setElementNameMethodName(String elementNameMethodName) {
        this.elementNameMethodName = elementNameMethodName;
    }

    public List<NestedComponentBinding> getNestedComponents() {
        return nestedComponents;
    }

    @Override
    public void acceptVisitor(TemplateNodeVisitor visitor) {
        visitor.visit(this);
    }
}
