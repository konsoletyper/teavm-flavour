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

import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.example.api.OrderDTO;
import org.teavm.flavour.example.api.OrderFacade;
import org.teavm.flavour.example.api.OrderItemDTO;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.widgets.BackgroundWorker;

@BindTemplate("templates/order.html")
public class OrderView {
    private Integer id;
    private OrderDTO order;
    private OrderFacade facade;
    private List<OrderItem> items = new ArrayList<>();
    private BackgroundWorker background = new BackgroundWorker();

    public OrderView(OrderFacade facade) {
        this.facade = facade;
        order = new OrderDTO();
        order.address = "";
        order.receiverName = "";
    }

    OrderView(OrderFacade facade, Integer id) {
        this.facade = facade;
        this.id = id;
        load();
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

    public boolean isLoading() {
        return background.isBusy();
    }

    public void save() {

    }
}
