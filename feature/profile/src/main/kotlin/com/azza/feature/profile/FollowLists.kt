package com.azza.feature.profile

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import com.azza.core.data.FollowRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.data.CursorPager
import com.azza.core.data.FollowEntry
import com.azza.core.data.FollowListsRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.Avatar
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.handle
import com.azza.core.ui.loadingMoreFooter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.Serializable

/** Your own followers, or whom you follow (apps/api keeps everyone else's private). */
@Serializable data class FollowListRoute(val followers: Boolean)

@HiltViewModel
class FollowListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: FollowListsRepository,
    private val follow: FollowRepository,
) : ViewModel() {
    /** Whom I follow now, so each row's button says Follow or Following (the web's lists have the button too). */
    val followedUsers: StateFlow<Set<String>> = follow.followedUsers
    val followedStores: StateFlow<Set<String>> = follow.followedStores

    fun toggle(entry: FollowEntry) = viewModelScope.launch {
        if (entry.isStore) follow.setFollowingStore(entry.id, entry.id !in followedStores.value)
        else follow.setFollowingUser(entry.id, entry.id !in followedUsers.value)
    }

    val followers = savedStateHandle.toRoute<FollowListRoute>().followers
    val pager = CursorPager<FollowEntry>(viewModelScope, { it.id }) { cursor ->
        if (followers) repository.followers(cursor) else repository.following(cursor)
    }

    init {
        pager.refresh()
    }
}

@Composable
fun FollowListScreen(
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    viewModel: FollowListViewModel = hiltViewModel(),
) {
    val state by viewModel.pager.state.collectAsStateWithLifecycle()
    val users by viewModel.followedUsers.collectAsStateWithLifecycle()
    val stores by viewModel.followedStores.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, viewModel.pager::loadMore)
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.followers) R.string.follow_list_followers else R.string.follow_list_following), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.profile_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loadingFirst -> LoadingState()
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { viewModel.pager.refresh() })
                state.items.isEmpty() ->
                    EmptyState(stringResource(if (viewModel.followers) R.string.follow_list_followers_empty else R.string.follow_list_following_empty))
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { entry ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (entry.isStore) onOpenStore(entry.slug ?: entry.id) else entry.username?.let(onOpenUser) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The lists carry no photo: the placeholder (a store's shopfront for a store).
                            Avatar(url = null, isStore = entry.isStore, size = 40)
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    handle(entry.username ?: entry.slug.orEmpty()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AzzaTheme.colors.textMuted,
                                )
                            }
                            val following = entry.id in (if (entry.isStore) stores else users)
                            if (following) {
                                OutlinedButton(onClick = { viewModel.toggle(entry) }) { Text(stringResource(R.string.profile_following)) }
                            } else {
                                Button(onClick = { viewModel.toggle(entry) }) { Text(stringResource(R.string.profile_follow)) }
                            }
                        }
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                    loadingMoreFooter(state.loadingMore)
                }
            }
        }
    }
}
