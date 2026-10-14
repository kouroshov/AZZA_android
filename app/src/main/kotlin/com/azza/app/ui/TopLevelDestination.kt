package com.azza.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.azza.app.R
import com.azza.feature.feed.FeedRoute
import com.azza.feature.profile.ProfileRoute
import kotlin.reflect.KClass

/**
 * The phone bottom bar, in the web's order (CLAUDE.md §4, Sidebar.tsx):
 * feed, search, "+", basket, your own page. Destinations only — the bell
 * and the "more" menu live in the top bar, on every screen.
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    FEED(FeedRoute, FeedRoute::class, R.string.nav_feed, Icons.Outlined.Home, Icons.Filled.Home),
    SEARCH(SearchRoute, SearchRoute::class, R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search),
    CREATE(CreateRoute, CreateRoute::class, R.string.nav_create, Icons.Outlined.AddBox, Icons.Filled.AddBox),
    BASKET(BasketRoute, BasketRoute::class, R.string.nav_basket, Icons.Outlined.ShoppingBag, Icons.Filled.ShoppingBag),
    PROFILE(ProfileRoute, ProfileRoute::class, R.string.nav_profile, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}
