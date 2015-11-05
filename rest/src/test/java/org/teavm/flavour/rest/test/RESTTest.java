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

import static org.junit.Assert.*;
import org.junit.Test;
import org.teavm.flavour.rest.RESTClient;
import org.teavm.jso.JSBody;

/**
 *
 * @author Alexey Andreev
 */
public class RESTTest {
    private TestService service = RESTClient.factory(TestService.class).createResource(getUrl());

    @Test
    public void passesQueryParams() {
        assertEquals(5, service.sum(2, 3));
    }

    @JSBody(params = {}, script = "return $test_url;")
    private static native String getUrl();
}
