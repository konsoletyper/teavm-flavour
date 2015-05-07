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
import java.util.Date;
import java.util.List;
import org.teavm.flavour.templates.Templates;

/**
 *
 * @author Alexey Andreev
 */
public final class Client {
    private Client() {
    }

    public static void main(String[] args) {
        final InMemoryProductDataSet dataSet = new InMemoryProductDataSet(createProducts());
        ProductViewFactory productViewFactory = new ProductViewFactory() {
            @Override public ProductsView create() {
                return new ProductsView(dataSet);
            }
        };

        OrderView order = new OrderView(productViewFactory);
        order.setAddress("Townburgh, Elm street, 123");
        order.setReceiverName("John Doe");
        order.setDate(new Date());

        Templates.bind(order, "application-content");
    }

    private static List<Product> createProducts() {
        List<Product> baseList = Arrays.asList(
            new Product("00001", "Umbrella", new BigDecimal("5.00")),
            new Product("00002", "Cup", new BigDecimal("3.00")),
            new Product("00003", "Brush", new BigDecimal("2.50")),
            new Product("00004", "Sofa", new BigDecimal("400.00")),
            new Product("00005", "Pants", new BigDecimal("50.00")),
            new Product("00006", "Toothpaste", new BigDecimal("4.00")),
            new Product("00007", "Pan", new BigDecimal("100.00")),
            new Product("00008", "Refrigerator", new BigDecimal("10000.00")));
        List<String> adjectives = Arrays.asList("Cheap", "Normal", "Expensive", "Exquisite");
        List<BigDecimal> quotients = Arrays.asList(new BigDecimal("0.5"), new BigDecimal(1), new BigDecimal(2),
                new BigDecimal(5));
        List<String> colors = Arrays.asList("Red", "Blue", "Green", "White", "Yellow", "Black", "Silver", "Gray");
        List<Product> list = new ArrayList<>();
        int index = 0;
        for (Product product : baseList) {
            for (int i = 0; i < adjectives.size(); ++i) {
                String adjective = adjectives.get(i);
                BigDecimal quotient = quotients.get(i);
                for (String color : colors) {
                    list.add(new Product(String.valueOf(index % 7) + String.valueOf(index % 3) + product.getSku(),
                            adjective + " " +  color + " " + product.getName(),
                            product.getUnitPrice().multiply(quotient)));
                }
            }
        }
        Collections.sort(list, new Comparator<Product>() {
            @Override public int compare(Product o1, Product o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return list;
    }
}
