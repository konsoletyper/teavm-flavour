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
package org.teavm.flavour.regex.ast;

/**
 *
 * @author Alexey Andreev
 */
public class CapturingGroupNode extends Node {
    private String name;
    private int index = -1;
    private Node captured;

    public CapturingGroupNode(String name, Node captured) {
        this.name = name;
        this.captured = captured;
    }

    public CapturingGroupNode(int index, Node captured) {
        this.index = index;
        this.captured = captured;
    }

    public CapturingGroupNode(Node captured) {
        this.captured = captured;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Node getCaptured() {
        return captured;
    }

    public void setCaptured(Node captured) {
        this.captured = captured;
    }

    @Override
    public void acceptVisitor(NodeVisitor visitor) {
        visitor.visit(this);
    }
}
