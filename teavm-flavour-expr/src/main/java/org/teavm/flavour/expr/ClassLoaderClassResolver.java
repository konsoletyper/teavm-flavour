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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>A {@link ClassResolver} that searches classes in {@link ClassLoader}. It is also capable of
 * searching the class by its simple names, if it was imported directly or its package was imported.</p>
 *
 * @see #importClass(String)
 * @see #importPackage(String)
 *
 * @author Alexey Andreev
 */
public class ClassLoaderClassResolver implements ClassResolver {
    private ClassLoader classLoader;
    private Map<String, String> cache = new HashMap<>();
    private List<Import> imports = new ArrayList<>();

    public ClassLoaderClassResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * <p>Adds imported class, so that this class will be resolved by its simple name.</p>
     *
     * @param name the name of the class to import.
     * @return this instance.
     */
    public ClassLoaderClassResolver importClass(String name) {
        int index = name.lastIndexOf('.');
        if (index >= 0) {
            Import imp = new Import();
            imp.name = name;
            imp.className = name.substring(index + 1);
            imports.add(imp);
        }
        return this;
    }

    /**
     * <p>Adds imported package, so that this any class within this package will be found by its simple name.</p>
     *
     * @param name the name of the package to import.
     * @return this instance.
     */
    public ClassLoaderClassResolver importPackage(String name) {
        Import imp = new Import();
        imp.name = name;
        imports.add(imp);
        return this;
    }

    @Override
    public String findClass(String name) {
        String fullName = cache.get(name);
        if (fullName == null) {
            try {
                Class.forName(name, false, classLoader);
                fullName = name;
            } catch (ClassNotFoundException e) {
                if (isSimpleName(name)) {
                    fullName = findClassBySimpleName(name);
                }
                if (fullName == null) {
                    fullName = "";
                }
            }
            cache.put(name, fullName);
        }
        return !fullName.isEmpty() ? fullName : null;
    }

    private boolean isSimpleName(String name) {
        return name.indexOf('.') < 0;
    }

    private String findClassBySimpleName(String name) {
        for (Import imp : imports) {
            if (imp.className != null) {
                if (name.equals(imp.className)) {
                    return imp.name;
                }
            } else {
                try {
                    return Class.forName(imp.name + "." + name, false, classLoader).getName();
                } catch (ClassNotFoundException e) {
                    // continue iterating through imports
                }
            }
        }
        return null;
    }

    static class Import {
        String name;
        String className;
    }
}
