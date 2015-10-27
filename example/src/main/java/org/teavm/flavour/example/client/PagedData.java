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
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Alexey Andreev
 */
public class PagedData<T> {
    private DataSet<T> dataSet;
    private List<T> list = new ArrayList<>();
    private List<T> readonlyList = Collections.unmodifiableList(list);
    private int currentPage;
    private int pageCount;
    private int pageSize = 20;

    public PagedData(DataSet<T> dataSet) {
        this.dataSet = dataSet;
    }

    public void refresh() {
        int count = dataSet.count();
        pageCount = (count - 1) / pageSize + 1;
        list.clear();
        list.addAll(dataSet.fetch(currentPage * pageSize, pageSize));
    }

    public void nextPage() {
        currentPage++;
        refresh();
    }

    public void previousPage() {
        currentPage--;
        refresh();
    }

    public int getCurrentPage() {
        return currentPage;
    }

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

    public List<T> getList() {
        return readonlyList;
    }
}
