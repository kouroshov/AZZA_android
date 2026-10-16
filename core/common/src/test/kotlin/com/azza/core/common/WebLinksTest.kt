package com.azza.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebLinksTest {
    private val links = WebLinks("https://azza.example/")

    @Test
    fun `builds the website's addresses`() {
        assertThat(links.product("019a-1")).isEqualTo("https://azza.example/store/019a-1")
        assertThat(links.user("sara.ahmadi")).isEqualTo("https://azza.example/u/sara.ahmadi")
        assertThat(links.store("نان بربری")).isEqualTo("https://azza.example/stores/%D9%86%D8%A7%D9%86%20%D8%A8%D8%B1%D8%A8%D8%B1%DB%8C")
    }

    @Test
    fun `reads them back, with or without a locale`() {
        assertThat(links.parse("https://azza.example/store/019a-1")).isEqualTo(WebDestination.Product("019a-1"))
        assertThat(links.parse("https://azza.example/fa/u/sara.ahmadi")).isEqualTo(WebDestination.User("sara.ahmadi"))
        assertThat(links.parse("https://AZZA.example/ckb/stores/bakery/?ref=x")).isEqualTo(WebDestination.Store("bakery"))
        assertThat(links.parse(links.store("نان بربری"))).isEqualTo(WebDestination.Store("نان بربری"))
    }

    @Test
    fun `leaves every other link to the browser`() {
        assertThat(links.parse("https://other.example/store/1")).isNull()
        assertThat(links.parse("https://azza.example/store")).isNull()
        assertThat(links.parse("https://azza.example/admin/users")).isNull()
        assertThat(links.parse("not a link")).isNull()
    }

    @Test
    fun `opens a branch and the two emailed links`() {
        assertThat(links.parse("https://azza.example/stores/bakery/branches/main")).isEqualTo(WebDestination.Branch("bakery", "main"))
        assertThat(links.parse("https://azza.example/reset-password?token=abc%2B1")).isEqualTo(WebDestination.ResetPassword("abc+1"))
        assertThat(links.parse("https://azza.example/fa/confirm-email-change?x=1&token=t0k")).isEqualTo(WebDestination.ConfirmEmailChange("t0k"))
        assertThat(links.parse("https://azza.example/reset-password")).isNull()
        assertThat(links.parse("https://azza.example/stores/bakery/edit/main")).isNull()
    }

    @Test
    fun `a development address keeps its scheme and port`() {
        val dev = WebLinks("http://10.0.2.2:3000")
        assertThat(dev.parse("http://10.0.2.2:3000/fa/store/abc")).isEqualTo(WebDestination.Product("abc"))
        assertThat(dev.parse("http://10.0.2.2:4000/store/abc")).isNull()
    }
}
