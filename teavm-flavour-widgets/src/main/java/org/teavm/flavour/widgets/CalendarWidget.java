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

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindTemplate;
import org.teavm.flavour.templates.Computation;
import org.teavm.flavour.templates.Slot;
import org.teavm.flavour.templates.ValueChangeListener;

/**
 *
 * @author Alexey Andreev
 */
@BindDirective(name = "calendar")
@BindTemplate("templates/flavour/widgets/calendar.html")
public class CalendarWidget extends AbstractWidget {
    private List<Day> days = new ArrayList<>();
    private int displayYear;
    private int displayMonth;
    private int displayFirstWeek;
    private Computation<String> currentLocale;
    private Computation<Date> currentDate;
    private ValueChangeListener<Date> changeListener;
    private Date cachedDate;
    private int selectedYear;
    private int selectedMonth;
    private int selectedDay;
    private DateFormatSymbols dateFormatSymbols = new DateFormatSymbols();
    private String cachedLocale;

    public CalendarWidget(Slot slot) {
        super(slot);
    }

    public List<Day> getDays() {
        return days;
    }

    public int getDisplayYear() {
        return displayYear;
    }

    public int getDisplayMonth() {
        return displayMonth;
    }

    public String getDisplayMonthName() {
        return dateFormatSymbols.getMonths()[displayMonth];
    }

    public int getSelectedYear() {
        return selectedYear;
    }

    public int getSelectedMonth() {
        return selectedMonth;
    }

    public int getSelectedDay() {
        return selectedDay;
    }

    public int getDisplayFirstWeek() {
        return displayFirstWeek;
    }

    @BindAttribute(name = "locale", optional = true)
    public void setCurrentLocale(Computation<String> currentLocale) {
        this.currentLocale = currentLocale;
    }

    @BindAttribute(name = "value")
    public void setCurrentDate(Computation<Date> currentDate) {
        this.currentDate = currentDate;
    }

    @BindAttribute(name = "onchange", optional = true)
    public void setChangeListener(ValueChangeListener<Date> changeListener) {
        this.changeListener = changeListener;
    }

    public void nextMonth() {
        if (++displayMonth == 12) {
            displayMonth = 0;
            ++displayYear;
        }
        rebuildDays();
    }

    public void previousMonth() {
        if (--displayMonth < 0) {
            displayMonth = 11;
            --displayYear;
        }
        rebuildDays();
    }

    @Override
    public void render() {
        Date newDate = currentDate.perform();
        if (!newDate.equals(cachedDate)) {
            cachedDate = newDate;
            rebuildCurrentDate();
        }
        String newLocale = currentLocale != null ? currentLocale.perform() : null;
        if (!Objects.equals(newLocale, cachedLocale)) {
            cachedLocale = newLocale;
            dateFormatSymbols = newLocale != null ? new DateFormatSymbols(new Locale(newLocale)) :
                    new DateFormatSymbols();
        }
        super.render();
    }

    private void rebuildCurrentDate() {
        Calendar c = Calendar.getInstance();
        c.setTime(cachedDate);
        selectedYear = c.get(Calendar.YEAR);
        selectedMonth = c.get(Calendar.MONTH);
        selectedDay = c.get(Calendar.DATE);
        if (selectedYear != displayYear || selectedMonth != displayMonth) {
            displayYear = selectedYear;
            displayMonth = selectedMonth;
            rebuildDays();
        }
    }

    private void rebuildDays() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.DATE, 1);
        c.set(Calendar.MONTH, displayMonth);
        c.set(Calendar.YEAR, displayYear);
        displayFirstWeek = c.get(Calendar.WEEK_OF_YEAR);
        c.add(Calendar.MONTH, 1);
        c.add(Calendar.DATE, -1);
        int currentLastWeek = c.get(Calendar.WEEK_OF_YEAR);

        days.clear();
        c.set(Calendar.YEAR, displayYear);
        c.set(Calendar.MONTH, displayMonth);
        c.set(Calendar.DATE, 1);
        while (c.getFirstDayOfWeek() != c.get(Calendar.DAY_OF_WEEK)) {
            c.add(Calendar.DATE, -1);
        }
        for (int i = displayFirstWeek; i <= currentLastWeek; ++i) {
            for (int j = 0; j < 7; ++j) {
                days.add(new Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE),
                        c.get(Calendar.WEEK_OF_YEAR), c.get(Calendar.DAY_OF_WEEK), j));
                c.add(Calendar.DATE, 1);
            }
        }
    }

    public class Day {
        public final int year;
        public final int month;
        public final int date;
        public final int week;
        public final int weekDay;
        public final int weekDayIndex;

        Day(int year, int month, int date, int week, int weekDay, int weekDayIndex) {
            this.year = year;
            this.month = month;
            this.date = date;
            this.week = week;
            this.weekDay = weekDay;
            this.weekDayIndex = weekDayIndex;
        }

        public boolean isCurrent() {
            return year == selectedYear && month == selectedMonth && date == selectedDay;
        }

        public boolean isAvailable() {
            return month == displayMonth;
        }

        public void select() {
            if (changeListener == null) {
                return;
            }
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DATE, date);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            changeListener.changed(c.getTime());
        }
    }
}
