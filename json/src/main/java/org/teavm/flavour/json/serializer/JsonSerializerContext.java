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
package org.teavm.flavour.json.serializer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Alexey Andreev
 */
public class JsonSerializerContext {
    private int lastId;
    private Map<Object, Integer> ids = new HashMap<>();
    private Set<Object> touchedObjects = new HashSet<>();

    public boolean hasId(Object object) {
        return ids.containsKey(object);
    }

    public int getId(Object object) {
        Integer id = ids.get(object);
        if (id == null) {
            id = lastId++;
            ids.put(object, id);
        }
        return id;
    }

    public void touch(Object object) {
        if (!touchedObjects.add(object)) {
            throw new IllegalArgumentException("Object has already been serialzied: " + object);
        }
    }
}
