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

import org.teavm.flavour.example.api.ProductFacade;
import org.teavm.flavour.rest.RESTClient;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.widgets.ApplicationTemplate;
import org.teavm.flavour.widgets.RouteBinder;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/main.html")
public final class Client extends ApplicationTemplate implements ApplicationRoute {
    private ProductFacade facade = RESTClient.factory(ProductFacade.class).createResource("api");

    private Client() {
    }

    public static void main(String[] args) {
        Client client = new Client();
        new RouteBinder()
                .add(client)
                .withDefault(ApplicationRoute.class, route -> route.productList())
                .update();
        client.bind("application-content");
    }

    @Override
    public void orderList() {
        setView(new OrderListView());
    }

    @Override
    public void order(int id) {
        setView(new OrderView());
    }

    @Override
    public void newOrder() {
        setView(new OrderView());
    }

    public ProductDataSource productDataSet() {
        return new ProductDataSource(facade);
    }

    @Override
    public void productList() {
        setView(new ProductListView(productDataSet()));
    }

    @Override
    public void productPage(int page) {
        if (getCurrentView() instanceof ProductListView) {
            ((ProductListView) getCurrentView()).selectPage(page);
        } else {
            setView(new ProductListView(productDataSet(), page));
        }
    }

    @Override
    public void product(int id) {
        setView(new ProductEditView(facade, id));
    }

    @Override
    public void newProduct() {
        setView(new ProductEditView(facade));
    }
}
