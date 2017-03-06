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
package org.teavm.flavour.example.model;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

@Entity
@SequenceGenerator(name = "OrderIdGen", sequenceName = "OrderIdGen", allocationSize = 1)
@Table(name = "orders")
public class Order {
    @Id
    @Column(nullable = false)
    @GeneratedValue(generator = "OrderIdGen", strategy = GenerationType.SEQUENCE)
    private Integer id;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCreated;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(length = 100, nullable = false)
    private String receiverName;

    @Column(length = 200, nullable = false)
    private String address;

    @Column(nullable = false)
    @Temporal(TemporalType.DATE)
    private Date shippingDate;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    private Set<OrderItem> items;

    @Transient
    private Set<OrderItem> readonlyItems;

    Order() {
    }

    public Order(String receiverName, String address, Date shippingDate) {
        validateReceiverName(receiverName);
        validateAddress(address);
        validateShippingDate(shippingDate);
        status = OrderStatus.PLANNED;
        dateCreated = new Date();
        this.receiverName = receiverName;
        this.address = address;
        this.shippingDate = shippingDate;
        items = new HashSet<>();
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        validateReceiverName(receiverName);
        this.receiverName = receiverName;
    }

    private void validateReceiverName(String receiverName) {
        if (receiverName == null) {
            throw new IllegalArgumentException("receiverName must be non-null");
        }
        if (receiverName.isEmpty()) {
            throw new IllegalArgumentException("receiverName must be non-empty");
        }
        if (receiverName.length() > 100) {
            throw new IllegalArgumentException("receiverName must be at most 100 characters long");
        }
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        validateAddress(address);
        this.address = address;
    }

    private void validateAddress(String address) {
        if (address == null) {
            throw new IllegalArgumentException("address must be non-null");
        }
        if (address.isEmpty()) {
            throw new IllegalArgumentException("address must be non-empty");
        }
        if (address.length() > 200) {
            throw new IllegalArgumentException("adress must be at most 200 characters long");
        }
    }

    public Date getShippingDate() {
        return shippingDate;
    }

    public void setShippingDate(Date shippingDate) {
        validateShippingDate(shippingDate);
        this.shippingDate = shippingDate;
    }

    private void validateShippingDate(Date shippingDate) {
        if (shippingDate == null) {
            throw new IllegalArgumentException("Shipping date must be non-null");
        }
    }

    public Set<OrderItem> getItems() {
        if (readonlyItems == null) {
            readonlyItems = Collections.unmodifiableSet(items);
        }
        return readonlyItems;
    }

    public void addItem(OrderItem item) {
        if (item.getOrder() != null) {
            throw new IllegalStateException("This item is already in some order");
        }
        item.setOrder(this);
        items.add(item);
    }

    void deleteItem(OrderItem item) {
        item.setOrder(null);
        items.remove(item);
    }
}
