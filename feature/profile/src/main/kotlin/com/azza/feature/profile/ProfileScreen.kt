package com.azza.feature.profile

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedTextField
import com.azza.core.ui.OwnerPostsBar
import com.azza.core.ui.PostActionsSheet
import com.azza.core.ui.shareLink
import com.azza.core.ui.LocalWebLinks
import com.azza.core.model.PostTile
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.azza.core.ui.postGrid
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.azza.core.model.FavoriteItem
import com.azza.core.model.FavoriteUnavailable
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.format
import com.azza.core.ui.loadingMoreFooter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.User
import com.azza.core.ui.Avatar
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.handle
import kotlinx.serialization.Serializable

@Serializable data object ProfileRoute

fun NavGraphBuilder.profileScreen(
    onSignIn: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (followers: Boolean) -> Unit,
    onOpenStores: () -> Unit,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    onEditListing: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNewPost: () -> Unit,
    onNewStore: () -> Unit = onOpenStores,
    /** One of your own stores: its management page. */
    onOpenOwnStore: (String) -> Unit = onOpenStore,
) {
    composable<ProfileRoute> { ProfileScreen(onSignIn, onOpenProduct, onEditProfile, onOpenFollowList, onOpenStores, onEditListing, onOpenSettings, onNewPost, onOpenStore, onNewStore, onOpenOwnStore) }
    composable<FollowListRoute> { FollowListScreen(onBack, onOpenUser, onOpenStore) }
}

@Composable
fun ProfileScreen(
    onSignIn: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (followers: Boolean) -> Unit,
    onOpenStores: () -> Unit,
    onEditListing: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNewPost: () -> Unit,
    onOpenStore: (String) -> Unit = {},
    onNewStore: () -> Unit = onOpenStores,
    onOpenOwnStore: (String) -> Unit = onOpenStore,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from posting, editing or deleting a listing: the grid re-reads, keeping what is on screen meanwhile.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshLists()
        onPauseOrDispose { }
    }
    when (val s = state) {
        ProfileUiState.Loading -> LoadingState()
        ProfileUiState.SignedOut -> SignedOut(onSignIn, onOpenSettings)
        is ProfileUiState.Failed ->
            if (s.error == AppError.Unauthenticated) SignedOut(onSignIn, onOpenSettings) else ErrorState(s.error, onRetry = { viewModel.retry() })
        is ProfileUiState.Ready -> {
            val me by viewModel.me.collectAsStateWithLifecycle()
            Profile(me ?: s.user, viewModel, onOpenProduct, onEditProfile, onOpenFollowList, onEditListing, onOpenSettings, onNewPost, onNewStore, onOpenOwnStore)
        }
    }
}

