package com.azza.core.ui

import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/** apps/api numbers days 0 = Sunday … 6 = Saturday; shown from the locale's first day (Saturday in Persian). */
fun weekOrder(locale: Locale = Locale.getDefault()): List<Int> {
    val first = Calendar.getInstance(locale).firstDayOfWeek - 1
    return (0..6).map { (first + it) % 7 }
}

fun dayName(day: Int, locale: Locale = Locale.getDefault()): String = DateFormatSymbols.getInstance(locale).weekdays[day + 1]

/** 540 → "09:00", in the locale's digits. */
fun clock(minutes: Int): String = String.format(Locale.getDefault(), "%02d:%02d", (minutes / 60) % 24, minutes % 60)

/** Today, as apps/api numbers days (0 = Sunday). */
fun todayIndex(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
