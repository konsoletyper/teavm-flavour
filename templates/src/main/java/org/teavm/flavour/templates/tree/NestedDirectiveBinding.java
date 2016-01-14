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

/**
 *
 * @author Alexey Andreev
 */
public class NestedDirectiveBinding extends DirectivePropertyBinding {
    private List<DirectiveBinding> directives = new ArrayList<>();
    private String directiveType;
    private boolean multiple;

    public NestedDirectiveBinding(String methodOwner, String methodName, String directiveType, boolean multiple) {
        super(methodOwner, methodName);
        this.directiveType = directiveType;
        this.multiple = multiple;
    }

    public List<DirectiveBinding> getDirectives() {
        return directives;
    }

    public String getDirectiveType() {
        return directiveType;
    }

    public void setDirectiveType(String directiveType) {
        this.directiveType = directiveType;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }
}
