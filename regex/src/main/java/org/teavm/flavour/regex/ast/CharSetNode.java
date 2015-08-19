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

import org.teavm.flavour.regex.core.SetOfChars;

/**
 *
 * @author Alexey Andreev
 */
public class CharSetNode extends Node {
    private SetOfChars charSet;

    public CharSetNode(SetOfChars charSet) {
        this.charSet = charSet;
    }

    public SetOfChars getCharSet() {
        return charSet;
    }

    public void setCharSet(SetOfChars charSet) {
        this.charSet = charSet;
    }

    @Override
    public void acceptVisitor(NodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "chars:" + charSet.toString();
    }
}
