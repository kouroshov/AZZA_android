package com.azza.core.data

import com.azza.core.model.LocalizedText
import com.azza.core.model.OpenHours
import com.azza.core.model.PlaceHit
import com.azza.core.network.api.OwnListingsApi
import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.AppPreferences
import com.azza.core.datastore.ChosenCity
import com.azza.core.datastore.SessionStore
import com.azza.core.model.Area
import com.azza.core.model.Money
import com.azza.core.model.Page
import com.azza.core.model.PostTile
import com.azza.core.model.PublicProfile
import com.azza.core.model.StorePage
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.FollowApi
import com.azza.core.network.api.GeographyApi
import com.azza.core.network.api.ProductsApi
import com.azza.core.network.api.StoresApi
import com.azza.core.network.api.UsersApi
import com.azza.core.network.model.ProductDto
import com.azza.core.network.model.ProductsPageDto
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Singleton
class ProfilesRepository @Inject constructor(
    private val users: UsersApi,
    private val stores: StoresApi,
    private val products: ProductsApi,
    private val own: OwnListingsApi,
    private val mediaUrls: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun user(username: String): Outcome<PublicProfile> = safeApiCall(json) { users.byUsername(username).data }.map {
        PublicProfile(it.id, it.username, it.displayName.ifBlank { it.username }, mediaUrls.resolve(it.avatarUrl), it.bio, it.cityName, it.followerCount, it.postCount)
    }

    suspend fun store(idOrSlug: String): Outcome<StorePage> = safeApiCall(json) { stores.get(idOrSlug).data }.map {
        StorePage(
            id = it.id,
            ownerId = it.ownerId,
            slug = it.slug,
            name = it.name,
            description = it.description,
            avatarUrl = mediaUrls.resolve(it.avatarUrl),
            followerCount = it.followerCount,
            openStatus = it.openStatus,
            postCount = it.postCount,
            ownerUsername = it.owner?.username,
            ownerName = it.owner?.displayName?.ifBlank { null },
            phone = it.branchPhone?.ifBlank { null },
            occupation = it.occupation?.let { o -> LocalizedText(o.name.fa.ifBlank { o.name.en }, o.name.en.ifBlank { o.code }) },
            address = it.location?.address?.ifBlank { null },
            hours = it.hours.map { h -> OpenHours(h.dayOfWeek, h.opensMinute, h.closesMinute, h.isClosed || h.opensMinute == null) },
        )
    }

    /** The listings on a person's personal page, not their stores' (as the web's /u/[username]). */
    suspend fun personalPosts(userId: String, cursor: String?): Outcome<Page<PostTile>> =
        safeApiCall(json) { products.list(sellerId = userId, personal = true, cursor = cursor).data }.map { it.toTiles() }

    suspend fun storePosts(storeId: String, cursor: String?): Outcome<Page<PostTile>> =
        safeApiCall(json) { products.list(storeId = storeId, cursor = cursor).data }.map { it.toTiles() }

    /** The caller's own personal listings in every state (drafts and held ones too) — their own page only. */
    suspend fun myPersonalPosts(cursor: String?, search: String = ""): Outcome<Page<PostTile>> =
        safeApiCall(json) { own.list(personal = true, cursor = cursor, search = search.trim().ifBlank { null }).data }.map { it.toTiles() }

    /** Every listing of one of the caller's stores, drafts and held ones included (the web's store "Products" tab). */
    suspend fun myStorePosts(storeId: String, cursor: String?, search: String = ""): Outcome<Page<PostTile>> =
        safeApiCall(json) { own.list(storeId = storeId, cursor = cursor, search = search.trim().ifBlank { null }).data }.map { it.toTiles() }

    private fun ProductsPageDto.toTiles() = Page(items.map { it.toTile() }, nextCursor, hasMore)

    private fun ProductDto.toTile() = PostTile(
        id = id,
        title = title,
        imageUrl = media.firstOrNull { it.mediaType == "IMAGE" }?.let { mediaUrls.resolve(it.url ?: it.storageKey) },
        price = Money(priceAmountMinor, currency),
        discountPercentage = discountPercentage,
        status = status,
    )
}

