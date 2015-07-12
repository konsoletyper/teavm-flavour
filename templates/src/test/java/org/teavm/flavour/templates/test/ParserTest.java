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
package org.teavm.flavour.templates.test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.junit.Test;
import org.teavm.flavour.expr.ClassPathClassResolver;
import org.teavm.flavour.expr.type.meta.ClassPathClassDescriberRepository;
import org.teavm.flavour.templates.parsing.ClassPathResourceProvider;
import org.teavm.flavour.templates.parsing.Parser;
import org.teavm.flavour.templates.tree.TemplateNode;

/**
 *
 * @author Alexey Andreev
 */
public class ParserTest {
    @Test
    public void parses() throws IOException {
        ClassPathClassDescriberRepository classRepository = new ClassPathClassDescriberRepository();
        ClassPathClassResolver classResolver = new ClassPathClassResolver();
        ClassPathResourceProvider resourceProvider = new ClassPathResourceProvider();
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        Parser parser = new Parser(classRepository, classResolver, resourceProvider);
        List<TemplateNode> template;
        try (Reader reader = new InputStreamReader(classLoader.getResourceAsStream(
                "META-INF/templates/test-template.html"), "UTF-8")) {
            template = parser.parse(reader, ModelPojo.class.getName());
        }
        System.out.println(template);
    }
}
