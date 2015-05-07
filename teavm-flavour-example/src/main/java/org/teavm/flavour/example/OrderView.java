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
package org.teavm.flavour.example;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.teavm.flavour.templates.BindTemplate;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/order.html")
public class OrderView {
    private ProductViewFactory productViewFactory;
    private String receiverName = "";
    private String address = "";
    private Date date;
    private List<OrderItem> items = new ArrayList<>();

    public OrderView(ProductViewFactory productViewFactory) {
        this.productViewFactory = productViewFactory;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotalPrice() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getPrice());
        }
        return total;
    }

    public void addProduct() {
        ProductsView products = productViewFactory.create();
        Popup.showModal(products);
        if (products.getChosenProduct() != null) {
            OrderItem item = new OrderItem(products.getChosenProduct());
            items.add(item);
        }
    }
}
