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
package org.teavm.flavour.widgets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.teavm.flavour.templates.Templates;

/**
 *
 * @author Alexey Andreev
 */
public class PagedCursor<T> implements Pageable, DataCursor<T> {
    private DataSource<T> dataSource;
    private List<T> list = new ArrayList<>();
    private List<T> readonlyList = Collections.unmodifiableList(list);
    private int currentPage;
    private int pageCount;
    private int pageSize = 20;
    private boolean loading;

    public PagedCursor(DataSource<T> dataSource) {
        this.dataSource = dataSource;
    }

    public void refresh() {
        loading = true;
        new Thread(() -> {
            try {
                int count = dataSource.count();
                int newPageCount = (count - 1) / pageSize + 1;
                List<T> newList = dataSource.fetch(currentPage * pageSize, pageSize);

                list.clear();
                list.addAll(newList);
                pageCount = newPageCount;
            } finally {
                loading = false;
            }
            Templates.update();
        });
    }

    public void nextPage() {
        currentPage++;
        refresh();
    }

    public void previousPage() {
        currentPage--;
        refresh();
    }

    @Override
    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public void parseCurrentPage(String text) {
        try {
            int page = Integer.parseInt(text) - 1;
            if (page >= 0 && page < pageCount) {
                setCurrentPage(page);
            }
        } catch (NumberFormatException e) {
            // Do nothing
        }
    }

    @Override
    public int getPageCount() {
        return pageCount;
    }

    public int getOffset() {
        return currentPage * pageSize;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public List<T> fetch() {
        return readonlyList;
    }

    @Override
    public boolean isLoading() {
        return loading;
    }
}
