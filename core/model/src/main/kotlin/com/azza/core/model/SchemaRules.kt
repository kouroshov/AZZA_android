package com.azza.core.model

import java.util.Calendar
import java.util.TimeZone

// The listing-attribute rule engine: condition evaluation + validation of a
// listing's attribute values against a resolved category schema.
//
// ⚠️ A THIRD copy of apps/api/src/taxonomy/schema-rules.ts (the authority;
// the web keeps the second in src/lib/taxonomy/schemaRules.ts). The server
// decides — this copy only gives the form the same live feedback the web
// gives. SchemaRulesConformanceTest runs apps/api's conformance cases
// (schema-rules.conformance.ts) through it: when the server's rules change,
// port the change here and copy the new cases.
//
// Values are what a form produces: String, Number, Boolean, List<String>.

enum class AttributeDataType { TEXT, LONG_TEXT, NUMBER, MONEY, BOOLEAN, ENUM, MULTI_ENUM, DATE, UNKNOWN }

data class SchemaOption(val value: String, val label: LocalizedText)

/** A small, data-only condition AST. A leaf reads an attribute slug, or `$condition` / `$offeringKind` / `$listingType`. */
sealed interface Condition {
    data class All(val conditions: List<Condition>) : Condition
    data class AnyOf(val conditions: List<Condition>) : Condition
    data class Not(val condition: Condition) : Condition
    data class Leaf(val field: String, val op: String, val value: Any?) : Condition
}

data class SchemaField(
    val slug: String,
    val label: LocalizedText,
    val help: LocalizedText? = null,
    val dataType: AttributeDataType,
    val unit: String? = null,
    val required: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val options: List<SchemaOption>? = null,
    val visibleWhen: Condition? = null,
    val requiredWhen: Condition? = null,
    val group: String? = null,
)

/** ✅ (2026-10-23) A kind of ad: for sale, for rent, wanted, a vacancy… */
data class ListingType(
    val code: String,
    val name: LocalizedText,
    /** REQUIRED: a price above 0; OPTIONAL: may be 0; NONE: always 0. */
    val priceMode: String = "REQUIRED",
    val purchasable: Boolean = true,
)

data class CategorySchema(
    val categoryId: String,
    val offeringKind: OfferingKind,
    val allowedKinds: List<OfferingKind>,
    val fields: List<SchemaField>,
    /** ✅ (2026-10-23) the kind of ad the fields were resolved for, and the ones the category takes. */
    val listingType: ListingType? = null,
    val allowedListingTypes: List<ListingType> = emptyList(),
    /** Whether "New / Used" is asked in this category. */
    val usesCondition: Boolean = true,
)

data class RuleContext(val condition: String? = null, val offeringKind: String? = null, val listingType: String? = null)

enum class FieldErrorCode { REQUIRED, UNKNOWN_FIELD, INVALID_TYPE, NOT_AN_OPTION, DUPLICATE_OPTION, TOO_SMALL, TOO_LARGE, TOO_LONG, PATTERN_MISMATCH }

data class FieldError(val field: String, val code: FieldErrorCode)

object SchemaRules {
    const val DEFAULT_TEXT_MAX_LENGTH = 200
    const val DEFAULT_LONG_TEXT_MAX_LENGTH = 5000

    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun toLatinDigits(text: String): String = buildString {
        for (ch in text) {
            val p = PERSIAN_DIGITS.indexOf(ch)
            val a = ARABIC_DIGITS.indexOf(ch)
            append(if (p >= 0) ('0' + p) else if (a >= 0) ('0' + a) else ch)
        }
    }

    fun isEmptyValue(value: Any?): Boolean = when (value) {
        null -> true
        is String -> value.isBlank()
        is List<*> -> value.isEmpty()
        else -> false
    }

    /** JavaScript's `Number(x)` for the values a form can hold; NaN when it would be NaN. */
    private fun toNumber(x: Any?): Double = when (x) {
        is Number -> x.toDouble()
        is Boolean -> if (x) 1.0 else 0.0
        is String -> x.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
        else -> Double.NaN
    }

    private fun looseEqual(a: Any?, b: Any?): Boolean {
        if (a == b) return true
        if (a is Number && b is Number) return a.toDouble() == b.toDouble()
        if (a is Number || b is Number) {
            val na = toNumber(a)
            val nb = toNumber(b)
            return !na.isNaN() && !nb.isNaN() && na == nb
        }
        return false
    }

