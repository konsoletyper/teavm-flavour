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
package org.teavm.flavour.example.client;

import org.teavm.flavour.routing.Path;
import org.teavm.flavour.routing.PathParameter;
import org.teavm.flavour.routing.PathSet;
import org.teavm.flavour.routing.Route;

@PathSet
public interface ApplicationRoute extends Route {
    @Path("orders")
    void orderList();

    @Path("orders/page-{pageNum}")
    void orderPage(@PathParameter("pageNum") int page);

    @Path("orders/new")
    void newOrder();

    @Path("orders/{id}")
    void order(@PathParameter("id") int id);

    @Path("products")
    void productList();

    @Path("products/page-{pageNum}")
    void productPage(@PathParameter("pageNum") int page);

    @Path("products/{id}")
    void product(@PathParameter("id") int id);

    @Path("products/new")
    void newProduct();
}
