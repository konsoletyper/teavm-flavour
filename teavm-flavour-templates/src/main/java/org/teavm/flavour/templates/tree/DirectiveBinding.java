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

/**
 *
 * @author Alexey Andreev
 */
public class DirectiveBinding extends TemplateNode {
    private String className;
    private List<TemplateNode> contentNodes = new ArrayList<>();
    private String contentMethodName;
    private List<DirectiveVariableBinding> variables = new ArrayList<>();
    private List<DirectiveComputationBinding> computations = new ArrayList<>();
    private List<DirectiveActionBinding> actions = new ArrayList<>();

    public DirectiveBinding(String className) {
        this.className = className;
    }

    public List<DirectiveVariableBinding> getVariables() {
        return variables;
    }

    public List<DirectiveComputationBinding> getComputations() {
        return computations;
    }

    public List<DirectiveActionBinding> getActions() {
        return actions;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
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

    @Override
    public void acceptVisitor(TemplateNodeVisitor visitor) {
        visitor.visit(this);
    }
}
