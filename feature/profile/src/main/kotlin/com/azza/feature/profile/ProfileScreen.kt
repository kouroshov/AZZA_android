package com.azza.feature.profile

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
) {
    composable<ProfileRoute> { ProfileScreen(onSignIn, onOpenProduct, onEditProfile, onOpenFollowList, onOpenStores) }
    composable<FollowListRoute> { FollowListScreen(onBack, onOpenUser, onOpenStore) }
}

@Composable
fun ProfileScreen(
    onSignIn: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (followers: Boolean) -> Unit,
    onOpenStores: () -> Unit,
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
        ProfileUiState.SignedOut -> SignedOut(onSignIn)
        is ProfileUiState.Failed ->
            if (s.error == AppError.Unauthenticated) SignedOut(onSignIn) else ErrorState(s.error, onRetry = { viewModel.retry() })
        is ProfileUiState.Ready -> {
            val me by viewModel.me.collectAsStateWithLifecycle()
            Profile(me ?: s.user, viewModel, onOpenProduct, onEditProfile, onOpenFollowList, onOpenStores)
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
    onOpenStores: () -> Unit,
) {
    val followers by viewModel.followerCount.collectAsStateWithLifecycle()
    val following by viewModel.followingCount.collectAsStateWithLifecycle()
    val colors = AzzaTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favorites by viewModel.favorites.state.collectAsStateWithLifecycle()
    val posts by viewModel.posts.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, if (tab == 0) posts.items.size else favorites.items.size) {
        if (tab == 0) viewModel.posts.loadMore() else viewModel.favorites.loadMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(handle(user.username), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                // Photo with the counters beside it (the web's phone header, §4).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Avatar(url = user.avatarUrl, size = 80)
                    Counter(followers?.toString() ?: "–", stringResource(R.string.follow_list_followers)) { onOpenFollowList(true) }
                    Counter(following.toString(), stringResource(R.string.follow_list_following)) { onOpenFollowList(false) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    user.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                // Signing out lives in Settings (the ⋮ menu), as on the web.
                // The web's actions slot: Edit profile, and the stores you run.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditProfile, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.profile_edit)) }
                    OutlinedButton(onClick = onOpenStores, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.profile_stores)) }
                }
            }
        }
        item(key = "tabs") {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = colors.bg) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.profile_tab_posts)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.profile_favorites)) })
            }
        }
        if (tab == 0) {
            when {
                posts.loadingFirst -> item { Text("…", modifier = Modifier.padding(16.dp), color = colors.textMuted) }
                posts.items.isEmpty() -> item {
                    Text(stringResource(R.string.profile_no_posts), modifier = Modifier.padding(16.dp), color = colors.textMuted)
                }
                else -> postGrid(posts.items, onOpenProduct)
            }
            loadingMoreFooter(posts.loadingMore)
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
private fun SignedOut(onSignIn: () -> Unit) {
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
