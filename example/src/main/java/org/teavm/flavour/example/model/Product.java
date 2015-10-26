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
import javax.persistence.Id;

/**
 *
 * @author Alexey Andreev
 */
@Entity
public class Product {
    @Id
    private Integer id;

    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String name;

    @Column(scale = 2, precision = 10)
    private BigDecimal price;

    public Product(String sku, String name, BigDecimal price) {
        validateSku(sku);
        validateName(name);
        validatePrice(price);
        this.sku = sku;
        this.name = name;
        this.price = price;
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
        } else if (name.length() > 40) {
            throw new IllegalArgumentException("SKU lenght must not exceed 40 character");
        }
    }

    public Product findSkuDuplicate(ProductRepository repository) {
        Product existing = repository.all()
                .where(product -> product.sku.equals(sku))
                .getOnlyValue();
        return existing != null && existing != this ? existing : null;
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
