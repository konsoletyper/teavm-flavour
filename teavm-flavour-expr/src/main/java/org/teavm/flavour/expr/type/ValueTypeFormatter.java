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
package org.teavm.flavour.expr.type;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alexey Andreev
 */
public class ValueTypeFormatter {
    private Map<TypeVar, String> typeVarNames = new HashMap<>();

    public String format(ValueType type) {
        StringBuilder sb = new StringBuilder();
        format(type, sb);
        return sb.toString();
    }

    public void format(ValueType type, StringBuilder sb) {
        if (type instanceof Primitive) {
            switch (((Primitive)type).getKind()) {
                case BOOLEAN:
                    sb.append("boolean");
                    break;
                case CHAR:
                    sb.append("char");
                    break;
                case BYTE:
                    sb.append("byte");
                    break;
                case SHORT:
                    sb.append("short");
                    break;
                case INT:
                    sb.append("int");
                    break;
                case LONG:
                    sb.append("long");
                    break;
                case FLOAT:
                    sb.append("float");
                    break;
                case DOUBLE:
                    sb.append("double");
                    break;
                default:
                    throw new AssertionError("Unexpected primitive type");
            }
        } else if (type instanceof GenericClass) {
            GenericClass cls = (GenericClass)type;
            sb.append(cls.getName());
            if (!cls.getArguments().isEmpty()) {
                sb.append('<');
                for (int i = 0; i < cls.getArguments().size(); ++i) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    format(cls.getArguments().get(i), sb);
                }
                sb.append('>');
            }
        } else if (type instanceof GenericArray) {
            GenericArray array = (GenericArray)type;
            format(array.getElementType());
            sb.append("[]");
        } else if (type instanceof GenericReference) {
            GenericReference ref = (GenericReference)type;
            sb.append(getNameOfTypeVar(ref.getVar()));
        } else {
            throw new AssertionError("Unexpected type: " + type.getClass().getName());
        }
    }

    private String getNameOfTypeVar(TypeVar var) {
        String name = typeVarNames.get(var);
        if (name == null) {
            name = generateName(typeVarNames.size());
            typeVarNames.put(var, name);
        }
        return name;
    }

    private String generateName(int index) {
        int letterIndex = index % 26;
        int suffix = index / 26;
        StringBuilder sb = new StringBuilder();
        sb.append((char)('A' + letterIndex));
        if (suffix > 0) {
            sb.append(suffix);
        }
        return sb.toString();
    }
}
