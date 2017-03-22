/*
 *  Copyright 2017 konsoletyper.
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
import org.teavm.flavour.widgets.PopupContent;
import org.teavm.flavour.widgets.PopupDelegate;

@BindTemplate("templates/order-filter.html")
public class OrderFilterView implements PopupContent {
    private OrderDataSource dataSource;
    private ProductSelectionViewFactory productSelectionViewFactory;
    private ProductDTO product;
    private PopupDelegate delegate;

    public OrderFilterView(OrderDataSource dataSource, ProductFacade productFacade,
            ProductSelectionViewFactory productSelectionViewFactory) {
        this.dataSource = dataSource;
        this.productSelectionViewFactory = productSelectionViewFactory;
        if (dataSource.getSearchProductId() != null) {
            product = productFacade.get(dataSource.getSearchProductId());
        }
    }

    public OrderDataSource getDataSource() {
        return dataSource;
    }

    public ProductDTO getProduct() {
        return product;
    }

    public void chooseProduct() {
        product = ProductSelectionViewFactory.chooseProduct(productSelectionViewFactory);
    }

    public void clearProduct() {
        product = null;
    }

    public void save() {
        dataSource.setSearchProductId(product != null ? product.id : null);
        delegate.close();
    }

    @Override
    public void setDelegate(PopupDelegate delegate) {
        this.delegate = delegate;
    }
}
