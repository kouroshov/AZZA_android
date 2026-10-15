package com.azza.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.azza.app.R
import com.azza.feature.basket.BasketRoute
import com.azza.feature.create.CreateRoute
import com.azza.feature.feed.FeedRoute
import com.azza.feature.map.MapRoute
import com.azza.feature.profile.ProfileRoute
import com.azza.feature.search.SearchRoute
import kotlin.reflect.KClass

/**
 * The phone bottom bar, in the web's order (Sidebar.tsx `bottomBarItems`):
 * Following, Explore, Map, "+", Basket, your own page. "+" only appears for
 * a signed-in person, as on the web. Destinations only: the bell and the
 * "more" menu live in the top bar.
 */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val signedInOnly: Boolean = false,
) {
    FOLLOWING(FeedRoute, FeedRoute::class, R.string.nav_following, Icons.Outlined.Home, Icons.Filled.Home),
    EXPLORE(SearchRoute, SearchRoute::class, R.string.nav_explore, Icons.Outlined.Explore, Icons.Filled.Explore),
    MAP(MapRoute, MapRoute::class, R.string.nav_map, Icons.Outlined.Place, Icons.Filled.Place),
    CREATE(CreateRoute, CreateRoute::class, R.string.nav_create, Icons.Outlined.AddBox, Icons.Filled.AddBox, signedInOnly = true),
    BASKET(BasketRoute(), BasketRoute::class, R.string.nav_basket, Icons.Outlined.ShoppingBag, Icons.Filled.ShoppingBag),
    PROFILE(ProfileRoute, ProfileRoute::class, R.string.nav_profile, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}
