/*
 *  Copyright 2017 Alexey Andreev.
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

import java.util.function.Consumer;
import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.routing.Routing;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.widgets.PagedCursor;
import org.teavm.flavour.widgets.PopupContent;
import org.teavm.flavour.widgets.PopupDelegate;

@BindTemplate("templates/product-selection.html")
public class ProductSelectionView implements PopupContent {
    private ProductDataSource dataSource;
    private PagedCursor<ProductDTO> cursor;
    private PopupDelegate delegate;
    private ProductDTO chosenProduct;

    public ProductSelectionView(ProductDataSource dataSource) {
        this.dataSource = dataSource;
        cursor = new PagedCursor<>(dataSource);
        cursor.setCurrentPage(0);
    }

    public ProductDataSource getDataSource() {
        return dataSource;
    }

    public PagedCursor<ProductDTO> getCursor() {
        return cursor;
    }

    public void pageLink(int page, Consumer<String> consumer) {
        Routing.build(ApplicationRoute.class, consumer).productPage(page);
    }

    public void setFilter(String filter) {
        dataSource.setNamePart(filter);
        cursor.refresh();
    }

    public void choose(ProductDTO product) {
        chosenProduct = product;
        delegate.close();
    }

    public ProductDTO getChosenProduct() {
        return chosenProduct;
    }

    @Override
    public void setDelegate(PopupDelegate delegate) {
        this.delegate = delegate;
    }
}
