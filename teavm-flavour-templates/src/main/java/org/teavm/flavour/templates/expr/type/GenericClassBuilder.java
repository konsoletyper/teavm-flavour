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
package org.teavm.flavour.templates.expr.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class GenericClassBuilder extends GenericTypeBuilder {
    private String name;
    private List<GenericTypeBuilder> arguments = new ArrayList<>();

    public GenericClassBuilder(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<GenericTypeBuilder> getArguments() {
        return arguments;
    }

    @Override
    GenericClass buildCacheMiss(Map<GenericTypeBuilder, GenericType> cache) {
        GenericClass cls = new GenericClass();
        cache.put(this, cls);
        cls.name = name;
        for (GenericTypeBuilder arg : arguments) {
            cls.arguments.add(arg.build(cache));
        }
        return cls;
    }


}
