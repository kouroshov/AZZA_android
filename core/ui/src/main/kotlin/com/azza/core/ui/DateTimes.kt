package com.azza.core.ui

import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Locale

// Dates and times the way the web writes them with Intl.DateTimeFormat: in
// Persian that is the Solar Hijri (Jalali) calendar with Persian digits,
// otherwise the Gregorian calendar of the locale. android.icu (API 24+)
// does both; java.text cannot do the Persian calendar.

private fun uLocale(locale: Locale): ULocale =
    if (locale.language == "fa") ULocale("fa_IR@calendar=persian") else ULocale.forLocale(locale)

private fun parseInstant(iso: String): java.util.Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", ULocale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
}.getOrNull() ?: runCatching {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", ULocale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
}.getOrNull()

/** apps/api's ISO-8601 instant ("2026-10-14T17:24:21.593Z") as a medium date. */
fun formatInstantDate(iso: String, locale: Locale = Locale.getDefault()): String {
    val date = parseInstant(iso) ?: return iso.take(10)
    return DateFormat.getDateInstance(DateFormat.MEDIUM, uLocale(locale)).format(date)
}

/** "14:30" — an instant's clock time in [timeZone] (the provider's, for a booking). */
fun formatClock(iso: String, timeZone: String, locale: Locale = Locale.getDefault()): String {
    val date = parseInstant(iso) ?: return iso
    return SimpleDateFormat("HH:mm", uLocale(locale)).apply { this.timeZone = TimeZone.getTimeZone(timeZone) }.format(date)
}

/** "Thursday 16 October, 14:30" — weekday, day, month and time in [timeZone]. */
fun formatWhen(iso: String, timeZone: String, locale: Locale = Locale.getDefault()): String {
    val date = parseInstant(iso) ?: return iso
    return SimpleDateFormat("EEEE d MMMM، HH:mm".let { if (locale.language == "fa") it else it.replace("،", ",") }, uLocale(locale))
        .apply { this.timeZone = TimeZone.getTimeZone(timeZone) }
        .format(date)
}

/** A calendar day from apps/api ("2026-10-16") as "Thu 16" — a chip in the booking week. */
fun formatDayChip(day: String, locale: Locale = Locale.getDefault()): String {
    val date = runCatching {
        SimpleDateFormat("yyyy-MM-dd", ULocale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(day)
    }.getOrNull() ?: return day
    return SimpleDateFormat("EEE d", uLocale(locale)).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
}

/** Minutes until an ISO instant, never negative — a hold's countdown. */
fun minutesUntil(iso: String?, now: Long = System.currentTimeMillis()): Int? {
    val date = iso?.let(::parseInstant) ?: return null
    return maxOf(0, Math.ceil((date.time - now) / 60_000.0).toInt())
}
