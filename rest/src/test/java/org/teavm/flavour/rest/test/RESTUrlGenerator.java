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
package org.teavm.flavour.rest.test;

import java.io.IOException;
import org.teavm.backend.javascript.TeaVMJavaScriptHost;
import org.teavm.backend.javascript.codegen.SourceWriter;
import org.teavm.backend.javascript.rendering.RenderingManager;
import org.teavm.backend.javascript.rendering.RenderingUtil;
import org.teavm.vm.BuildTarget;
import org.teavm.vm.spi.AbstractRendererListener;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

public class RESTUrlGenerator extends AbstractRendererListener implements TeaVMPlugin {
    @Override
    public void begin(RenderingManager context, BuildTarget buildTarget) throws IOException {
        SourceWriter writer = context.getWriter();
        String url = System.getProperty("rest.service.url");
        writer.append("var $test_url = \"")
                .append(RenderingUtil.escapeString(url != null ? url : ""))
                .append("\";")
                .newLine();
    }

    @Override
    public void install(TeaVMHost host) {
        TeaVMJavaScriptHost jsHost = host.getExtension(TeaVMJavaScriptHost.class);
        jsHost.add(this);
    }
}
