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
import org.teavm.flavour.widgets.PopupContent;
import org.teavm.flavour.widgets.PopupDelegate;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/product-selection.html")
public class ProductSelectionView implements PopupContent {
    private ProductDataSet dataSet;
    private PopupDelegate popup;
    private ProductDTO chosenProduct;
    private String filter;
    private PagedData<ProductDTO> data;

    public ProductSelectionView(ProductDataSet dataSet) {
        this.dataSet = dataSet;
        data = new PagedData<>(new DataSet<ProductDTO>() {
            @Override public List<ProductDTO> fetch(int offset, int limit) {
                return dataSet.getProducts(filter, offset, limit);
            }
            @Override public int count() {
                return dataSet.countProducts(filter);
            }
        });
        data.refresh();
    }

    @Override
    public void setDelegate(PopupDelegate popup) {
        this.popup = popup;
    }

    public ProductDTO getChosenProduct() {
        return chosenProduct;
    }

    public void choose(int index) {
        chosenProduct = data.getList().get(index);
        popup.close();
    }

}
