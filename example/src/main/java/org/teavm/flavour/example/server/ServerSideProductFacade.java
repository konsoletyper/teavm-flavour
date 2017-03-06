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
package org.teavm.flavour.example.server;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.jinq.jpa.JPQL;
import org.jinq.orm.stream.JinqStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.example.api.ProductFacade;
import org.teavm.flavour.example.api.ProductQueryDTO;
import org.teavm.flavour.example.api.QueryPageDTO;
import org.teavm.flavour.example.model.Product;
import org.teavm.flavour.example.model.ProductRepository;

@Component
@Transactional
public class ServerSideProductFacade implements ProductFacade {
    private ProductRepository repository;
    private ProductMapper mapper;

    @Autowired
    public ServerSideProductFacade(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public int create(ProductDTO data) {
        Product product = fromDTO(data);
        product.checkSkuDuplicate(repository);
        repository.add(product);
        return repository.getId(product);
    }

    @Override
    public List<ProductDTO> list(ProductQueryDTO query, QueryPageDTO page) {
        JinqStream<Product> all = filtered(query)
                .sortedBy(Product::getName)
                .skip(page.offset != null ? page.offset : 0);
        if (page.limit != null) {
            all = all.limit(page.limit);
        }
        return all
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public int count(ProductQueryDTO query) {
        return (int) filtered(query).count();
    }

    private JinqStream<Product> filtered(ProductQueryDTO query) {
        JinqStream<Product> all = repository.all();
        if (query.namePart != null && !query.namePart.trim().isEmpty()) {
            String namePart = "%" + query.namePart.trim().toLowerCase() + "%";
            all = all.where(product -> JPQL.like(product.getName().toLowerCase(), namePart));
        }
        return all;
    }

    @Override
    public ProductDTO get(int id) {
        return mapper.toDTO(requireProduct(id));
    }

    @Override
    public void update(int id, ProductDTO data) {
        Product product = requireProduct(id);
        fromDTO(data, product);
        product.checkSkuDuplicate(repository);
    }

    @Override
    public void delete(int id) {
        repository.remove(requireProduct(id));
    }

    private Product requireProduct(int id) {
        Product product = repository.findById(id);
        if (product == null) {
            throw new IllegalArgumentException("Product with id " + id + " was not found");
        }
        return product;
    }

    private Product fromDTO(ProductDTO data) {
        return new Product(data.sku, data.name, new BigDecimal(data.unitPrice));
    }

    private void fromDTO(ProductDTO data, Product product) {
        product.setSku(data.sku);
        product.setName(data.name);
        product.setPrice(new BigDecimal(data.unitPrice));
    }
}
