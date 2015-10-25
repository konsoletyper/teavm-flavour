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
package org.teavm.flavour.example;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.teavm.flavour.routing.Conductor;
import org.teavm.flavour.routing.Route;
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

    private Client() {
    }

    public static void main(String[] args) {
        Client client = new Client();
        new Conductor().add(client);
        Route.open(ApplicationRoute.class).orderList();
        Templates.bind(client, "application-content");
    }

    @Override
    public void orderList() {
        content = Templates.create(new OrderListView());
    }

    @Override
    public void order(int id) {
        content = Templates.create(new OrderView(productViewFactory()));
    }

    @Override
    public void newOrder() {
        content = Templates.create(new OrderView(productViewFactory()));
    }

    public Fragment getContent() {
        return content;
    }

    public ProductDataSet dataSet() {
        return new InMemoryProductDataSet(createProducts());
    }

    public ProductViewFactory productViewFactory() {
        return () -> new ProductsView(dataSet());
    }

    private static List<Product> createProducts() {
        List<Product> baseList = Arrays.asList(
            new Product("001", "Umbrella", new BigDecimal("2.50")),
            new Product("002", "Cup", new BigDecimal("1.50")),
            new Product("003", "Brush", new BigDecimal("1.75")),
            new Product("004", "Sofa", new BigDecimal("200.00")),
            new Product("005", "Pants", new BigDecimal("25.00")),
            new Product("006", "Toothpaste", new BigDecimal("2.00")),
            new Product("007", "Pan", new BigDecimal("50.00")),
            new Product("008", "Refrigerator", new BigDecimal("500.00")));
        List<String> adjectives = Arrays.asList("Cheap", "Normal", "Expensive", "Exquisite");
        List<BigDecimal> quotients = Arrays.asList(new BigDecimal("1"), new BigDecimal(2), new BigDecimal(3),
                new BigDecimal(5));
        List<String> colors = Arrays.asList("Red", "Blue", "Green", "White", "Yellow", "Black", "Silver", "Gray");
        List<Product> list = new ArrayList<>();
        int index = 0;
        for (Product product : baseList) {
            for (int i = 0; i < adjectives.size(); ++i) {
                String adjective = adjectives.get(i);
                BigDecimal quotient = quotients.get(i);
                for (String color : colors) {
                    String sku = String.valueOf(index / 100) + String.valueOf((index / 10) % 10)
                            + String.valueOf(index % 10) + product.getSku();
                    ++index;
                    list.add(new Product(sku,
                            adjective + " " + color + " " + product.getName(),
                            product.getUnitPrice().multiply(quotient)));
                }
            }
        }
        Collections.sort(list, Comparator.comparing(Product::getName));
        return list;
    }
}
