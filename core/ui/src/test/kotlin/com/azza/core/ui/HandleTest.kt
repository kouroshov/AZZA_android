package com.azza.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HandleTest {
    @Test
    fun `a handle is an isolated left-to-right run`() {
        assertThat(handle("sara")).isEqualTo("\u2066@sara\u2069")
    }
}
