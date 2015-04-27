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

/**
 *
 * @author Alexey Andreev
 */
public abstract class DirectivePropertyBinding {
    private String methodOwner;
    private String methodName;

    public DirectivePropertyBinding(String methodOwner, String methodName) {
        this.methodOwner = methodOwner;
        this.methodName = methodName;
    }

    public String getMethodOwner() {
        return methodOwner;
    }

    public void setMethodOwner(String methodOwner) {
        this.methodOwner = methodOwner;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
}
