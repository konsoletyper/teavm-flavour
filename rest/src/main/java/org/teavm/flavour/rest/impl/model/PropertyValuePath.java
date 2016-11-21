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
package org.teavm.flavour.rest.impl.model;

import org.teavm.metaprogramming.ReflectClass;

public class PropertyValuePath extends ValuePath {
    private ValuePath parent;
    private PropertyModel property;

    PropertyValuePath(ValuePath parent, PropertyModel property) {
        this.parent = parent;
        this.property = property;
    }

    @Override
    public Usage getUsage() {
        return property.usage;
    }

    @Override
    public String getName() {
        return property.targetName;
    }

    @Override
    public ValuePath getParent() {
        return parent;
    }

    public PropertyModel getProperty() {
        return property;
    }

    @Override
    public ReflectClass<?> getType() {
        return property.getType();
    }

    @Override
    public String toString() {
        return parent.toString() + "." + property.name;
    }

    @Override
    public void acceptVisitor(ValuePathVisitor visitor) {
        visitor.visit(this);
    }
}
