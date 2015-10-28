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
import java.util.List;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Slot;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "paginator")
@BindTemplate("templates/flavour/widgets/paginator.html")
public class Paginator extends AbstractWidget {
    private Computation<Integer> sidePages = () -> 3;
    private Computation<Integer> surroundingPages = () -> 2;
    private Computation<Pageable> data;
    private List<Item> items = new ArrayList<>();
    private Pageable cachedData;
    private int cachedSidePages;
    private int cachedSurroundingPages;
    private int cachedPage;
    private int cachedPageCount;

    public Paginator(Slot slot) {
        super(slot);
    }

    @BindAttribute(name = "data")
    public void setData(Computation<Pageable> data) {
        this.data = data;
    }

    @BindAttribute(name = "side-pages", optional = true)
    public void setSidePages(Computation<Integer> sidePages) {
        this.sidePages = sidePages;
    }

    @BindAttribute(name = "surrounding-pages", optional = true)
    public void setSurroundingPages(Computation<Integer> surroundingPages) {
        this.surroundingPages = surroundingPages;
    }

    @Override
    public void render() {
        Pageable data = this.data.perform();
        int sidePages = this.sidePages.perform();
        int surroundingPages = this.surroundingPages.perform();
        int page = data.getCurrentPage();
        int pageCount = data.getPageCount();
        if (sidePages != cachedSidePages || surroundingPages != cachedSurroundingPages
                || data != cachedData || cachedPage != page || cachedPageCount != pageCount) {
            cachedSidePages = sidePages;
            cachedSurroundingPages = surroundingPages;
            cachedData = data;
            cachedPage = page;
            cachedPageCount = pageCount;
            rebuildItems();
            super.render();
        }
    }

    public int getPageCount() {
        return cachedPageCount;
    }

    public boolean isPreviousAvailable() {
        return cachedPage > 0;
    }

    public boolean isNextAvailable() {
        return cachedPage < cachedPageCount - 1;
    }

    public int getCurrentPage() {
        return cachedPage;
    }

    private void rebuildItems() {
        items.clear();
        items.add(new Item(ItemType.PREVIOUS, cachedPage));

        if (cachedSidePages < cachedPageCount) {
            int left = Math.max(cachedSidePages, cachedPage - cachedSurroundingPages);
            int right = Math.min(cachedPageCount - cachedSidePages, cachedPage + cachedSurroundingPages + 1);

            for (int i = 0; i < cachedSidePages; ++i) {
                items.add(new Item(ItemType.PAGE, i));
            }
            if (left > cachedSidePages) {
                items.add(new Item(ItemType.SKIP, -1));
            }

            for (int i = left; i < right; ++i) {
                items.add(new Item(ItemType.PAGE, i));
            }

            if (right <= cachedPageCount - cachedSidePages) {
                items.add(new Item(ItemType.SKIP, -1));
            }
            for (int i = cachedPageCount - cachedSidePages; i < cachedPageCount; ++i) {
                items.add(new Item(ItemType.PAGE, i));
            }
        } else {
            for (int i = 0; i < cachedPageCount; ++i) {
                items.add(new Item(ItemType.PAGE, i));
            }
        }

        items.add(new Item(ItemType.NEXT, cachedPage));
    }

    public List<Item> getItems() {
        return items;
    }

    public static class Item {
        private ItemType type;
        private int pageNumber;

        public Item(ItemType type, int pageNumber) {
            this.type = type;
            this.pageNumber = pageNumber;
        }

        public ItemType getType() {
            return type;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public boolean isPrevious() {
            return type == ItemType.PREVIOUS;
        }

        public boolean isNext() {
            return type == ItemType.NEXT;
        }

        public boolean isPage() {
            return type == ItemType.PAGE;
        }

        public boolean isSkip() {
            return type == ItemType.SKIP;
        }
    }

    public static enum ItemType {
        PREVIOUS,
        NEXT,
        PAGE,
        SKIP
    }
}
