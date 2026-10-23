package com.azza.core.data

import com.azza.core.model.AttrFilter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** ✅ (2026-10-23) The category-field filters travel as apps/api's `attrs` object and come back from a saved search. */
class AttrsJsonTest {

    @Test
    fun `writes the shape attribute-filter ts reads`() {
        val json = attrsJson(
            linkedMapOf(
                "rooms" to AttrFilter.Options(listOf("2", "3")),
                "parking" to AttrFilter.Yes,
                "area_m2" to AttrFilter.Range(min = 50.0),
            ),
        )
        assertThat(json).isEqualTo("""{"rooms":["2","3"],"parking":true,"area_m2":{"min":50.0}}""")
    }

    @Test
    fun `reads it back`() {
        val back = parseAttrsJson("""{"rooms":["2"],"parking":true,"area_m2":{"min":50,"max":120}}""")
        assertThat(back).containsExactly(
            "rooms", AttrFilter.Options(listOf("2")),
            "parking", AttrFilter.Yes,
            "area_m2", AttrFilter.Range(50.0, 120.0),
        )
    }

    @Test
    fun `drops what it does not understand`() {
        assertThat(parseAttrsJson("not json")).isEmpty()
        assertThat(parseAttrsJson(null)).isEmpty()
        assertThat(parseAttrsJson("""{"a":false,"b":[],"c":{}}""")).isEmpty()
    }
}
