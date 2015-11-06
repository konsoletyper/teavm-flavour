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
import java.util.function.Supplier;
import org.teavm.flavour.templates.BindAttribute;
import org.teavm.flavour.templates.BindDirective;
import org.teavm.flavour.templates.BindTemplate;
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
    private Supplier<String> currentLocale;
    private Supplier<Date> currentDate;
    private ValueChangeListener<Date> changeListener;
    private Date cachedDate;
    private int selectedYear;
    private int selectedMonth;
    private int selectedDay;
    private DateFormatSymbols dateFormatSymbols = new DateFormatSymbols();
    private String cachedLocale;
    private List<Weekday> weekdays = new ArrayList<>();

    public CalendarWidget(Slot slot) {
        super(slot);
    }

    public List<Day> getDays() {
        return days;
    }

    public List<Weekday> getWeekdays() {
        return weekdays;
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

    @BindAttribute(name = "locale", optional = true)
    public void setCurrentLocale(Supplier<String> currentLocale) {
        this.currentLocale = currentLocale;
    }

    @BindAttribute(name = "value")
    public void setCurrentDate(Supplier<Date> currentDate) {
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
        Date newDate = currentDate.get();
        if (!newDate.equals(cachedDate)) {
            cachedDate = newDate;
            rebuildCurrentDate();
        }
        String newLocale = currentLocale != null ? currentLocale.get() : null;
        boolean localeChanged = false;
        if (!Objects.equals(newLocale, cachedLocale)) {
            cachedLocale = newLocale;
            dateFormatSymbols = newLocale != null ? new DateFormatSymbols(new Locale(newLocale))
                    : new DateFormatSymbols();
            localeChanged = true;
        }
        if (localeChanged || weekdays.isEmpty()) {
            rebuildWeekdays();
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
        days.clear();
        int offset = c.get(Calendar.DAY_OF_WEEK) - c.getFirstDayOfWeek();
        if (offset < 0) {
            offset += 7;
        }
        c.add(Calendar.DATE, -offset);
        for (int i = 0; i < 5; ++i) {
            for (int j = 0; j < 7; ++j) {
                days.add(new Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE),
                        i, c.get(Calendar.DAY_OF_WEEK), j));
                c.add(Calendar.DATE, 1);
            }
        }
        int i = 0;
        while (c.get(Calendar.MONTH) == displayMonth) {
            days.set(i, new Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE),
                    0, c.get(Calendar.DAY_OF_WEEK), i));
            c.add(Calendar.DATE, 1);
        }
    }

    private void rebuildWeekdays() {
        weekdays.clear();
        String[] names = dateFormatSymbols.getShortWeekdays();
        Calendar c = Calendar.getInstance();
        int offset = c.getFirstDayOfWeek() - 1;
        for (int i = 0; i < 7; ++i) {
            int weekdayNumber = (offset + i) % 7;
            Weekday weekday = new Weekday(weekdayNumber + 1, i, names[weekdayNumber]);
            weekdays.add(weekday);
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

    public class Weekday {
        public final int weekDay;
        public final int weekDayIndex;
        public final String name;

        public Weekday(int weekDay, int weekDayIndex, String name) {
            this.weekDay = weekDay;
            this.weekDayIndex = weekDayIndex;
            this.name = name;
        }
    }
}
