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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.example.api.OrderDTO;
import org.teavm.flavour.example.api.OrderEditDTO;
import org.teavm.flavour.example.api.OrderEditItemDTO;
import org.teavm.flavour.example.api.OrderFacade;
import org.teavm.flavour.example.api.OrderItemDTO;
import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.example.model.OrderStatus;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.widgets.BackgroundWorker;
import org.teavm.jso.browser.Window;

@BindTemplate("templates/order.html")
public class OrderView {
    private Integer id;
    private OrderDTO order;
    private OrderFacade facade;
    private ProductSelectionViewFactory productSelectionViewFactory;
    private List<OrderItem> items = new ArrayList<>();
    private BackgroundWorker background = new BackgroundWorker();

    public OrderView(OrderFacade facade, ProductSelectionViewFactory productSelectionViewFactory) {
        this.facade = facade;
        this.productSelectionViewFactory = productSelectionViewFactory;
        initOrder();
    }

    OrderView(OrderFacade facade, Integer id) {
        this.facade = facade;
        this.id = id;
        initOrder();
        load();
    }

    private void initOrder() {
        order = new OrderDTO();
        order.address = "";
        order.receiverName = "";
        order.status = OrderStatus.PLANNED;
    }

    public boolean isFresh() {
        return id == null;
    }

    public void load() {
        background.run(() -> {
            order = facade.get(id);
            items.clear();
            for (OrderItemDTO itemData : order.items) {
                items.add(new OrderItem(itemData));
            }
        });
    }

    public OrderDTO getOrder() {
        return order;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void addProduct() {
        ProductDTO product = ProductSelectionViewFactory.chooseProduct(productSelectionViewFactory);
        if (product != null) {
            OrderItemDTO itemData = new OrderItemDTO();
            itemData.product = product;
            itemData.amount = 1;
            OrderItem item = new OrderItem(itemData);
            items.add(item);
        }
    }

    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.data.getPrice());
        }
        return total;
    }

    public boolean isLoading() {
        return background.isBusy();
    }

    public void save() {
        OrderEditDTO saveData = new OrderEditDTO();
        saveData.date = order.date;
        saveData.address = order.address;
        saveData.receiverName = order.receiverName;
        saveData.status = order.status;
        saveData.items.clear();
        for (OrderItem item : items) {
            OrderEditItemDTO saveItemData = new OrderEditItemDTO();
            saveItemData.productId = item.data.product.id;
            saveItemData.amount = item.data.amount;
            saveData.items.add(saveItemData);
        }
        background.run(() -> {
            if (id == null) {
                facade.create(saveData);
            } else {
                facade.update(id, saveData);
            }
            Window.current().getHistory().back();
        });
    }
}
