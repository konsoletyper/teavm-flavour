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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class InMemoryProductDataSet implements ProductDataSet {
    private List<Product> products = new ArrayList<>();

    public InMemoryProductDataSet(List<Product> products) {
        this.products.addAll(products);
    }

    @Override
    public List<Product> getProducts(String searchString, int offset, int limit) {
        if (searchString == null) {
            searchString = "";
        }
        searchString = searchString.trim().toLowerCase();
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            if (product.getName().contains(searchString)) {
                if (offset > 0) {
                    --offset;
                } else if (limit > 0) {
                    result.add(product);
                    --limit;
                } else {
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public int countProducts(String searchString) {
        if (searchString == null || searchString.trim().isEmpty()) {
            return products.size();
        }
        searchString = searchString.trim().toLowerCase();
        int count = 0;
        for (Product product : products) {
            if (product.getName().toLowerCase().contains(searchString)) {
                count++;
            }
        }
        return count;
    }
}
