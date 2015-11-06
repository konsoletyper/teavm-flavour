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

import java.util.Date;
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.ValueChangeListener;

/**
 *
 * @author Alexey Andreev
 */
@BindTemplate("templates/flavour/widgets/calendar-drop-down.html")
public class CalendarDropDown {
    private Supplier<Date> value;
    private ValueChangeListener<Date> changeListener;

    public CalendarDropDown(Supplier<Date> value, ValueChangeListener<Date> changeListener) {
        this.value = value;
        this.changeListener = changeListener;
    }

    public Date getValue() {
        return value.get();
    }

    public ValueChangeListener<Date> getChangeListener() {
        return changeListener;
    }
}
