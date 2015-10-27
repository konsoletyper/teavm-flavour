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

import java.util.List;
import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.templates.BindTemplate;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/product-list.html")
public class ProductListView {
    private List<ProductDTO> products;
    private ProductDataSet dataSet;

    public ProductListView(ProductDataSet dataSet) {
        this.dataSet = dataSet;
    }

    public List<ProductDTO> getProducts() {
        return products;
    }
}
