package com.azza.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.ProductPostCard
import kotlinx.serialization.Serializable

@Serializable data object FeedRoute

fun NavGraphBuilder.feedScreen(onOpenProduct: (String) -> Unit) {
    composable<FeedRoute> { FeedScreen(onOpenProduct = onOpenProduct) }
}

@Composable
fun FeedScreen(
    onOpenProduct: (String) -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) viewModel.loadMore() }

    when {
        state.loadingFirstPage -> LoadingState()
        state.items.isEmpty() && state.error != null ->
            ErrorState(state.error!!, onRetry = { viewModel.loadFirstPage() })
        else -> PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.loadFirstPage(isRefresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.items.isEmpty()) {
                EmptyState(stringResource(R.string.feed_empty))
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { card ->
                        ProductPostCard(card = card, onOpen = { onOpenProduct(card.id) })
                        HorizontalDivider(color = AzzaTheme.colors.navDivider)
                    }
                    if (state.loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
