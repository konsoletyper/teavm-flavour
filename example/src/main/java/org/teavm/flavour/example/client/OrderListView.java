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

import java.text.DateFormat;
import java.util.function.Consumer;
import org.teavm.flavour.example.api.OrderDTO;
import org.teavm.flavour.example.api.ProductFacade;
import org.teavm.flavour.routing.Routing;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.widgets.PagedCursor;
import org.teavm.flavour.widgets.Popup;

@BindTemplate("templates/order-list.html")
public class OrderListView {
    private OrderDataSource dataSource;
    private ProductFacade productFacade;
    private ProductSelectionViewFactory productSelectionViewFactory;
    private PagedCursor<OrderDTO> cursor;

    public OrderListView(OrderDataSource dataSource, ProductFacade productFacade,
            ProductSelectionViewFactory productSelectionViewFactory) {
        this(dataSource, productFacade, productSelectionViewFactory, 0);
    }

    public OrderListView(OrderDataSource dataSource, ProductFacade productFacade,
            ProductSelectionViewFactory productSelectionViewFactory, int pageNumber) {
        this.dataSource = dataSource;
        this.productFacade = productFacade;
        this.productSelectionViewFactory = productSelectionViewFactory;
        cursor = new PagedCursor<>(dataSource);
        cursor.setCurrentPage(pageNumber);
    }

    public OrderDataSource getDataSource() {
        return dataSource;
    }

    public PagedCursor<OrderDTO> getCursor() {
        return cursor;
    }

    public void edit(int id, Consumer<String> consumer) {
        Routing.build(ApplicationRoute.class, consumer).order(id);
    }

    public void add() {
        Routing.open(ApplicationRoute.class).newOrder();
    }

    public void selectPage(int page) {
        cursor.setCurrentPage(page);
    }

    public void pageLink(int page, Consumer<String> consumer) {
        Routing.build(ApplicationRoute.class, consumer).orderPage(page);
    }

    public void setFilter(String filter) {
        dataSource.setSearchString(filter);
        cursor.refresh();
    }

    public DateFormat getDateFormat() {
        return DateFormat.getDateInstance(DateFormat.MEDIUM);
    }

    public boolean isAdvancedFilterApplied() {
        return dataSource.getStartDateFilter() != null || dataSource.getEndDateFilter() != null
                || dataSource.getSearchProductId() != null;
    }

    public void filter() {
        OrderFilterView filterView = new OrderFilterView(dataSource, productFacade, productSelectionViewFactory);
        Popup.showModal(filterView);
        cursor.refresh();
    }
}
