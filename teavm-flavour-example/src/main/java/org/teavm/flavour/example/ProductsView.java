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
import org.teavm.flavour.templates.BindTemplate;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/products.html")
public class ProductsView implements PopupContent {
    private ProductDataSet dataSet;
    private PopupDelegate popup;
    private List<Product> products = new ArrayList<>();
    private int currentPage;
    private int pageSize = 20;
    private int pageCount;
    private Product chosenProduct;
    private String filter;

    public ProductsView(ProductDataSet dataSet) {
        this.dataSet = dataSet;
        refresh();
    }

    @Override
    public void setDelegate(PopupDelegate popup) {
        this.popup = popup;
    }

    public Product getChosenProduct() {
        return chosenProduct;
    }

    public void refresh() {
        int count = dataSet.countProducts(filter);
        pageCount = (count - 1) / pageSize + 1;
        products.clear();
        products.addAll(dataSet.getProducts(filter, currentPage * pageSize, pageSize));
    }

    public void nextPage() {
        currentPage++;
        refresh();
    }

    public void previousPage() {
        currentPage--;
        refresh();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getOffset() {
        return currentPage * pageSize;
    }

    public void choose(int index) {
        chosenProduct = products.get(index);
        popup.close();
    }

    public List<Product> getProducts() {
        return products;
    }
}
