package com.azza.core.data

import com.azza.core.model.Condition
import com.azza.core.model.SchemaRules
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

/** The schema's condition JSON (apps/api schema-rules.ts `Condition`) becomes the same AST the rules evaluate. */
class ConditionParsingTest {

    private fun parse(text: String) = Json.parseToJsonElement(text).toCondition()

    @Test
    fun `a leaf keeps its field, op and typed value`() {
        assertThat(parse("""{"field":"flag","op":"eq","value":true}""")).isEqualTo(Condition.Leaf("flag", "eq", true))
        assertThat(parse("""{"field":"count","op":"gt","value":50}""")).isEqualTo(Condition.Leaf("count", "gt", 50.0))
        assertThat(parse("""{"field":"pick","op":"in","value":["a","c"]}""")).isEqualTo(Condition.Leaf("pick", "in", listOf("a", "c")))
        assertThat(parse("""{"field":"x","op":"exists"}""")).isEqualTo(Condition.Leaf("x", "exists", null))
    }

    @Test
    fun `nested all, any and not evaluate like the server`() {
        val condition = parse(
            """{"all":[{"any":[{"field":"pick","op":"eq","value":"a"},{"field":"pick","op":"eq","value":"c"}]},
               {"not":{"field":"count","op":"gt","value":50}}]}""",
        )
        assertThat(SchemaRules.evaluate(condition, mapOf("pick" to "c", "count" to 10.0))).isTrue()
        assertThat(SchemaRules.evaluate(condition, mapOf("pick" to "c", "count" to 90.0))).isFalse()
        assertThat(SchemaRules.evaluate(condition, mapOf("pick" to "b", "count" to 10.0))).isFalse()
    }

    @Test
    fun `null or absent means no condition`() {
        assertThat((null as JsonElement?).toCondition()).isNull()
        assertThat(parse("null")).isNull()
    }
}
