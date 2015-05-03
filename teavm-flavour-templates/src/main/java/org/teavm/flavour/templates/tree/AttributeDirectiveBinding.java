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
public class AttributeDirectiveBinding {
    private String className;
    private String name;
    private String directiveNameMethodName;
    private List<DirectiveVariableBinding> variables = new ArrayList<>();
    private List<DirectiveComputationBinding> computations = new ArrayList<>();
    private List<DirectiveActionBinding> actions = new ArrayList<>();

    public AttributeDirectiveBinding(String className, String name) {
        this.className = className;
        this.name = name;
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

    public String getDirectiveNameMethodName() {
        return directiveNameMethodName;
    }

    public void setDirectiveNameMethodName(String directiveNameMethodName) {
        this.directiveNameMethodName = directiveNameMethodName;
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
}
