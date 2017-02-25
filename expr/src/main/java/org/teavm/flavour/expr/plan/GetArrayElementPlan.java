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
package org.teavm.flavour.expr.plan;

public class GetArrayElementPlan extends Plan {
    private Plan array;
    private Plan index;

    public GetArrayElementPlan(Plan array, Plan index) {
        this.array = array;
        this.index = index;
    }

    public Plan getArray() {
        return array;
    }

    public void setArray(Plan array) {
        this.array = array;
    }

    public Plan getIndex() {
        return index;
    }

    public void setIndex(Plan index) {
        this.index = index;
    }

    @Override
    public void acceptVisitor(PlanVisitor visitor) {
        visitor.visit(this);
    }
}