    private fun readField(field: String, values: Map<String, Any?>, ctx: RuleContext): Any? = when (field) {
        "\$condition" -> ctx.condition
        "\$offeringKind" -> ctx.offeringKind
        "\$listingType" -> ctx.listingType
        else -> values[field]
    }

    fun evaluate(condition: Condition?, values: Map<String, Any?>, ctx: RuleContext = RuleContext()): Boolean {
        return when (condition) {
            null -> true
            is Condition.All -> condition.conditions.all { evaluate(it, values, ctx) }
            is Condition.AnyOf -> condition.conditions.any { evaluate(it, values, ctx) }
            is Condition.Not -> !evaluate(condition.condition, values, ctx)
            is Condition.Leaf -> {
                val actual = readField(condition.field, values, ctx)
                val expected = condition.value
                when (condition.op) {
                    "eq" -> !isEmptyValue(actual) && looseEqual(actual, expected)
                    "neq" -> isEmptyValue(actual) || !looseEqual(actual, expected)
                    "in" -> !isEmptyValue(actual) && expected is List<*> && expected.any { looseEqual(actual, it) }
                    "nin" -> isEmptyValue(actual) || expected !is List<*> || expected.none { looseEqual(actual, it) }
                    "gt", "gte", "lt", "lte" -> {
                        if (isEmptyValue(actual)) return false
                        val a = toNumber(actual)
                        val e = toNumber(expected)
                        if (a.isNaN() || e.isNaN()) return false
                        when (condition.op) {
                            "gt" -> a > e
                            "gte" -> a >= e
                            "lt" -> a < e
                            else -> a <= e
                        }
                    }
                    "exists" -> !isEmptyValue(actual)
                    "empty" -> isEmptyValue(actual)
                    "contains" -> actual is List<*> && actual.any { looseEqual(it, expected) }
                    else -> false
                }
            }
        }
    }

    private sealed interface Coerced {
        data class Ok(val value: Any?) : Coerced
        data class Err(val code: FieldErrorCode) : Coerced
    }

