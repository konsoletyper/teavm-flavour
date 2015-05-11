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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import org.teavm.dom.browser.Window;
import org.teavm.dom.html.HTMLElement;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Slot;
import org.teavm.jso.JS;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "date")
@BindTemplate("templates/flavour/widgets/date-field.html")
public class DateField extends AbstractWidget {
    private static final Window window = (Window)JS.getGlobal();
    private Computation<Integer> size;
    private Computation<Date> value;
    private Computation<String> locale;
    private Computation<String> format;
    private String cachedFormat;
    private String cachedLocale;
    private DateFormat cachedFormatObject;
    private HTMLElement dropDownElement;

    public DateField(Slot slot) {
        super(slot);
    }

    public int getSize() {
        Integer result = size != null ? size.perform() : null;
        return result != null ? result : 20;
    }

    @BindAttribute(name = "size", optional = true)
    public void setSize(Computation<Integer> size) {
        this.size = size;
    }

    public Date getValue() {
        return value.perform();
    }

    public String getDateString() {
        Date value = getValue();
        return value != null ? cachedFormatObject.format(getValue()) : "";
    }

    @BindAttribute(name = "value")
    public void setValue(Computation<Date> value) {
        this.value = value;
    }

    @BindAttribute(name = "locale", optional = true)
    public void setLocale(Computation<String> locale) {
        this.locale = locale;
    }

    @BindAttribute(name = "format", optional = true)
    public void setFormat(Computation<String> format) {
        this.format = format;
    }

    @Override
    public void render() {
        boolean formatChanged = false;
        String newFormat = format != null ? format.perform() : null;
        if (!Objects.equals(cachedFormat, newFormat)) {
            cachedFormat = newFormat;
            formatChanged = true;
        }

        boolean localeChanged = false;
        String newLocale = locale != null ? locale.perform() : null;
        if (!Objects.equals(cachedLocale, newLocale)) {
            cachedLocale = newLocale;
            localeChanged = true;
        }

        if (formatChanged || localeChanged || cachedFormatObject == null) {
            Locale locale = cachedLocale != null ? Locale.getDefault() : new Locale(newLocale);
            if (cachedFormat == null || cachedFormat.equals("medium")) {
                cachedFormatObject = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
            } else if (cachedFormat.equals("short")) {
                cachedFormatObject = DateFormat.getDateInstance(DateFormat.SHORT, locale);
            } else if (cachedFormat.equals("long")) {
                cachedFormatObject = DateFormat.getDateInstance(DateFormat.LONG, locale);
            } else if (cachedFormat.equals("full")) {
                cachedFormatObject = DateFormat.getDateInstance(DateFormat.FULL, locale);
            } else {
                cachedFormatObject = new SimpleDateFormat(cachedFormat, locale);
            }
        }
        super.render();
    }

    public void dropDown() {
        if (dropDownElement != null) {
            return;
        }
        dropDownElement = window.getDocument().createElement("div");
        dropDownElement.setAttribute("class", "flavour-dropdown");
        dropDownElement.getStyle().setProperty("left", "");
    }
}
