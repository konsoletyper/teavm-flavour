/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.flavour.validation;

import java.text.DecimalFormat;
import java.text.ParsePosition;

/**
 *
 * @author Alexey Andreev
 */
public interface Converter<T> {
    String makeString(T value);

    T parse(String value) throws ConversionException;

    static Converter<String> stringFormat() {
        return new Converter<String>() {
            @Override
            public String makeString(String value) {
                return value;
            }
            @Override
            public String parse(String value) throws ConversionException {
                return value;
            }
        };
    }

    static Converter<Integer> integerFormat() {
        return new Converter<Integer>() {
            @Override
            public String makeString(Integer value) {
                return value != null ? String.valueOf(value.intValue()) : "";
            }

            @Override
            public Integer parse(String value) throws ConversionException {
                value = value.trim();
                if (value.isEmpty()) {
                    return null;
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ConversionException();
                }
            }
        };
    }

    static Converter<Double> doubleFormat(String formatString) {
        return new Converter<Double>() {
            DecimalFormat format = new DecimalFormat(formatString);

            @Override
            public String makeString(Double value) {
                return format.format(value);
            }

            @Override
            public Double parse(String value) throws ConversionException {
                value = value.trim();
                ParsePosition position = new ParsePosition(0);
                Number result = format.parse(value, position);
                if (result == null || position.getIndex() != value.length()) {
                    throw new ConversionException();
                }
                return result.doubleValue();
            }
        };
    }
}
