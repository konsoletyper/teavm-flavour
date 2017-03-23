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

import org.teavm.flavour.example.api.OrderItemDTO;

public class OrderItem {
    public OrderItemDTO data;
    private boolean invalidAmountString;

    public OrderItem(OrderItemDTO data) {
        this.data = data;
    }

    public void parseAmount(String amount) {
        try {
            int value = Integer.parseInt(amount);
            if (value < 1) {
                invalidAmountString = true;
                return;
            }
            data.amount = Integer.parseInt(amount);
            invalidAmountString = false;
        } catch (NumberFormatException e) {
            invalidAmountString = true;
        }
    }

    public boolean isInvalidAmountString() {
        return invalidAmountString;
    }

    public void more() {
        ++data.amount;
        invalidAmountString = false;
    }

    public void less() {
        if (data.amount > 1) {
            --data.amount;
            invalidAmountString = false;
        }
    }
}
