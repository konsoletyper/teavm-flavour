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
package org.teavm.flavour.json.emit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.flavour.mp.reflect.ReflectMethod;

/**
 *
 * @author Alexey Andreev
 */
class ClassInformation {
    String className;
    String typeName;
    ClassInformation parent;
    InheritanceInformation inheritance = new InheritanceInformation();
    Map<String, PropertyInformation> properties = new HashMap<>();
    Map<String, PropertyInformation> propertiesByOutputName = new HashMap<>();
    Visibility getterVisibility = Visibility.PUBLIC_ONLY;
    Visibility isGetterVisibility = Visibility.PUBLIC_ONLY;
    Visibility setterVisibility = Visibility.ANY;
    Visibility creatorVisibility = Visibility.NONE;
    Visibility fieldVisibility = Visibility.NONE;
    IdGeneratorType idGenerator = IdGeneratorType.NONE;
    String idProperty;
    ReflectMethod constructor;
    List<PropertyInformation> constructorArgs = new ArrayList<>();
}
