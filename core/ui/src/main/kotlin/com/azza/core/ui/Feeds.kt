@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.azza.core.ui

import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.azza.core.data.CursorPager
import com.azza.core.designsystem.theme.AzzaTextStyles
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.PostAuthor
import com.azza.core.model.PostTile
import com.azza.core.model.ProductCard

/**
 * A feed of posts (explore, city, following): first-page states, pull to
 * refresh, paging at the end. [header] scrolls with the list.
 */
@Composable
fun ProductFeed(
    pager: CursorPager<ProductCard>,
    emptyText: String,
    onOpenProduct: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
    modifier: Modifier = Modifier,
    emptyAction: (@Composable () -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val state by pager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, pager::loadMore)

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { pager.refresh(showSpinner = true) },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            header?.invoke(this)
            offlineNote(state.savedAt)
            when {
                state.loadingFirst -> item { Box(Modifier.fillMaxWidth().padding(48.dp)) { LoadingState() } }
                state.error != null && state.items.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp)) { ErrorState(state.error!!, onRetry = { pager.refresh() }) }
                }
                state.items.isEmpty() -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(emptyText, color = AzzaTheme.colors.textMuted)
                        emptyAction?.invoke()
                    }
                }
                else -> items(state.items, key = { it.id }) { card ->
                    ProductPostCard(card = card, onOpen = { onOpenProduct(card.id) }, onOpenAuthor = onOpenAuthor)
                    HorizontalDivider(color = AzzaTheme.colors.navDivider)
                }
            }
            loadingMoreFooter(state.loadingMore)
        }
    }
}

/**
 * A profile's posts as a three-column grid of squares — the web's profile
 * layout on a phone (CLAUDE.md §4, `contactSheet`). Rows of three inside a
 * LazyColumn, so it can sit under a profile header in one scroll.
 */
fun LazyListScope.postGrid(
    tiles: List<PostTile>,
    onOpen: (String) -> Unit,
    /** The owner's grid: a long press opens the post's menu. */
    onLongPress: ((PostTile) -> Unit)? = null,
    /** Non-null while selecting: a tap toggles, and the chosen ones are marked. */
    selected: Set<String>? = null,
    onToggle: (String) -> Unit = {},
) {
    val rows = tiles.chunked(3)
    items(rows.size, key = { "row-" + rows[it].first().id }) { index ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            rows[index].forEach { tile ->
                PostSquare(
                    tile,
                    Modifier.weight(1f),
                    selected = selected?.let { tile.id in it },
                    onLongPress = onLongPress?.let { { it(tile) } },
                ) { if (selected != null) onToggle(tile.id) else onOpen(tile.id) }
            }
            repeat(3 - rows[index].size) { Box(Modifier.weight(1f)) }
        }
        Box(Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun PostSquare(tile: PostTile, modifier: Modifier, selected: Boolean? = null, onLongPress: (() -> Unit)? = null, onOpen: () -> Unit) {
    Box(modifier.aspectRatio(1f).background(AzzaTheme.colors.surface).combinedClickable(onClick = onOpen, onLongClick = onLongPress)) {
        if (tile.imageUrl != null) {
            AsyncImage(tile.imageUrl, tile.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(
                tile.title,
                style = AzzaTextStyles.productMeta,
                color = AzzaTheme.colors.textSecondary,
                maxLines = 3,
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            tile.price.priceLabel(tile.discountPercentage, tile.priceNegotiable),
            style = AzzaTextStyles.productMeta,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (tile.status != "ACTIVE") {
            Text(
                stringResource(
                    when (tile.status) {
                        "DRAFT" -> R.string.tile_draft
                        "SOLD" -> R.string.tile_sold
                        "REJECTED" -> R.string.tile_taken_down
                        else -> R.string.tile_not_live
                    },
                ),
                style = AzzaTextStyles.productMeta,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(if (tile.status == "REJECTED") AzzaTheme.colors.error else Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (selected != null) {
            Box(Modifier.matchParentSize().background(if (selected) AzzaTheme.colors.primary.copy(alpha = 0.35f) else Color.Transparent))
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
    }
}
