package com.azza.feature.profile

import androidx.compose.foundation.layout.Arrangement
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

fun NavGraphBuilder.profileScreen(onSignIn: () -> Unit, onOpenProduct: (String) -> Unit) {
    composable<ProfileRoute> { ProfileScreen(onSignIn = onSignIn, onOpenProduct = onOpenProduct) }
}

@Composable
fun ProfileScreen(onSignIn: () -> Unit, onOpenProduct: (String) -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        ProfileUiState.Loading -> LoadingState()
        ProfileUiState.SignedOut -> SignedOut(onSignIn)
        is ProfileUiState.Failed ->
            if (s.error == AppError.Unauthenticated) SignedOut(onSignIn) else ErrorState(s.error, onRetry = { viewModel.retry() })
        is ProfileUiState.Ready -> Profile(s.user, viewModel, onOpenProduct, onLogout = { viewModel.logout() })
    }
}

/** The profile header's slots in their phone order: handle, photo, name/bio, then actions (web §4); then favourites. */
@Composable
private fun Profile(user: User, viewModel: ProfileViewModel, onOpenProduct: (String) -> Unit, onLogout: () -> Unit) {
    val colors = AzzaTheme.colors
    val favorites by viewModel.favorites.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, favorites.items.size, viewModel.favorites::loadMore)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(handle(user.username), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Avatar(url = user.avatarUrl, size = 80)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    user.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_sign_out))
                }
                Text(stringResource(R.string.profile_favorites), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
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
