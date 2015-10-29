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
import org.teavm.flavour.routing.Conductor;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Templates;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/main.html")
public final class Client implements ApplicationRoute {
    private Fragment content;
    private ProductFacade facade = new RemoteProductFacade();

    private Client() {
    }

    public static void main(String[] args) {
        Client client = new Client();
        new Conductor().add(client);
        //Route.open(ApplicationRoute.class).productList();
        Templates.bind(client, "application-content");
        client.parse();
        Templates.update();
    }

    @Override
    public void orderList() {
        content = Templates.create(new OrderListView());
        Templates.update();
    }

    @Override
    public void order(int id) {
        content = Templates.create(new OrderView());
        Templates.update();
    }

    @Override
    public void newOrder() {
        content = Templates.create(new OrderView());
        Templates.update();
    }

    public Fragment getContent() {
        return content;
    }

    public ProductDataSource productDataSet() {
        return new ProductDataSource(facade);
    }

    @Override
    public void productList() {
        content = Templates.create(new ProductListView(productDataSet()));
        Templates.update();
    }

    @Override
    public void product(int id) {
    }

    @Override
    public void newProduct() {
        content = Templates.create(new ProductEditView(facade));
        Templates.update();
    }
}
