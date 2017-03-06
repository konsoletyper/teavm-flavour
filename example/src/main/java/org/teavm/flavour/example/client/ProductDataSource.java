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
import org.teavm.flavour.example.api.ProductFacade;
import org.teavm.flavour.example.api.ProductQueryDTO;
import org.teavm.flavour.example.api.QueryPageDTO;
import org.teavm.flavour.widgets.DataSource;

public class ProductDataSource implements DataSource<ProductDTO> {
    private ProductFacade facade;
    private String namePart;

    public ProductDataSource(ProductFacade facade) {
        this.facade = facade;
    }

    public String getNamePart() {
        return namePart;
    }

    public void setNamePart(String namePart) {
        this.namePart = namePart;
    }

    @Override
    public List<ProductDTO> fetch(int offset, int limit) {
        ProductQueryDTO query = new ProductQueryDTO();
        QueryPageDTO page = new QueryPageDTO();
        query.namePart = namePart;
        page.offset = offset;
        page.limit = limit;
        return facade.list(query, page);
    }

    @Override
    public int count() {
        ProductQueryDTO query = new ProductQueryDTO();
        query.namePart = namePart;
        return facade.count(query);
    }
}
