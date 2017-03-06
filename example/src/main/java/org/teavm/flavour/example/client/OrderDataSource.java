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
package org.teavm.flavour.example.client;

import java.util.Date;
import java.util.List;
import org.teavm.flavour.example.api.OrderDTO;
import org.teavm.flavour.example.api.OrderFacade;
import org.teavm.flavour.example.api.OrderQueryDTO;
import org.teavm.flavour.example.api.QueryPageDTO;
import org.teavm.flavour.widgets.DataSource;

public class OrderDataSource implements DataSource<OrderDTO> {
    private OrderFacade facade;
    private String searchString;
    private Integer searchProductId;
    private Date startDateFilter;
    private Date endDateFilter;

    public OrderDataSource(OrderFacade facade) {
        this.facade = facade;
    }

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public Integer getSearchProductId() {
        return searchProductId;
    }

    public void setSearchProductId(Integer searchProductId) {
        this.searchProductId = searchProductId;
    }

    public Date getStartDateFilter() {
        return startDateFilter;
    }

    public void setStartDateFilter(Date startDateFilter) {
        this.startDateFilter = startDateFilter;
    }

    public Date getEndDateFilter() {
        return endDateFilter;
    }

    public void setEndDateFilter(Date endDateFilter) {
        this.endDateFilter = endDateFilter;
    }

    @Override
    public List<OrderDTO> fetch(int offset, int limit) {
        QueryPageDTO page = new QueryPageDTO();
        page.offset = offset;
        page.limit = limit;
        return facade.list(createQuery(), page);
    }

    @Override
    public int count() {
        return facade.count(createQuery());
    }

    private OrderQueryDTO createQuery() {
        OrderQueryDTO query = new OrderQueryDTO();
        query.text = searchString;
        query.itemId = searchProductId;
        query.startDate = startDateFilter;
        query.endDate = endDateFilter;
        return query;
    }
}
