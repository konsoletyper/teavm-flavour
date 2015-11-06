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
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.Templates;
import org.teavm.flavour.templates.ValueChangeListener;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.TextRectangle;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "date")
@BindTemplate("templates/flavour/widgets/date-field.html")
public class DateField extends AbstractWidget {
    private Supplier<Integer> size;
    private Supplier<Date> value;
    private Supplier<String> locale;
    private Supplier<String> format;
    private ValueChangeListener<Date> changeListener;
    private String cachedFormat;
    private String cachedLocale;
    private DateFormat cachedFormatObject;
    private HTMLElement dropDownElement;
    private Component dropDownComponent;
    private HTMLInputElement inputElement;

    public DateField(Slot slot) {
        super(slot);
    }

    public int getSize() {
        Integer result = size != null ? size.get() : null;
        return result != null ? result : 20;
    }

    @BindAttribute(name = "size", optional = true)
    public void setSize(Supplier<Integer> size) {
        this.size = size;
    }

    public Date getValue() {
        return value.get();
    }

    public String getDateString() {
        Date value = getValue();
        return value != null ? cachedFormatObject.format(getValue()) : "";
    }

    @BindAttribute(name = "value")
    public void setValue(Supplier<Date> value) {
        this.value = value;
    }

    @BindAttribute(name = "locale", optional = true)
    public void setLocale(Supplier<String> locale) {
        this.locale = locale;
    }

    @BindAttribute(name = "format", optional = true)
    public void setFormat(Supplier<String> format) {
        this.format = format;
    }

    @BindAttribute(name = "onchange", optional = true)
    public void setChangeListener(ValueChangeListener<Date> changeListener) {
        this.changeListener = changeListener;
    }

    @Override
    public void render() {
        boolean formatChanged = false;
        String newFormat = format != null ? format.get() : null;
        if (!Objects.equals(cachedFormat, newFormat)) {
            cachedFormat = newFormat;
            formatChanged = true;
        }

        boolean localeChanged = false;
        String newLocale = locale != null ? locale.get() : null;
        if (!Objects.equals(cachedLocale, newLocale)) {
            cachedLocale = newLocale;
            localeChanged = true;
        }

        if (formatChanged || localeChanged || cachedFormatObject == null) {
            Locale locale = cachedLocale == null ? Locale.getDefault() : new Locale(cachedLocale);
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

    public void setInputElement(HTMLInputElement inputElement) {
        this.inputElement = inputElement;
    }

    public void dropDown() {
        if (dropDownElement != null) {
            return;
        }
        dropDownElement = HTMLDocument.current().createElement("div");
        dropDownElement.setAttribute("class", "flavour-dropdown flavour-dropdown-calendar");

        TextRectangle windowRect = HTMLDocument.current().getBody().getBoundingClientRect();
        TextRectangle inputRect = inputElement.getBoundingClientRect();
        dropDownElement.getStyle().setProperty("right", windowRect.getWidth() - inputRect.getRight() + "px");
        dropDownElement.getStyle().setProperty("top", inputRect.getBottom() + "px");

        CalendarDropDown dropDown = new CalendarDropDown(value, newValue -> {
            closeDropDown();
            changeListener.changed(newValue);
        });
        dropDownComponent = Templates.bind(dropDown, dropDownElement);

        HTMLDocument.current().getBody().appendChild(dropDownElement);
        Window.setTimeout(() -> HTMLDocument.current().addEventListener("click", bodyListener), 0);
    }

    private void closeDropDown() {
        HTMLDocument.current().getBody().removeChild(dropDownElement);
        HTMLDocument.current().removeEventListener("click", bodyListener);
        dropDownComponent.destroy();
        dropDownElement = null;
        dropDownComponent = null;
    }

    private EventListener<MouseEvent> bodyListener = evt -> {
        HTMLElement clickedElement = (HTMLElement) evt.getTarget();
        while (clickedElement != null) {
            if (clickedElement == dropDownElement) {
                return;
            }
            clickedElement = (HTMLElement) clickedElement.getParentNode();
        }
        closeDropDown();
    };
}
