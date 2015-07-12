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
    private RepeatNode repeated;
    private int minimum;
    private int maximum;
    private boolean reluctant;

    public RepeatNode(RepeatNode repeated, int minimum, int maximum) {
        this.repeated = repeated;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public RepeatNode getRepeated() {
        return repeated;
    }

    public void setRepeated(RepeatNode repeated) {
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

    public boolean isReluctant() {
        return reluctant;
    }

    public void setReluctant(boolean reluctant) {
        this.reluctant = reluctant;
    }

    @Override
    public void acceptVisitor(NodeVisitor visitor) {
        visitor.visit(this);
    }
}
