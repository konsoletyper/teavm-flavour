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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.teavm.flavour.example.api.ProductDTO;
import org.teavm.flavour.example.api.ProductFacade;
import org.teavm.flavour.example.api.ProductQueryDTO;
import org.teavm.flavour.json.JSON;
import org.teavm.flavour.json.tree.ArrayNode;
import org.teavm.flavour.json.tree.Node;
import org.teavm.jso.browser.Window;

/**
 *
 * @author Alexey Andreev
 */
public class RemoteProductFacade implements ProductFacade {
    @Override
    public int create(ProductDTO data) {
        String url = "api/products";
        try {
            String response = Ajax.post(url, JSON.serialize(data).stringify());
            return JSON.deserializeInt(Node.parse(response));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ProductDTO> list(ProductQueryDTO query) {
        StringBuilder sb = new StringBuilder();
        sb.append("api/products?offset" + query.page.offset);
        if (query.namePart != null && !query.namePart.isEmpty()) {
            sb.append("&name=" + Window.encodeURIComponent(query.namePart));
        }
        if (query.page.limit > 0) {
            sb.append("&limit=" + query.page.limit);
        }
        List<ProductDTO> products = new ArrayList<>();
        try {
            ArrayNode jsonArray = (ArrayNode) Node.parse(Ajax.get(sb.toString()));
            for (int i = 0; i < jsonArray.size(); ++i) {
                products.add(JSON.deserialize(jsonArray.get(i), ProductDTO.class));
            }
            return products;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int count(ProductQueryDTO query) {
        StringBuilder sb = new StringBuilder("api/products/count");
        if (query.namePart != null && !query.namePart.isEmpty()) {
            sb.append("&name=" + Window.encodeURIComponent(query.namePart));
        }
        try {
            return JSON.deserializeInt(Node.parse(Ajax.get(sb.toString())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProductDTO get(int id) {
        String url = "api/products/" + id;
        try {
            return JSON.deserialize(Node.parse(Ajax.get(url)), ProductDTO.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update(int id, ProductDTO data) {
        String url = "api/products/" + id;
        try {
            Ajax.put(url, JSON.serialize(data).stringify());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(int id) {
    }
}
