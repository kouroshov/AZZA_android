package com.azza.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import com.azza.feature.notifications.EXTRA_OPEN_NOTIFICATIONS
import com.azza.core.common.WebDestination
import com.azza.core.ui.LocalWebLinks
import androidx.compose.ui.platform.LocalContext
import com.azza.feature.scan.ScanRoute
import com.azza.feature.scan.scanScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavDestination
import com.azza.feature.settings.EditProfileRoute
import com.azza.feature.settings.NewTicketRoute
import com.azza.feature.settings.SettingsRoute
import com.azza.feature.settings.SupportRoute
import com.azza.feature.settings.TicketRoute
import com.azza.feature.settings.settingsScreens
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
import com.azza.feature.basket.BasketRoute
import com.azza.feature.basket.BasketTab
import com.azza.feature.basket.CheckoutRoute
import com.azza.feature.basket.PlacedOrderRoute
import com.azza.feature.basket.ReceivedOrderRoute
import com.azza.feature.basket.basketScreens
import com.azza.feature.create.CreateRoute
import com.azza.feature.create.EditListingRoute
import com.azza.feature.create.createScreen
import com.azza.feature.feed.FeedRoute
import com.azza.feature.map.mapScreen
import com.azza.feature.notifications.NotificationsRoute
import com.azza.feature.notifications.notificationsScreen
import com.azza.feature.search.SearchRoute
import com.azza.feature.search.searchScreen
import com.azza.feature.profile.StoreProfileRoute
import com.azza.feature.profile.UserProfileRoute
import com.azza.feature.profile.publicProfileScreens
import com.azza.core.model.PostAuthor
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
fun AzzaApp(
    incoming: Intent? = null,
    onIncomingHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showChrome = TopLevelDestination.entries.any { destination.isOn(it) } ||
        destination?.hasRoute(NotificationsRoute::class) == true
    val unread by appViewModel.unreadNotifications.collectAsStateWithLifecycle()
    val basketCount by appViewModel.basketCount.collectAsStateWithLifecycle()
    val signedIn by appViewModel.signedIn.collectAsStateWithLifecycle()

    LaunchedEffect(destination) { appViewModel.refreshBadges() }

    val webLinks = LocalWebLinks.current
    val context = LocalContext.current
    LaunchedEffect(incoming) {
        val intent = incoming ?: return@LaunchedEffect
        if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            navController.navigate(NotificationsRoute) { launchSingleTop = true }
        }
        val link = intent.data?.takeIf { intent.action == Intent.ACTION_VIEW }
        if (link != null) {
            when (val to = webLinks.parse(link.toString())) {
                is WebDestination.Product -> navController.navigate(ProductRoute(to.id))
                is WebDestination.User -> navController.navigate(UserProfileRoute(to.username))
                is WebDestination.Store -> navController.navigate(StoreProfileRoute(to.slug))
                // A page the app has no screen for: the browser, never this app again.
                null -> openInBrowser(context, link)
            }
        }
        onIncomingHandled()
    }

    val askPermission by appViewModel.askNotificationPermission.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(askPermission) {
        if (askPermission && Build.VERSION.SDK_INT >= 33) {
            appViewModel.notificationPermissionAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LifecycleResumeEffect(Unit) {
        appViewModel.refreshBadges()
        onPauseOrDispose { }
    }

    val openProduct: (String) -> Unit = { id -> navController.navigate(ProductRoute(id)) }
    val openPlaced: (String) -> Unit = { id -> navController.navigate(PlacedOrderRoute(id)) }
    val openReceived: (String) -> Unit = { id -> navController.navigate(ReceivedOrderRoute(id)) }
    val signIn: () -> Unit = { navController.navigate(LoginRoute) }
    val openUser: (String) -> Unit = { username -> navController.navigate(UserProfileRoute(username)) }
    val openStore: (String) -> Unit = { idOrSlug -> navController.navigate(StoreProfileRoute(idOrSlug)) }
    val openAuthor: (PostAuthor) -> Unit = { author ->
        if (author.isStore) openStore(author.storeSlug ?: author.id) else openUser(author.handle)
    }

    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = { if (showChrome) AzzaTopBar(
                unread,
                onNotifications = { navController.navigate(NotificationsRoute) { launchSingleTop = true } },
                onMore = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            ) },
        bottomBar = { if (showChrome) AzzaBottomBar(destination, navController, basketCount, signedIn) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FeedRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            feedScreen(
                onOpenProduct = { id -> navController.navigate(ProductRoute(id, source = "FOLLOWING_FEED")) },
                onOpenAuthor = openAuthor,
                onSignIn = signIn,
                onExplore = { navigateTab(navController, SearchRoute) },
            )
            searchScreen(
                onOpenProduct = { id -> navController.navigate(ProductRoute(id, source = "GLOBAL_EXPLORE")) },
                onOpenUser = openUser,
                onOpenStore = openStore,
                onOpenAuthor = openAuthor,
                onScan = { navController.navigate(ScanRoute) },
            )
            scanScreen(
                onBack = { navController.popBackStack() },
                // The listing replaces the scanner, so Back from it returns to Explore.
                onOpenProduct = { id -> navController.navigate(ProductRoute(id)) { popUpTo<ScanRoute> { inclusive = true } } },
            )
            publicProfileScreens(
                onBack = navController::popBackStack,
                onOpenProduct = { id -> navController.navigate(ProductRoute(id, source = "PROFILE")) },
                onSignIn = signIn,
            )
            productScreen(
                onBack = navController::popBackStack,
                onRequireSignIn = signIn,
                onOpenBasket = { navController.navigate(BasketRoute()) { launchSingleTop = true } },
                onEdit = { id -> navController.navigate(EditListingRoute(id)) },
                onOpenAppointments = { navController.navigate(BasketRoute(tab = BasketTab.APPOINTMENTS.ordinal)) },
            )
            profileScreen(onSignIn = signIn, onOpenProduct = { id -> navController.navigate(ProductRoute(id, source = "PROFILE")) })
            basketScreens(
                onSignIn = signIn,
                onBrowse = { navigateTab(navController, SearchRoute) },
                onOpenProduct = openProduct,
                onCheckout = { navController.navigate(CheckoutRoute) },
                onOpenPlaced = { id ->
                    // From checkout, "View order" replaces the checkout screen: going back must not re-open a used form.
                    navController.navigate(PlacedOrderRoute(id)) { popUpTo<CheckoutRoute> { inclusive = true } }
                },
                onOpenReceived = openReceived,
                onBack = navController::popBackStack,
            )
            notificationsScreen(
                onOpenProduct = openProduct,
                onOpenPlacedOrder = openPlaced,
                onOpenReceivedOrder = openReceived,
            )
            authScreens(
                onSignedIn = { navController.popBackStack(LoginRoute, inclusive = true) || navController.popBackStack() },
                onOpenRegister = { navController.navigate(RegisterRoute) { popUpTo(LoginRoute) } },
                onOpenLogin = { navController.navigate(LoginRoute) { popUpTo(LoginRoute) { inclusive = true } } },
            )
            createScreen(
                onPosted = { id ->
                    // The new post replaces the form: back from it returns to where "+" was pressed.
                    navController.navigate(ProductRoute(id)) { popUpTo<CreateRoute> { inclusive = true } }
                },
                onSaved = {
                    // Back to the listing, which re-reads itself.
                    navController.popBackStack()
                },
                onDeleted = {
                    // The listing no longer exists: leave both the form and its page.
                    navController.popBackStack<ProductRoute>(inclusive = true) || navController.popBackStack()
                },
            )
            mapScreen(onOpenStore = openStore, onOpenProduct = { id -> navController.navigate(ProductRoute(id, source = "MAP")) })
            settingsScreens(
                onBack = { navController.popBackStack() },
                onSignIn = signIn,
                onEditProfile = { navController.navigate(EditProfileRoute) },
                onSupport = { navController.navigate(SupportRoute) },
                onNewTicket = { navController.navigate(NewTicketRoute) },
                onOpenTicket = { id -> navController.navigate(TicketRoute(id)) },
                // The new thread replaces the form, so Back returns to the list.
                onTicketCreated = { id -> navController.navigate(TicketRoute(id)) { popUpTo<NewTicketRoute> { inclusive = true } } },
            )
        }
    }
}

