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
package org.teavm.flavour.example;

import java.math.BigDecimal;

/**
 *
 * @author Alexey Andreev
 */
public class OrderItem {
    private Product product;
    private int amount;
    private boolean invalidAmountString;

    public OrderItem(Product product) {
        this.product = product;
        amount = 1;
    }

    public Product getProduct() {
        return product;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        if (this.amount != amount) {
            this.amount = amount;
            invalidAmountString = false;
        }
    }

    public void parseAmount(String amount) {
        try {
            int value = Integer.parseInt(amount);
            if (value < 1) {
                invalidAmountString = true;
                return;
            }
            setAmount(Integer.parseInt(amount));
        } catch (NumberFormatException e) {
            invalidAmountString = true;
        }
    }

    public boolean isInvalidAmountString() {
        return invalidAmountString;
    }

    public BigDecimal getPrice() {
        return product.getUnitPrice().multiply(new BigDecimal(amount));
    }

    public void more() {
        ++amount;
        invalidAmountString = false;
    }

    public void less() {
        if (amount > 1) {
            --amount;
            invalidAmountString = false;
        }
    }
}
