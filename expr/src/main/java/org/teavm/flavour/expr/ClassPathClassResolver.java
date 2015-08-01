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
package org.teavm.flavour.expr;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>A {@link ClassResolver} that searches classes in {@link ClassLoader}.</p>
 *
 * @author Alexey Andreev
 */
public class ClassPathClassResolver implements ClassResolver {
    private ClassLoader classLoader;
    private Map<String, String> cache = new HashMap<>();

    public ClassPathClassResolver() {
        this(ClassLoader.getSystemClassLoader());
    }

    public ClassPathClassResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public String findClass(String name) {
        String fullName = cache.get(name);
        if (fullName == null) {
            try {
                Class.forName(name, false, classLoader);
                fullName = name;
            } catch (ClassNotFoundException e) {
                fullName = "";
            }
            cache.put(name, fullName);
        }
        return !fullName.isEmpty() ? fullName : null;
    }
}
