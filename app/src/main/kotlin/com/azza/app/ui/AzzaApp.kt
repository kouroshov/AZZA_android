package com.azza.app.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.azza.app.R
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.feature.auth.LoginRoute
import com.azza.feature.auth.RegisterRoute
import com.azza.feature.auth.authScreens
import com.azza.feature.feed.FeedRoute
import com.azza.feature.feed.feedScreen
import com.azza.feature.product.ProductRoute
import com.azza.feature.product.productScreen
import com.azza.feature.profile.ProfileRoute
import com.azza.feature.profile.profileScreen

/**
 * The phone layout of the website (CLAUDE.md §4, "Below md the app is laid
 * out like a phone app"): a top bar with the brand at the start and
 * notifications + "more" at the end, a bottom bar of destinations. Detail
 * and auth screens are full-screen, without either bar.
 */
@Composable
fun AzzaApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showChrome = TopLevelDestination.entries.any { destination.isOn(it) } ||
        destination?.hasRoute(NotificationsRoute::class) == true

    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = { if (showChrome) AzzaTopBar(onNotifications = { navController.navigate(NotificationsRoute) }) },
        bottomBar = { if (showChrome) AzzaBottomBar(destination, navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            feedScreen(onOpenProduct = { id -> navController.navigate(ProductRoute(id, source = "GLOBAL_EXPLORE")) })
            productScreen(onBack = navController::popBackStack)
            profileScreen(onSignIn = { navController.navigate(LoginRoute) })
            authScreens(
                onSignedIn = { navController.popBackStack(LoginRoute, inclusive = true) || navController.popBackStack() },
                onOpenRegister = { navController.navigate(RegisterRoute) { popUpTo(LoginRoute) } },
                onOpenLogin = { navController.navigate(LoginRoute) { popUpTo(LoginRoute) { inclusive = true } } },
            )
            pendingScreens()
        }
    }
}

@Composable
private fun AzzaTopBar(onNotifications: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AzzaTheme.colors.navInk,
            )
        },
        actions = {
            IconButton(onClick = onNotifications) {
                Icon(Icons.Outlined.Notifications, stringResource(R.string.nav_notifications), tint = AzzaTheme.colors.navInk)
            }
            IconButton(onClick = { /* settings menu — docs/ROADMAP.md */ }) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.nav_more), tint = AzzaTheme.colors.navInk)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
    )
}

@Composable
private fun AzzaBottomBar(current: NavDestination?, navController: NavHostController) {
    NavigationBar(containerColor = AzzaTheme.colors.bg) {
        TopLevelDestination.entries.forEach { item ->
            val selected = current.isOn(item)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        // Tabs keep their own back stack and scroll position, like the web's history per page.
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = stringResource(item.label)) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AzzaTheme.colors.navInk,
                    unselectedIconColor = AzzaTheme.colors.navInk.copy(alpha = 0.6f),
                    indicatorColor = AzzaTheme.colors.surfaceRaised,
                ),
            )
        }
    }
}

private fun NavDestination?.isOn(item: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(item.routeClass) } == true