/**
 * Who and which stores the signed-in person follows, in memory, so a
 * Follow button can be drawn without a request per profile. Same pattern
 * as [FavoritesRepository]: loaded per sign-in, optimistic toggles.
 */
@Singleton
class FollowRepository @Inject constructor(
    private val api: FollowApi,
    private val json: Json,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _users = MutableStateFlow<Set<String>>(emptySet())
    val followedUsers: StateFlow<Set<String>> = _users.asStateFlow()

    private val _stores = MutableStateFlow<Set<String>>(emptySet())
    val followedStores: StateFlow<Set<String>> = _stores.asStateFlow()

    init {
        scope.launch {
            sessionStore.isSignedIn.collect { signedIn ->
                if (signedIn) {
                    (safeApiCall(json) { api.following().data } as? Outcome.Success)?.value?.let { set ->
                        _users.value = set.users.mapTo(HashSet()) { it.id }
                        _stores.value = set.stores.mapTo(HashSet()) { it.id }
                    }
                } else {
                    _users.value = emptySet()
                    _stores.value = emptySet()
                }
            }
        }
    }

    suspend fun setFollowingUser(userId: String, follow: Boolean): Outcome<Unit> =
        toggle(_users, userId, follow) { if (follow) api.followUser(userId) else api.unfollowUser(userId) }

    suspend fun setFollowingStore(storeId: String, follow: Boolean): Outcome<Unit> =
        toggle(_stores, storeId, follow) { if (follow) api.followStore(storeId) else api.unfollowStore(storeId) }

    private suspend fun toggle(set: MutableStateFlow<Set<String>>, id: String, on: Boolean, call: suspend () -> Unit): Outcome<Unit> {
        set.update { if (on) it + id else it - id }
        val result = safeApiCall(json) { call() }
        if (result is Outcome.Failure) set.update { if (on) it - id else it + id }
        return result
    }
}

@Singleton
class GeographyRepository @Inject constructor(
    private val api: GeographyApi,
    private val preferences: AppPreferences,
    private val json: Json,
) {
    /** The cities the Explore page's City tab shows, remembered on this device. */
    val chosenCities: Flow<List<Area>> = preferences.cities.map { list -> list.map { Area(it.id, it.name) } }

    /** The picker's "previous selections". */
    val recentCities: Flow<List<Area>> = preferences.recentCities.map { list -> list.map { Area(it.id, it.name) } }

    suspend fun provinces(): Outcome<List<Area>> =
        safeApiCall(json) { api.provinces().data }.map { list -> list.map { Area(it.id, it.name, it.center?.lat, it.center?.lng) }.sortedBy { it.name } }

    suspend fun cities(provinceId: String): Outcome<List<Area>> =
        safeApiCall(json) { api.cities(provinceId).data }.map { list -> list.map { Area(it.id, it.name, it.center?.lat, it.center?.lng) }.sortedBy { it.name } }

    suspend fun chooseCities(cities: List<Area>) {
        val chosen = cities.map { ChosenCity(it.id, it.name) }
        preferences.setCities(chosen)
        if (chosen.isNotEmpty()) preferences.recordRecentCities(chosen)
    }

    /** AZZA's own provinces and cities only: an outside geocoder's hit has no area to filter by. */
    suspend fun search(query: String): Outcome<List<PlaceHit>> =
        safeApiCall(json) { api.search(query).data }.map { hits ->
            hits.filter { it.source == "local" && (it.type == "CITY" || it.type == "PROVINCE") }.map {
                PlaceHit(Area(it.id, it.name, it.latitude, it.longitude), isProvince = it.type == "PROVINCE", provinceName = it.province)
            }
        }
}
