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

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.BeanParam;
import org.jinq.jpa.JPQL;
import org.jinq.orm.stream.JinqStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.teavm.flavour.example.api.OrderDTO;
import org.teavm.flavour.example.api.OrderEditDTO;
import org.teavm.flavour.example.api.OrderEditItemDTO;
import org.teavm.flavour.example.api.OrderFacade;
import org.teavm.flavour.example.api.OrderItemDTO;
import org.teavm.flavour.example.api.OrderQueryDTO;
import org.teavm.flavour.example.api.QueryPageDTO;
import org.teavm.flavour.example.model.Order;
import org.teavm.flavour.example.model.OrderItem;
import org.teavm.flavour.example.model.OrderRepository;
import org.teavm.flavour.example.model.Product;
import org.teavm.flavour.example.model.ProductRepository;

@Component
@Transactional
public class ServerSideOrderFacade implements OrderFacade {
    private OrderRepository orderRepository;
    private ProductRepository productRepository;

    @Autowired
    public ServerSideOrderFacade(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Override
    public int create(OrderEditDTO data) {
        Order order = fromDTO(data);
        orderRepository.add(order);
        return orderRepository.getId(order);
    }

    @Override
    public List<OrderDTO> list(@BeanParam OrderQueryDTO query, @BeanParam QueryPageDTO page) {
        JinqStream<Order> all = filtered(query)
                .sortedBy(product -> product.getDateCreated())
                .skip(page.offset != null ? page.offset : 0);
        if (page.limit != null) {
            all = all.limit(page.limit);
        }
        return all
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public int count(@BeanParam OrderQueryDTO query) {
        return (int) filtered(query).count();
    }

    @Override
    public OrderDTO get(int id) {
        Order order = orderRepository.findById(id);
        return order != null ? toDto(order) : null;
    }

    @Override
    public void update(int id, OrderEditDTO data) {

    }

    private Order fromDTO(OrderEditDTO dto) {
        Order order = new Order(dto.receiverName, dto.address, dto.date);
        for (OrderEditItemDTO itemDto : dto.items) {
            Product product = productRepository.findById(itemDto.productId);
            OrderItem item = new OrderItem(product, itemDto.amount);
            order.addItem(item);
        }
        return order;
    }

    private OrderDTO toDto(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.id = orderRepository.getId(order);
        dto.status = order.getStatus();
        dto.dateCreated = order.getDateCreated();
        dto.receiverName = order.getReceiverName();
        dto.address = order.getAddress();
        dto.date = order.getShippingDate();
        List<OrderItem> items = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getName()))
                .collect(Collectors.toList());
        for (OrderItem item : items) {
            OrderItemDTO itemDto = new OrderItemDTO();
            itemDto.amount = item.getAmount();
            dto.items.add(itemDto);
        }
        return dto;
    }

    private JinqStream<Order> filtered(OrderQueryDTO query) {
        JinqStream<Order> all = orderRepository.all();
        if (query.text != null && !query.text.trim().isEmpty()) {
            String text = "%" + query.text.trim().toLowerCase() + "%";
            all = all.where(order -> JPQL.like(order.getAddress().toLowerCase(), text)
                    || JPQL.like(order.getReceiverName().toLowerCase(), text)
                    || JinqStream.from(order.getItems())
                            .anyMatch(item -> JPQL.like(item.getProduct().getName().toLowerCase(), text)));
        }
        if (query.startDate != null) {
            Date startDate = query.startDate;
            all = all.where(order -> !order.getShippingDate().before(startDate));
        }
        if (query.endDate != null) {
            Date endDate = query.endDate;
            all = all.where(order -> order.getShippingDate().before(endDate));
        }
        if (query.itemId != null) {
            Product product = productRepository.findById(query.itemId);
            if (product != null) {
                all = all.where(order -> JinqStream.from(order.getItems())
                        .anyMatch(item -> item.getProduct() == product));
            }
        }
        return all;
    }
}
