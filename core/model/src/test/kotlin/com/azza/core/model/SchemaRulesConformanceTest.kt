package com.azza.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * apps/api/src/taxonomy/schema-rules.conformance.ts, case for case: the
 * same FIELDS and CASES the server and the web run, so this copy of the
 * rules cannot drift from theirs unnoticed. Numbers are compared as numbers
 * (JavaScript has one number type; 42 and 42.0 are the same value there).
 */
class SchemaRulesConformanceTest {

    private fun field(
        slug: String,
        type: AttributeDataType,
        required: Boolean = false,
        min: Double? = null,
        max: Double? = null,
        maxLength: Int? = null,
        pattern: String? = null,
        options: List<SchemaOption>? = null,
        visibleWhen: Condition? = null,
        requiredWhen: Condition? = null,
    ) = SchemaField(slug, LocalizedText(slug, slug), null, type, null, required, min, max, maxLength, pattern, options, visibleWhen, requiredWhen)

    private val options = listOf("a", "b", "c").map { SchemaOption(it, LocalizedText(it.uppercase(), it.uppercase())) }

    private val fields = listOf(
        field("title", AttributeDataType.TEXT, required = true, maxLength = 10),
        field("count", AttributeDataType.NUMBER, min = 1.0, max = 100.0),
        field("flag", AttributeDataType.BOOLEAN),
        field("pick", AttributeDataType.ENUM, options = options),
        field("many", AttributeDataType.MULTI_ENUM, options = options),
        field("when", AttributeDataType.DATE),
        field("code", AttributeDataType.TEXT, pattern = "^[0-9]{3}$"),
        field(
            "extra", AttributeDataType.TEXT,
            visibleWhen = Condition.Leaf("flag", "eq", true),
            requiredWhen = Condition.Leaf("flag", "eq", true),
        ),
        field("mileage", AttributeDataType.NUMBER, visibleWhen = Condition.Leaf("\$condition", "neq", "NEW")),
        field("area", AttributeDataType.TEXT, visibleWhen = Condition.Leaf("many", "contains", "b")),
        field(
            "nested", AttributeDataType.TEXT,
            visibleWhen = Condition.All(
                listOf(
                    Condition.AnyOf(listOf(Condition.Leaf("pick", "eq", "a"), Condition.Leaf("pick", "eq", "c"))),
                    Condition.Not(Condition.Leaf("count", "gt", 50)),
                ),
            ),
        ),
    )

    private fun check(
        input: Map<String, Any?>,
        expectValues: Map<String, Any>,
        expectErrors: List<Pair<String, FieldErrorCode>> = emptyList(),
        ctx: RuleContext = RuleContext(),
    ) {
        val result = SchemaRules.validate(fields, input, ctx)
        assertThat(normalize(result.values)).isEqualTo(normalize(expectValues))
        assertThat(result.errors.map { it.field to it.code }).containsExactlyElementsIn(expectErrors)
    }

    /** Numbers as Double, so 42 (Int) and 42.0 compare equal — as they are in JavaScript. */
    private fun normalize(values: Map<String, Any>): Map<String, Any> = values.mapValues { (_, v) -> if (v is Number) v.toDouble() else v }

    @Test fun `coerces strings to their declared types`() = check(
        mapOf("title" to " hello ", "count" to "42", "flag" to "true", "pick" to "b", "many" to listOf("c", "a"), "when" to "2026-03-21", "extra" to "e"),
        mapOf("title" to "hello", "count" to 42, "flag" to true, "pick" to "b", "many" to listOf("a", "c"), "when" to "2026-03-21", "extra" to "e"),
    )

    @Test fun `accepts Persian digits in a number`() = check(mapOf("title" to "x", "count" to "۱۲"), mapOf("title" to "x", "count" to 12))

    @Test fun `a missing required field is REQUIRED`() = check(mapOf("count" to 5), mapOf("count" to 5), listOf("title" to FieldErrorCode.REQUIRED))

    @Test fun `an empty string counts as missing`() = check(mapOf("title" to "   "), emptyMap(), listOf("title" to FieldErrorCode.REQUIRED))

    @Test fun `range, length, option and pattern violations`() = check(
        mapOf("title" to "way too long for ten", "count" to 500, "pick" to "z", "many" to listOf("a", "zz"), "code" to "12"),
        emptyMap(),
        listOf(
            "title" to FieldErrorCode.TOO_LONG,
            "count" to FieldErrorCode.TOO_LARGE,
            "pick" to FieldErrorCode.NOT_AN_OPTION,
            "many" to FieldErrorCode.NOT_AN_OPTION,
            "code" to FieldErrorCode.PATTERN_MISMATCH,
        ),
    )

    @Test fun `a number below the minimum is TOO_SMALL`() =
        check(mapOf("title" to "x", "count" to 0), mapOf("title" to "x"), listOf("count" to FieldErrorCode.TOO_SMALL))

    @Test fun `a repeated multi-select option is DUPLICATE_OPTION`() =
        check(mapOf("title" to "x", "many" to listOf("a", "a")), mapOf("title" to "x"), listOf("many" to FieldErrorCode.DUPLICATE_OPTION))

    @Test fun `an invalid calendar date is refused`() =
        check(mapOf("title" to "x", "when" to "2026-02-31"), mapOf("title" to "x"), listOf("when" to FieldErrorCode.INVALID_TYPE))

    @Test fun `a key that is not a field is UNKNOWN_FIELD`() =
        check(mapOf("title" to "x", "nope" to 1), mapOf("title" to "x"), listOf("nope" to FieldErrorCode.UNKNOWN_FIELD))

    @Test fun `a hidden field's value is dropped, and it is never required`() =
        check(mapOf("title" to "x", "flag" to false, "extra" to "should be dropped"), mapOf("title" to "x", "flag" to false))

    @Test fun `showing a field can make it required`() =
        check(mapOf("title" to "x", "flag" to true), mapOf("title" to "x", "flag" to true), listOf("extra" to FieldErrorCode.REQUIRED))

    @Test fun `a visible conditional field is kept`() =
        check(mapOf("title" to "x", "flag" to true, "extra" to "kept"), mapOf("title" to "x", "flag" to true, "extra" to "kept"))

    @Test fun `context - mileage is hidden on a NEW listing`() =
        check(mapOf("title" to "x", "mileage" to 1000), mapOf("title" to "x"), ctx = RuleContext(condition = "NEW"))

    @Test fun `context - mileage is kept on a USED listing`() =
        check(mapOf("title" to "x", "mileage" to 1000), mapOf("title" to "x", "mileage" to 1000), ctx = RuleContext(condition = "USED"))

    @Test fun `contains reads a multi-select`() = check(
        mapOf("title" to "x", "many" to listOf("b"), "area" to "Tehran"),
        mapOf("title" to "x", "many" to listOf("b"), "area" to "Tehran"),
    )

    @Test fun `contains false hides the dependent field`() =
        check(mapOf("title" to "x", "many" to listOf("a"), "area" to "Tehran"), mapOf("title" to "x", "many" to listOf("a")))

    @Test fun `nested all-any-not - visible`() = check(
        mapOf("title" to "x", "count" to 10, "pick" to "c", "nested" to "yes"),
        mapOf("title" to "x", "count" to 10, "pick" to "c", "nested" to "yes"),
    )

    @Test fun `nested all-any-not - hidden by the not`() =
        check(mapOf("title" to "x", "count" to 90, "pick" to "c", "nested" to "no"), mapOf("title" to "x", "count" to 90, "pick" to "c"))
}
