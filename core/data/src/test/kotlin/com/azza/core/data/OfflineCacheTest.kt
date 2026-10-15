package com.azza.core.data

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.database.CachedResponse
import com.azza.core.database.CachedResponseDao
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Test

class OfflineCacheTest {
    private class FakeDao : CachedResponseDao {
        val rows = linkedMapOf<String, CachedResponse>()
        override suspend fun get(key: String) = rows[key]
        override suspend fun put(row: CachedResponse) { rows[row.key] = row }
        override suspend fun deletePersonal() { rows.values.removeAll { it.personal } }
        override suspend fun trim(prefix: String, keep: Int) {
            val stale = rows.values.filter { it.key.startsWith(prefix) }.sortedByDescending { it.savedAt }.drop(keep)
            stale.forEach { rows.remove(it.key) }
        }
    }

    private val dao = FakeDao()
    private val signedIn = MutableStateFlow(true)
    private var clock = 1_000L
    private val cache = OfflineCache(dao, Json, signedIn, TestScope(UnconfinedTestDispatcher()), now = { clock })
    private val online = listOf("a", "b")

    private suspend fun load(key: String = "feed", personal: Boolean = true, keep: Int? = null, answer: Outcome<List<String>>) =
        cache.load(key, personal, ListSerializer(String.serializer()), keep) { answer }

    @Test
    fun `an answer is passed through and saved`() = runTest {
        assertThat(load(answer = Outcome.Success(online))).isEqualTo(Outcome.Success(Fresh(online)))
        assertThat(dao.rows.getValue("feed").savedAt).isEqualTo(1_000L)
    }

    @Test
    fun `offline, the saved copy comes back with when it was saved`() = runTest {
        load(answer = Outcome.Success(online))
        clock = 5_000L
        assertThat(load(answer = Outcome.Failure(AppError.Network))).isEqualTo(Outcome.Success(Fresh(online, savedAt = 1_000L)))
    }

    @Test
    fun `a server's error is never hidden behind the copy`() = runTest {
        load(answer = Outcome.Success(online))
        val refused = Outcome.Failure(AppError.Api(401, "AUTH_REQUIRED", "", null))
        assertThat(load(answer = refused)).isEqualTo(refused)
    }

    @Test
    fun `offline with nothing saved is still offline`() = runTest {
        assertThat(load(answer = Outcome.Failure(AppError.Network))).isEqualTo(Outcome.Failure(AppError.Network))
    }

    @Test
    fun `signing out deletes only the personal copies`() = runTest {
        load(key = "explore", personal = false, answer = Outcome.Success(online))
        load(key = "orders", personal = true, answer = Outcome.Success(online))
        signedIn.value = false
        assertThat(dao.rows.keys).containsExactly("explore")
    }

    @Test
    fun `opened listings keep only the newest`() = runTest {
        for (i in 1..4) {
            clock = i.toLong()
            load(key = "product:$i", keep = 2, answer = Outcome.Success(online))
        }
        assertThat(dao.rows.keys).containsExactly("product:3", "product:4")
    }
}
