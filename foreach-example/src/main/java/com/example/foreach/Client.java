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
package com.example.foreach;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Templates;
import org.teavm.flavour.widgets.ApplicationTemplate;
import org.teavm.jso.browser.Window;

@BindTemplate("templates/foreach.html")
public final class Client extends ApplicationTemplate {
    static Random random = new Random();

    private List<Integer> listItems = new ArrayList<>();

    private Client() {
      listItems.add(1);
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.bind("application-content");
        Window.current().setInterval(() -> {
          client.addItem();
          Templates.update();
        }, 1000);
    }

    public List<Integer> getItems() {
        return listItems;
    }

    private void addItem() {
        listItems.add(random.nextInt(listItems.size()), random.nextInt(100));
    }
}
