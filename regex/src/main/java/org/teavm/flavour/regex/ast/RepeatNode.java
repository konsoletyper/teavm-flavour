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
public class RepeatNode extends Node {
    private Node repeated;
    private int minimum;
    private int maximum;

    public RepeatNode(Node repeated, int minimum, int maximum) {
        this.repeated = repeated;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public Node getRepeated() {
        return repeated;
    }

    public void setRepeated(Node repeated) {
        this.repeated = repeated;
    }

    public int getMinimum() {
        return minimum;
    }

    public void setMinimum(int minimum) {
        this.minimum = minimum;
    }

    public int getMaximum() {
        return maximum;
    }

    public void setMaximum(int maximum) {
        this.maximum = maximum;
    }

    @Override
    public void acceptVisitor(NodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        if (minimum == 0 && maximum == 1) {
            return "optional(" + repeated + ")";
        } else if (minimum == 0 && maximum == 0) {
            return "repeat(" + repeated + ")";
        } else if (minimum == 1 && maximum == 0) {
            return "once-at-least(" + repeated + ")";
        } else {
            return "repeat(" + repeated + "){" + minimum + "," + maximum + "}";
        }
    }
}
