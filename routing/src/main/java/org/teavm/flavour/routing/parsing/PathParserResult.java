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
package org.teavm.flavour.routing.parsing;

public class PathParserResult {
    private int caseIndex;
    private int[] startIndexes;
    private int[] endIndexes;

    PathParserResult(int caseIndex, int[] startIndexes, int[] endIndexes) {
        this.caseIndex = caseIndex;
        this.startIndexes = startIndexes;
        this.endIndexes = endIndexes;
    }

    public int getCaseIndex() {
        return caseIndex;
    }

    public int start(int index) {
        return startIndexes[index];
    }

    public int end(int index) {
        return endIndexes[index];
    }
}
