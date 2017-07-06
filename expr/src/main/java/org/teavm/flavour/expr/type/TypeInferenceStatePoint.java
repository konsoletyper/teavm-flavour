/*
 *  Copyright 2017 Alexey Andreev.
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
package org.teavm.flavour.expr.type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TypeInferenceStatePoint implements AutoCloseable {
    TypeInference typeInference;
    int index;
    Set<TypeVar> addedTypeVars;
    Map<TypeInference.InferenceVar, TypeInference.InferenceVar> inferenceVarsBackup;
    Map<TypeVar, TypeInference.InferenceVar> inferenceVarMapBackup;

    TypeInferenceStatePoint(TypeInference typeInference, int index) {
        this.typeInference = typeInference;
        this.index = index;
    }

    void addTypeVar(TypeVar typeVar) {
        if (addedTypeVars == null) {
            addedTypeVars = new HashSet<>();
        }
        addedTypeVars.add(typeVar);
    }

    void backup(TypeInference.InferenceVar inferenceVar) {
        if (inferenceVarsBackup == null) {
            inferenceVarsBackup = new HashMap<>();
        }
        if (!inferenceVarsBackup.containsKey(inferenceVar)) {
            inferenceVarsBackup.put(inferenceVar, inferenceVar.backup());
        }
    }

    public void restoreTo() {
        if (typeInference.statePoints.size() <= index) {
            throw new IllegalStateException("This state point already has been rolled back");
        }

        while (typeInference.statePoints.size() > index + 1) {
            typeInference.rollBack(typeInference.statePoints.remove(typeInference.statePoints.size() - 1));
        }

        typeInference.currentStatePoint = new TypeInferenceStatePoint(typeInference, typeInference.statePoints.size());
        typeInference.statePoints.add(typeInference.currentStatePoint);
    }

    @Override
    public void close() {
        restoreTo();
    }
}
