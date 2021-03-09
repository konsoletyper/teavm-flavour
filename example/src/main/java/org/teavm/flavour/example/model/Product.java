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
package org.teavm.flavour.example.model;

import java.math.BigDecimal;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import org.teavm.flavour.json.JsonPersistable;

@Entity
@SequenceGenerator(name = "ProductIdGen", sequenceName = "ProductIdGen", allocationSize = 1)
@Table(name = "products")
@JsonPersistable
public class Product {
    @Id
    @Column(nullable = false)
    @GeneratedValue(generator = "ProductIdGen", strategy = GenerationType.SEQUENCE)
    private Integer id;

    @Column(length = 40, nullable = false)
    private String sku;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(scale = 2, precision = 10, nullable = false)
    private BigDecimal price;

    Product() {
    }

    public Product(String sku, String name, BigDecimal price) {
        validateSku(sku);
        validateName(name);
        validatePrice(price);
        this.sku = sku;
        this.name = name;
        this.price = price;
    }

    public Integer getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        validateSku(sku);
        this.sku = sku;
    }

    private void validateSku(String sku) {
        Objects.requireNonNull(sku);
        if (sku.isEmpty()) {
            throw new IllegalArgumentException("SKU must be non-empty");
        } else if (sku.length() > 40) {
            throw new IllegalArgumentException("SKU length must not exceed 40 character");
        }
    }

    public Product findSkuDuplicate(ProductRepository repository) {
        String sku = this.sku;
        Product existing = repository.all()
                .where(product -> product.getSku().equals(sku))
                .findFirst().orElse(null);
        return existing != null && existing != this ? existing : null;
    }

    public void checkSkuDuplicate(ProductRepository repository) {
        if (findSkuDuplicate(repository) != null) {
            throw new IllegalArgumentException("Product with SKU " + sku + " already exists");
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        validateName(name);
        this.name = name;
    }

    private void validateName(String name) {
        Objects.requireNonNull(name);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name must be non-empty");
        } else if (name.length() > 100) {
            throw new IllegalArgumentException("Name lenght must not exceed 100 character");
        }
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        validatePrice(price);
        this.price = price;
    }

    private void validatePrice(BigDecimal price) {
        Objects.requireNonNull(price);
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }
}
