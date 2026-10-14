package com.azza.core.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaUrlResolverTest {

    private val resolver = MediaUrlResolver("http://10.0.2.2:4000/api/v1/")

    @Test
    fun `a storage key is served by apps-api's media route, each segment encoded`() {
        assertThat(resolver.resolve("products/abc/my photo.jpg"))
            .isEqualTo("http://10.0.2.2:4000/api/v1/media/products/abc/my%20photo.jpg")
    }

    @Test
    fun `an absolute url is never rewritten`() {
        assertThat(resolver.resolve("https://cdn.example.com/x.jpg")).isEqualTo("https://cdn.example.com/x.jpg")
    }

    @Test
    fun `a server-relative path resolves against apps-api's origin`() {
        assertThat(resolver.resolve("/api/v1/media/a/b.png")).isEqualTo("http://10.0.2.2:4000/api/v1/media/a/b.png")
    }

    @Test
    fun `blank means no image`() {
        assertThat(resolver.resolve(null)).isNull()
        assertThat(resolver.resolve("  ")).isNull()
    }
}
