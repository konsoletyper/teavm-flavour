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
package org.teavm.flavour.expr.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.teavm.flavour.expr.ClassPathClassResolver;
import org.teavm.flavour.expr.ClassResolver;
import org.teavm.flavour.expr.Compiler;
import org.teavm.flavour.expr.ImportingClassResolver;
import org.teavm.flavour.expr.Parser;
import org.teavm.flavour.expr.Scope;
import org.teavm.flavour.expr.TypedPlan;
import org.teavm.flavour.expr.ast.Expr;
import org.teavm.flavour.expr.type.GenericClass;
import org.teavm.flavour.expr.type.ValueType;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;

/**
 *
 * @author Alexey Andreev
 */
public class TypeInferenceTest {
    private ClassResolver classResolver = new ClassPathClassResolver();

    @Test
    public void infersCommonSupertype() {
        Map<String, ValueType> vars = new HashMap<>();
        vars.put("a", new GenericClass("java.lang.Class", new GenericClass("java.lang.Integer")));
        vars.put("b", new GenericClass("java.lang.Class", new GenericClass("java.lang.Long")));
        ValueType type = inferType("TypeInferenceTest.pair(a, b)", vars);
        assertEquals("java.lang.Class<java.lang.Number>", type.toString());
    }

    @Test
    public void infersExaсtVariable() {
        Map<String, ValueType> vars = new HashMap<>();
        vars.put("a", new GenericClass("java.util.Map",
                new GenericClass("java.lang.Number"),
                new GenericClass("java.lang.String")));
        ValueType type = inferType("TypeInferenceTest.extract(a, 23)", vars);
        assertEquals("java.lang.String", type.toString());
    }

    @Test
    public void exactVariableInferenceFailsOnContradiction() {
        Map<String, ValueType> vars = new HashMap<>();
        vars.put("a", new GenericClass("java.util.HashMap",
                new GenericClass("java.lang.Long"),
                new GenericClass("java.lang.String")));
        assertNull(inferType("TypeInferenceTest.extract(a, Integer.valueOf(23))", vars));
    }

    public static native <T> T pair(T a, T b);

    public static native <K, V> V extract(Map<K, V> map, K key);

    private ValueType inferType(String exprString, Map<String, ValueType> vars) {
        ImportingClassResolver imports = new ImportingClassResolver(classResolver);
        imports.importClass(TypeInferenceTest.class.getName());
        imports.importPackage("java.lang");
        imports.importPackage("java.util");

        Parser parser = new Parser(imports);
        Expr<Void> expr = parser.parse(exprString);
        if (!parser.getDiagnostics().isEmpty()) {
            throw new RuntimeException();
        }

        ClassPathClassDescriberRepository classes = new ClassPathClassDescriberRepository();
        Compiler compiler = new Compiler(classes, imports, new MapScope(vars));
        TypedPlan plan = compiler.compile(expr);

        if (!compiler.getDiagnostics().isEmpty()) {
            return null;
        }

        return plan.getType();
    }

    static class MapScope implements Scope {
        private Map<String, ValueType> map;

        public MapScope(Map<String, ValueType> map) {
            this.map = map;
        }

        @Override
        public ValueType variableType(String variableName) {
            return map.get(variableName);
        }
    }
}
