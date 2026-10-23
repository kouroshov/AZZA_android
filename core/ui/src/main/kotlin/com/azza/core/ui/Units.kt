package com.azza.core.ui

import java.text.NumberFormat
import java.util.Locale

// ✅ (2026-10-23, the audit: "«2500 m²» beside «۳٬۱۷۳ m²», units not
// translated"). The web's src/lib/units.ts: a unit said in the app's
// language, and a plain number in its digits. The stored unit stays the
// short international one; an unknown unit passes through unchanged.

private val FA = mapOf(
    "m" to "متر", "m²" to "متر مربع", "km" to "کیلومتر", "ha" to "هکتار", "ft" to "فوت", "inch" to "اینچ",
    "months" to "ماه", "weeks" to "هفته", "days" to "روز", "h" to "ساعت", "min" to "دقیقه",
    "GB" to "گیگابایت", "W" to "وات", "kW" to "کیلووات", "kWh" to "کیلووات‌ساعت", "hp" to "اسب بخار",
    "m³/h" to "متر مکعب بر ساعت", "cc" to "سی‌سی", "g" to "گرم", "kg" to "کیلوگرم", "t" to "تن", "L" to "لیتر", "%" to "٪",
)

private val CKB = mapOf(
    "m" to "مەتر", "m²" to "مەتری چوارگۆشە", "km" to "کیلۆمەتر", "ha" to "هێکتار", "ft" to "فووت", "inch" to "ئینچ",
    "months" to "مانگ", "weeks" to "هەفتە", "days" to "ڕۆژ", "h" to "کاتژمێر", "min" to "خولەک",
    "GB" to "گیگابایت", "W" to "وات", "kW" to "کیلۆوات", "kWh" to "کیلۆوات‌کاتژمێر", "hp" to "هێزی ئەسپ",
    "m³/h" to "مەتری سێجا لە کاتژمێرێکدا", "cc" to "سی‌سی", "g" to "گرام", "kg" to "کیلۆگرام", "t" to "تەن", "L" to "لیتر", "%" to "٪",
)

fun localizeUnit(unit: String?, locale: Locale = Locale.getDefault()): String {
    if (unit.isNullOrBlank()) return ""
    return when (locale.language) {
        "fa" -> FA[unit]
        "ckb" -> CKB[unit]
        else -> null
    } ?: unit
}

/** A number in the app language's digits, grouped from five digits up (a year stays «۱۳۹۵»). */
fun formatPlainNumber(n: Double, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = 2
    format.isGroupingUsed = kotlin.math.abs(n) >= 10_000
    return format.format(n)
}