    private val NUMBER_SHAPE = Regex("^-?\\d+(\\.\\d+)?$")
    /** An amount in major units: no sign, at most two decimals (the server stores minor units). */
    private val MONEY_SHAPE = Regex("^\\d+(\\.\\d{1,2})?$")
    private val DATE_SHAPE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    private fun coerce(field: SchemaField, raw: Any?): Coerced {
        if (isEmptyValue(raw)) return Coerced.Ok(null)
        val optionValues = field.options.orEmpty().map { it.value }
        return when (field.dataType) {
            AttributeDataType.TEXT, AttributeDataType.LONG_TEXT -> {
                if (raw !is String && raw !is Number) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                val text = (if (raw is Number) numberText(raw) else raw as String).trim()
                val limit = field.maxLength
                    ?: if (field.dataType == AttributeDataType.TEXT) DEFAULT_TEXT_MAX_LENGTH else DEFAULT_LONG_TEXT_MAX_LENGTH
                if (text.length > limit) return Coerced.Err(FieldErrorCode.TOO_LONG)
                field.pattern?.let { pattern ->
                    val re = runCatching { Regex(pattern) }.getOrNull() ?: return Coerced.Err(FieldErrorCode.PATTERN_MISMATCH)
                    if (!re.containsMatchIn(toLatinDigits(text))) return Coerced.Err(FieldErrorCode.PATTERN_MISMATCH)
                }
                Coerced.Ok(text)
            }
            AttributeDataType.NUMBER -> {
                val n: Double = when (raw) {
                    is Number -> raw.toDouble()
                    is String -> {
                        val cleaned = toLatinDigits(raw.trim()).replace(Regex("[,٬]"), "").replaceFirst('٫', '.')
                        if (!NUMBER_SHAPE.matches(cleaned)) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                        cleaned.toDouble()
                    }
                    else -> return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                }
                if (n.isNaN() || n.isInfinite()) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                if (field.min != null && n < field.min) return Coerced.Err(FieldErrorCode.TOO_SMALL)
                if (field.max != null && n > field.max) return Coerced.Err(FieldErrorCode.TOO_LARGE)
                Coerced.Ok(n)
            }
            AttributeDataType.MONEY -> {
                val text = when (raw) {
                    is Number -> numberText(raw)
                    is String -> toLatinDigits(raw.trim()).replace(Regex("[,٬]"), "").replaceFirst('٫', '.')
                    else -> return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                }
                if (!MONEY_SHAPE.matches(text)) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                val n = text.toDouble()
                if (n.isNaN() || n.isInfinite()) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                if (field.min != null && n < field.min) return Coerced.Err(FieldErrorCode.TOO_SMALL)
                if (field.max != null && n > field.max) return Coerced.Err(FieldErrorCode.TOO_LARGE)
                Coerced.Ok(n)
            }
            AttributeDataType.BOOLEAN -> when (raw) {
                is Boolean -> Coerced.Ok(raw)
                "true" -> Coerced.Ok(true)
                "false" -> Coerced.Ok(false)
                else -> Coerced.Err(FieldErrorCode.INVALID_TYPE)
            }
            AttributeDataType.ENUM -> when {
                raw !is String -> Coerced.Err(FieldErrorCode.INVALID_TYPE)
                raw !in optionValues -> Coerced.Err(FieldErrorCode.NOT_AN_OPTION)
                else -> Coerced.Ok(raw)
            }
            AttributeDataType.MULTI_ENUM -> {
                if (raw !is List<*> || raw.any { it !is String }) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                val items = raw.map { it as String }
                if (items.toSet().size != items.size) return Coerced.Err(FieldErrorCode.DUPLICATE_OPTION)
                if (items.any { it !in optionValues }) return Coerced.Err(FieldErrorCode.NOT_AN_OPTION)
                if (field.max != null && items.size > field.max) return Coerced.Err(FieldErrorCode.TOO_LARGE)
                // Stored in the schema's option order, so equal sets compare equal.
                Coerced.Ok(optionValues.filter { it in items })
            }
            AttributeDataType.DATE -> {
                if (raw !is String) return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                val text = toLatinDigits(raw.trim())
                val m = DATE_SHAPE.matchEntire(text) ?: return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                val (y, mo, d) = m.destructured.toList().map { it.toInt() }
                val probe = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    isLenient = true
                    set(y, mo - 1, d)
                }
                if (probe.get(Calendar.YEAR) != y || probe.get(Calendar.MONTH) != mo - 1 || probe.get(Calendar.DAY_OF_MONTH) != d) {
                    return Coerced.Err(FieldErrorCode.INVALID_TYPE)
                }
                Coerced.Ok(text)
            }
            AttributeDataType.UNKNOWN -> Coerced.Err(FieldErrorCode.INVALID_TYPE)
        }
    }

    /** How JavaScript's String(n) writes a number: no ".0" on a whole one. */
    private fun numberText(n: Number): String {
        val d = n.toDouble()
        return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()
    }

    fun isVisible(field: SchemaField, values: Map<String, Any?>, ctx: RuleContext = RuleContext()): Boolean =
        evaluate(field.visibleWhen, values, ctx)

    fun isRequired(field: SchemaField, values: Map<String, Any?>, ctx: RuleContext = RuleContext()): Boolean {
        if (!isVisible(field, values, ctx)) return false
        return field.required || (field.requiredWhen != null && evaluate(field.requiredWhen, values, ctx))
    }

    data class Result(val values: Map<String, Any>, val errors: List<FieldError>)

    /**
     * Validates [input] against [fields] (in schema order). Returns the clean,
     * typed values — hidden fields and empty inputs dropped — and every error.
     */
    fun validate(fields: List<SchemaField>, input: Map<String, Any?>, ctx: RuleContext = RuleContext()): Result {
        val errors = mutableListOf<FieldError>()
        val known = fields.mapTo(HashSet()) { it.slug }
        input.keys.filter { it !in known }.forEach { errors += FieldError(it, FieldErrorCode.UNKNOWN_FIELD) }
        // Only values of fields that are visible and valid so far: what later conditions see.
        val accepted = LinkedHashMap<String, Any>()
        for (field in fields) {
            if (!isVisible(field, accepted, ctx)) continue
            when (val result = coerce(field, input[field.slug])) {
                is Coerced.Err -> errors += FieldError(field.slug, result.code)
                is Coerced.Ok -> {
                    if (result.value == null) {
                        if (isRequired(field, accepted, ctx)) errors += FieldError(field.slug, FieldErrorCode.REQUIRED)
                    } else {
                        accepted[field.slug] = result.value
                    }
                }
            }
        }
        return Result(accepted, errors)
    }
}
