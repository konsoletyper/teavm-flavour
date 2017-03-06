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
import java.util.Collections;
import java.util.List;
import org.teavm.flavour.expr.Location;

public class DOMElement extends TemplateNode {
    private String name;
    private List<TemplateNode> childNodes = new ArrayList<>();
    private List<DOMAttribute> attributes = new ArrayList<>();
    private List<DOMAttribute> readonlyAttributes = Collections.unmodifiableList(attributes);
    private List<AttributeDirectiveBinding> attributeDirectives = new ArrayList<>();

    public DOMElement(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<TemplateNode> getChildNodes() {
        return childNodes;
    }

    public DOMAttribute getAttribute(String name) {
        for (DOMAttribute attr : attributes) {
            if (attr.getName().equals(name)) {
                return attr;
            }
        }
        return null;
    }

    public DOMAttribute createAttribute(String name) {
        return createAttribute(name, null);
    }

    public DOMAttribute createAttribute(String name, Location location) {
        DOMAttribute attribute = getAttribute(name);
        if (attribute == null) {
            attribute = new DOMAttribute(name, "");
            attribute.setLocation(location);
            attributes.add(attribute);
        }
        return attribute;
    }

    public void deleteAttribute(String name) {
        for (int i = 0; i < attributes.size(); ++i) {
            if (attributes.get(i).getName().equals(name)) {
                attributes.remove(i);
                break;
            }
        }
    }

    public void setAttribute(String name, String value) {
        createAttribute(name).setValue(value);
    }

    public void setAttribute(String name, String value, Location location) {
        createAttribute(name, location).setValue(value);
    }

    public List<DOMAttribute> getAttributes() {
        return readonlyAttributes;
    }

    public List<AttributeDirectiveBinding> getAttributeDirectives() {
        return attributeDirectives;
    }


    @Override
    public void acceptVisitor(TemplateNodeVisitor visitor) {
        visitor.visit(this);
    }
}
