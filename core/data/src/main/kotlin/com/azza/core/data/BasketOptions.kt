package com.azza.core.data

import com.azza.core.common.ApplicationScope
import com.azza.core.datastore.AppPreferences
import com.azza.core.datastore.SessionStore
import com.azza.core.model.Money
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** An option chosen for a basket line: [perItem] times for EACH item of the line. */
data class ChosenOption(val optionId: String, val name: String, val perItem: Int, val extra: Money)

/**
 * The options chosen for what is in the basket (phase 5c). apps/api's
 * server cart holds a product and a quantity, nothing more — the website
 * keeps its whole basket in the browser and sends the options at checkout.
 * So the app keeps the choices on the phone, one set per product (adding
 * the product again replaces it), and checkout sends them with explicit
 * lines. Prices here are for display only: checkout reads every option's
 * price from the database. Cleared on sign-out and after a checkout.
 */
@Singleton
class BasketOptions @Inject constructor(
    private val preferences: AppPreferences,
    private val json: Json,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    val chosen: StateFlow<Map<String, List<ChosenOption>>> =
        preferences.basketOptions.map { raw -> raw?.let(::decode).orEmpty() }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch { sessionStore.isSignedIn.drop(1).collect { if (!it) clear() } }
    }

    suspend fun set(productId: String, options: List<ChosenOption>) {
        val next = chosen.value.toMutableMap().apply { if (options.isEmpty()) remove(productId) else put(productId, options) }
        preferences.setBasketOptions(encode(next))
    }

    suspend fun clear() = preferences.setBasketOptions(null)

    private fun encode(map: Map<String, List<ChosenOption>>): String? = if (map.isEmpty()) null else JsonObject(
        map.mapValues { (_, list) ->
            JsonArray(
                list.map { o ->
                    buildJsonObject {
                        put("id", o.optionId)
                        put("name", o.name)
                        put("n", o.perItem)
                        put("minor", o.extra.amountMinor)
                        put("cur", o.extra.currency)
                    }
                },
            )
        },
    ).toString()

    private fun decode(raw: String): Map<String, List<ChosenOption>> = runCatching {
        json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) ->
            v.jsonArray.map { e ->
                val o = e.jsonObject
                ChosenOption(
                    optionId = o.getValue("id").jsonPrimitive.content,
                    name = o.getValue("name").jsonPrimitive.content,
                    perItem = o.getValue("n").jsonPrimitive.int,
                    extra = Money(o.getValue("minor").jsonPrimitive.long, o.getValue("cur").jsonPrimitive.content),
                )
            }
        }
    }.getOrDefault(emptyMap())
}
