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
package org.teavm.flavour.example.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.example.model.Product;
import org.teavm.flavour.example.model.ProductRepository;

@Component
public class ProductMapper {
    private ProductRepository repository;

    @Autowired
    public ProductMapper(ProductRepository repository) {
        this.repository = repository;
    }

    public ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.id = repository.getId(product);
        dto.name = product.getName();
        dto.sku = product.getSku();
        dto.unitPrice = product.getPrice().doubleValue();
        return dto;
    }
}
