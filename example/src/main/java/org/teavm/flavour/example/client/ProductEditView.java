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

import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.example.api.ProductFacade;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/product.html")
public class ProductEditView {
    private Integer id;
    private ProductDTO product;
    private boolean loading;
    private ProductFacade facade;
    private boolean priceValid = true;

    public ProductEditView(ProductFacade facade) {
        this.facade = facade;
        product = new ProductDTO();
        product.sku = "";
        product.name = "";
        product.unitPrice = 0;
    }

    public ProductEditView(ProductFacade facade, int id) {
        this.facade = facade;
        this.id = id;
        load();
    }

    private void load() {
        loading = true;
        new Thread(() -> {
            try {
                product = facade.get(id);
            } finally {
                loading = false;
            }
            Templates.update();
        }).start();
    }

    public void save() {
        new Thread(() -> {
            try {
                if (id == null) {
                    facade.create(product);
                } else {
                    facade.update(id, product);
                }
                Window.current().getHistory().back();
            } finally {
                loading = false;
            }
            Templates.update();
        }).start();
    }

    public void setSku(String sku) {
        product.sku = sku;
    }

    public void setName(String name) {
        product.name = name;
    }

    public void setUnitPrice(String price) {
        priceValid = true;
        try {
            product.unitPrice = Double.parseDouble(price.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            priceValid = false;
        }
    }

    public boolean isPriceValid() {
        return priceValid;
    }

    public ProductDTO getProduct() {
        return product;
    }

    public boolean isLoading() {
        return loading;
    }
}
