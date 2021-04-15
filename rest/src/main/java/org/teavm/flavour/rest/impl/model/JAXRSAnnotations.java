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
package org.teavm.flavour.rest.impl.model;

final class JAXRSAnnotations {
    private static final String PREFIX = "javax.ws.rs.";
    public static final String PATH = PREFIX + "Path";
    public static final String GET = PREFIX + "GET";
    public static final String PUT = PREFIX + "PUT";
    public static final String POST = PREFIX + "POST";
    public static final String PATCH = PREFIX + "PATCH";
    public static final String DELETE = PREFIX + "DELETE";
    public static final String HEAD = PREFIX + "HEAD";
    public static final String OPTIONS = PREFIX + "OPTIONS";
    public static final String PATH_PARAM = PREFIX + "PathParam";
    public static final String QUERY_PARAM = PREFIX + "QueryParam";
    public static final String HEADER_PARAM = PREFIX + "HeaderParam";
    public static final String BEAN_PARAM = PREFIX + "BeanParam";
    public static final String CONSUMES = PREFIX + "Consumes";
    public static final String PRODUCES = PREFIX + "Produces";

    private JAXRSAnnotations() {
    }
}
