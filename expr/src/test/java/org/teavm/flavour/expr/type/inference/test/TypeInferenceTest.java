/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.flavour.expr.type.inference.test;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.teavm.flavour.expr.type.GenericTypeNavigator;
import org.teavm.flavour.expr.type.inference.ClassType;
import org.teavm.flavour.expr.type.inference.TypeConstraints;
import org.teavm.flavour.expr.type.inference.Variable;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeInferenceTest {
    @Test
    public void infersListOfClasses() {
        boolean ok = true;
        TypeConstraints g = newConstraints();
        Variable.Reference t = g.createVariable().createReference();
        ok &= g.assertSubtype(new ClassType("java.lang.Class", new ClassType("java.lang.Integer")), t);
        ok &= g.assertSubtype(new ClassType("java.lang.Class", new ClassType("java.lang.Long")), t);
        ok &= g.assertSubtype(new ClassType("java.lang.Class", new ClassType("java.lang.Float")), t);

        Variable.Reference s = g.createVariable().createReference();
        ClassType lhsType = new ClassType("java.util.List", new ClassType("java.lang.Class", s));
        ok &= g.assertSubtype(s, new ClassType("java.lang.Number"));
        ok &= g.assertSubtype(new ClassType("java.util.List", t), lhsType);

        assertTrue(ok);
    }

    private TypeConstraints newConstraints() {
        return new TypeConstraints(new GenericTypeNavigator(new ClassPathClassDescriberRepository()));
    }
}
