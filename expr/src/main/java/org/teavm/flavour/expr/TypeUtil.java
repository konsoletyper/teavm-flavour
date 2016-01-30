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
package org.teavm.flavour.expr;

import org.teavm.flavour.expr.type.GenericType;
import org.teavm.flavour.expr.type.Primitive;
import org.teavm.flavour.expr.type.TypeInference;
import org.teavm.flavour.expr.type.ValueType;

/**
 *
 * @author Alexey Andreev
 */
public final class TypeUtil {
    private TypeUtil() {
    }

    public static boolean same(ValueType a, ValueType b, TypeInference inference) {
        if (a == null || b == null) {
            return true;
        }
        if (a instanceof GenericType && b instanceof GenericType) {
            return inference.equalConstraint((GenericType) a, (GenericType) b);
        } else {
            return a.equals(b);
        }
    }

    public static boolean subtype(ValueType a, ValueType b, TypeInference inference) {
        if (a == null || b == null) {
            return true;
        }
        if (a.equals(b)) {
            return true;
        }
        if (b instanceof Primitive) {
            if (!(a instanceof Primitive)) {
                ValueType unboxed = CompilerCommons.unbox(a);
                if (unboxed == null) {
                    GenericType boxed = CompilerCommons.box(b);
                    return inference.subtypeConstraint((GenericType) a, boxed);
                } else {
                    a = unboxed;
                }
            }
            if (!CompilerCommons.hasImplicitConversion(((Primitive) a).getKind(),
                    ((Primitive) b).getKind())) {
                return false;
            }
            return CompilerCommons.tryCastPrimitive((Primitive) a, (Primitive) b);
        }
        if (a instanceof Primitive) {
            a = CompilerCommons.box(a);
            if (a == null) {
                return false;
            }
        }

        return inference.subtypeConstraint((GenericType) a, (GenericType) b);
    }
}
