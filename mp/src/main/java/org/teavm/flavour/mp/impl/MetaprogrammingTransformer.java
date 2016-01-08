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

import java.util.Collections;
import java.util.List;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.flavour.mp.impl.meta.ProxyDescriber;
import org.teavm.flavour.mp.impl.meta.ProxyUsageFinder;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassReaderSource;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;

/**
 *
 * @author Alexey Andreev
 */
public class MetaprogrammingTransformer implements ClassHolderTransformer {
    private ProxyDescriber describer;
    private ProxyUsageFinder usageFinder;

    @Override
    public void transformClass(ClassHolder cls, ClassReaderSource innerSource, Diagnostics diagnostics) {
        if (describer == null) {
            describer = new ProxyDescriber(diagnostics, innerSource);
            usageFinder = new ProxyUsageFinder(describer, diagnostics);
        }
        for (MethodHolder method : cls.getMethods().toArray(new MethodHolder[0])) {
            usageFinder.findUsages(cls, method.getReference(), method.getProgram());
        }
    }

    public List<Runnable> getDeferredErrors(MethodReference method) {
        return usageFinder != null ? usageFinder.getDeferredErrors(method) : Collections.emptyList();
    }
}