/** The profile header's slots in their phone order: handle, photo, name/bio, then actions (web §4); then Posts | Favorites. */
@Composable
private fun Profile(
    user: User,
    viewModel: ProfileViewModel,
    onOpenProduct: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (followers: Boolean) -> Unit,
    onEditListing: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNewPost: () -> Unit,
    onNewStore: () -> Unit,
    onOpenOwnStore: (String) -> Unit,
) {
    val followers by viewModel.followerCount.collectAsStateWithLifecycle()
    // Read once, so the Stores tab has them when it is opened.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadStores() }
    val postCount by viewModel.postCount.collectAsStateWithLifecycle()
    val owner by viewModel.owner.state.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<PostTile?>(null) }
    var deleteFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val links = LocalWebLinks.current
    menuFor?.let { tile ->
        PostActionsSheet(
            title = tile.title,
            // A personal post has no shelf to label (the web shows the barcode for store posts only).
            showBarcode = false,
            onEdit = { onEditListing(tile.id) },
            onBarcode = {},
            onDelete = { viewModel.owner.deleteOne(tile.id) { ok -> deleteFailed = !ok } },
            onSelect = { viewModel.owner.start(tile.id) },
            onDismiss = { menuFor = null },
        )
    }
    BackHandler(enabled = owner.selecting) { viewModel.owner.stop() }
    val following by viewModel.followingCount.collectAsStateWithLifecycle()
    val colors = AzzaTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favorites by viewModel.favorites.state.collectAsStateWithLifecycle()
    val posts by viewModel.posts.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, if (tab == 0) posts.items.size else favorites.items.size) {
        if (tab == 0) viewModel.posts.loadMore() else viewModel.favorites.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(handle(user.username), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    // Instagram's "+": what you can create — a post, or a store.
                    Box {
                        var createOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { createOpen = true }) { Icon(Icons.Outlined.AddBox, stringResource(R.string.profile_create)) }
                        androidx.compose.material3.DropdownMenu(expanded = createOpen, onDismissRequest = { createOpen = false }) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_new_post)) },
                                leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                                onClick = { createOpen = false; onNewPost() },
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.profile_new_store)) },
                                leadingIcon = { Icon(Icons.Outlined.Storefront, null) },
                                onClick = { createOpen = false; onNewStore() },
                            )
                        }
                    }
                    // Settings, on this page only (it slides in from this edge).
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.profile_settings)) }
                }
                // Photo with the counters beside it (the web's phone header, §4).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Avatar(url = user.avatarUrl, size = 80)
                    Counter(postCount?.toString() ?: "–", pluralStringResource(R.plurals.profile_posts, postCount ?: 0)) { }
                    Counter(followers?.toString() ?: "–", stringResource(R.string.follow_list_followers)) { onOpenFollowList(true) }
                    Counter(following.toString(), stringResource(R.string.follow_list_following)) { onOpenFollowList(false) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    user.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                // Instagram's two actions. Creating (a post, a store) is the "+" above;
                // your stores are the third tab below.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onEditProfile,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceRaised, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.profile_edit)) }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { shareLink(context, user.displayName, links.user(user.username)) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceRaised, contentColor = colors.textPrimary),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.profile_share_profile)) }
                }
            }
        }
        item(key = "tabs") {
            // The web profile's three tabs, with their icons.
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = colors.bg,
                divider = { androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = colors.navDivider) },
                indicator = {
                    androidx.compose.material3.TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(tab, matchContentSize = false),
                        width = androidx.compose.ui.unit.Dp.Unspecified,
                        height = 1.dp,
                        color = colors.textPrimary,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                    )
                },
            ) {
                listOf(
                    Icons.Outlined.GridOn to R.string.profile_tab_posts,
                    Icons.Outlined.FavoriteBorder to R.string.profile_favorites,
                    Icons.Outlined.Storefront to R.string.profile_tab_stores,
                ).forEachIndexed { index, (icon, label) ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index; if (index == 2) viewModel.loadStores() },
                        selectedContentColor = colors.textPrimary,
                        unselectedContentColor = colors.textMuted,
                        modifier = Modifier.height(44.dp),
                    ) { Icon(icon, stringResource(label), Modifier.size(24.dp)) }
                }
            }
        }
        if (tab == 0) {
            item(key = "post-search") {
                val search by viewModel.postSearch.collectAsStateWithLifecycle()
                OutlinedTextField(
                    value = search,
                    onValueChange = viewModel::searchPosts,
                    placeholder = { Text(stringResource(R.string.profile_search_posts)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            when {
                posts.loadingFirst -> item { Text("…", modifier = Modifier.padding(16.dp), color = colors.textMuted) }
                posts.items.isEmpty() -> item {
                    Text(stringResource(R.string.profile_no_posts), modifier = Modifier.padding(16.dp), color = colors.textMuted)
                }
                else -> postGrid(
                    posts.items,
                    onOpenProduct,
                    onLongPress = { menuFor = it },
                    selected = owner.selected.takeIf { owner.selecting },
                    onToggle = viewModel.owner::toggle,
                )
            }
            loadingMoreFooter(posts.loadingMore)
        } else if (tab == 2) {
            val stores = viewModel.stores.value
            when {
                stores == null -> item { Text("…", modifier = Modifier.padding(16.dp), color = colors.textMuted) }
                stores.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.profile_stores_empty), color = colors.textMuted)
                        androidx.compose.material3.TextButton(onClick = onNewStore) { Text(stringResource(R.string.profile_new_store)) }
                    }
                }
                else -> {
                    val rows = stores.chunked(3)
                    items(rows.size, key = { "stores-" + rows[it].first().id }) { index ->
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            rows[index].forEach { store -> StoreTile(store, Modifier.weight(1f)) { onOpenOwnStore(store.id) } }
                            repeat(3 - rows[index].size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        } else {
            when {
                favorites.loadingFirst -> item { Text("…", modifier = Modifier.padding(16.dp), color = colors.textMuted) }
                favorites.items.isEmpty() -> item {
                    Text(stringResource(R.string.profile_favorites_empty), modifier = Modifier.padding(16.dp), color = colors.textMuted)
                }
                else -> items(favorites.items, key = { it.productId }) { fav ->
                    FavoriteRow(fav, onOpen = { onOpenProduct(fav.productId) }, onRemove = { viewModel.unfavorite(fav.productId) })
                    HorizontalDivider(color = colors.navDivider)
                }
            }
            loadingMoreFooter(favorites.loadingMore)
        }
    }
    if (deleteFailed) Text(stringResource(com.azza.core.ui.R.string.owner_delete_failed), color = AzzaTheme.colors.error, modifier = Modifier.padding(8.dp))
    OwnerPostsBar(owner, viewModel.owner::applyDiscount, viewModel.owner::deleteSelected, viewModel.owner::stop)
    }
}

@Composable
private fun FavoriteRow(item: FavoriteItem, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = AzzaTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = item.unavailable != FavoriteUnavailable.DELETED, onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface)) {
            item.imageUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (item.unavailable) {
                FavoriteUnavailable.DELETED -> Text(stringResource(R.string.profile_fav_deleted), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                FavoriteUnavailable.UNPUBLISHED -> Text(stringResource(R.string.profile_fav_unpublished), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                FavoriteUnavailable.OUT_OF_STOCK -> Text(stringResource(R.string.profile_fav_out_of_stock), color = colors.warning, style = MaterialTheme.typography.bodySmall)
                null -> Text(item.price.format(), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.profile_fav_remove)) }
    }
}

@Composable
private fun SignedOut(onSignIn: () -> Unit, onOpenSettings: () -> Unit) {
    // Language and theme are settings too, so a guest reaches them here as well.
    Box(Modifier.fillMaxSize()) {
        IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.profile_settings))
        }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.profile_signed_out),
            textAlign = TextAlign.Center,
            color = AzzaTheme.colors.textSecondary,
        )
        Button(onClick = onSignIn) { Text(stringResource(R.string.profile_sign_in)) }
    }
    }
}

@Composable
private fun Counter(value: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
    }
}

/** One store, as a square the size of a post tile: its picture, and its name where a post shows its price. */
@Composable
private fun StoreTile(store: com.azza.core.data.ManagedStore, modifier: Modifier, onOpen: () -> Unit) {
    val colors = AzzaTheme.colors
    Box(modifier.aspectRatio(1f).background(colors.surface).clickable(onClick = onOpen)) {
        if (store.avatarUrl != null) {
            coil.compose.AsyncImage(store.avatarUrl, store.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Icon(Icons.Outlined.Storefront, null, tint = colors.textMuted, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        Text(
            store.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
