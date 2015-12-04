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
package org.teavm.flavour.mp.impl;

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Alexey Andreev
 */
public class ProxyClassLoader extends ClassLoader {
    private String className;
    private ProxyMethodInstrumentation instrumentation = new ProxyMethodInstrumentation();

    public ProxyClassLoader(ClassLoader parent, String className) {
        super(parent);
        this.className = className;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith(className)) {
            return super.loadClass(name, resolve);
        } else {
            try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                byte[] array = instrumentation.instrument(IOUtils.toByteArray(input));
                return defineClass(name, array, 0, array.length);
            } catch (IOException e) {
                throw new ClassNotFoundException("Error reading bytecode of class " + name, e);
            }
        }
    }
}