@Composable
private fun AzzaTopBar(unread: Int, onNotifications: () -> Unit, onMore: () -> Unit) {
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
                CountBadge(unread) {
                    Icon(Icons.Outlined.Notifications, stringResource(R.string.nav_notifications), tint = AzzaTheme.colors.navInk)
                }
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.nav_more), tint = AzzaTheme.colors.navInk)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
    )
}

@Composable
private fun AzzaBottomBar(current: NavDestination?, navController: NavHostController, basketCount: Int, signedIn: Boolean) {
    NavigationBar(containerColor = AzzaTheme.colors.bg) {
        TopLevelDestination.entries.filter { signedIn || !it.signedInOnly }.forEach { item ->
            val selected = current.isOn(item)
            NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(navController, item.route) },
                icon = {
                    CountBadge(if (item == TopLevelDestination.BASKET) basketCount else 0) {
                        Icon(if (selected) item.selectedIcon else item.icon, contentDescription = stringResource(item.label))
                    }
                },
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

@Composable
private fun CountBadge(count: Int, content: @Composable () -> Unit) {
    if (count <= 0) return content()
    BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) { content() }
}

/** Tabs keep their own back stack and scroll position, like the web's history per page. */
private fun navigateTab(navController: NavHostController, route: Any) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun openInBrowser(context: android.content.Context, link: android.net.Uri) {
    val browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    val view = Intent(Intent.ACTION_VIEW, link).apply { selector = browser }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(view) }
}
