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
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttributeDirective;
import org.teavm.flavour.templates.BindContent;
import org.teavm.flavour.templates.Component;
import org.teavm.flavour.templates.Renderable;
import org.teavm.flavour.templates.Templates;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.TextRectangle;

@BindAttributeDirective(name = "date", elements = "input")
public class DateField implements Renderable {
    private HTMLInputElement element;
    private Supplier<DateFieldSettings> settings;
    private String cachedFormat;
    private String cachedLocale;
    private DateFormat cachedFormatObject;
    private HTMLElement dropDownElement;
    private Component dropDownComponent;

    public DateField(HTMLElement element) {
        this.element = (HTMLInputElement) element;
        element.addEventListener("click", evt -> dropDown());
    }

    @BindContent
    public void setSettings(Supplier<DateFieldSettings> settings) {
        this.settings = settings;
    }

    @Override
    public void render() {
        boolean formatChanged = false;
        DateFieldSettings settings = this.settings.get();
        String newFormat = settings.getFormat();
        if (!Objects.equals(cachedFormat, newFormat)) {
            cachedFormat = newFormat;
            formatChanged = true;
        }

        boolean localeChanged = false;
        String newLocale = settings.getLocale();
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
    }

    @Override
    public void destroy() {
        if (dropDownElement != null) {
            closeDropDown();
        }
    }

    public void dropDown() {
        if (dropDownElement != null) {
            return;
        }
        dropDownElement = HTMLDocument.current().createElement("div");
        dropDownElement.setAttribute("class", "flavour-dropdown flavour-dropdown-calendar");

        TextRectangle windowRect = HTMLDocument.current().getBody().getBoundingClientRect();
        TextRectangle inputRect = element.getBoundingClientRect();
        dropDownElement.getStyle().setProperty("right", windowRect.getWidth() - inputRect.getRight() + "px");
        dropDownElement.getStyle().setProperty("top", inputRect.getBottom() + "px");

        CalendarDropDown dropDown = new CalendarDropDown(this::parseValue, newValue -> {
            closeDropDown();
            element.setValue(cachedFormatObject.format(newValue));
        });
        dropDownComponent = Templates.bind(dropDown, dropDownElement);

        HTMLDocument.current().getBody().appendChild(dropDownElement);
        Window.setTimeout(() -> HTMLDocument.current().addEventListener("click", bodyListener), 0);
    }

    private Date parseValue() {
        ParsePosition position = new ParsePosition(0);
        Date result = cachedFormatObject.parse(element.getValue(), position);
        if (result == null || position.getIndex() < element.getValue().length()) {
            return null;
        }
        return result;
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
