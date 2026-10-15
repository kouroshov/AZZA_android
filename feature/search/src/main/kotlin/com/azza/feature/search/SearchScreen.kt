package com.azza.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import com.azza.core.model.PostAuthor
import com.azza.core.ui.ProductFeed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.azza.core.data.CursorPager
import com.azza.core.designsystem.theme.AzzaTextStyles
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Availability
import com.azza.core.model.SearchProduct
import com.azza.core.model.SearchSort
import com.azza.core.model.SearchStore
import com.azza.core.model.SearchUser
import com.azza.core.ui.Avatar
import com.azza.core.ui.EmptyState
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadMoreEffect
import com.azza.core.ui.LoadingState
import com.azza.core.ui.handle
import com.azza.core.ui.discounted
import com.azza.core.ui.format
import com.azza.core.ui.loadingMoreFooter
import kotlinx.serialization.Serializable

/** The web's /explore: a feed until something is typed, search results once it is. */
@Serializable data object SearchRoute

fun NavGraphBuilder.searchScreen(
    onOpenProduct: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
) {
    composable<SearchRoute> { SearchScreen(onOpenProduct, onOpenUser, onOpenStore, onOpenAuthor) }
}

@Composable
fun SearchScreen(
    onOpenProduct: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val active by viewModel.activeQuery.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    if (picker.open) CityPickerDialog(picker, viewModel)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear)) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (active.length < MIN_QUERY_LENGTH) {
            ExploreFeed(viewModel, onOpenProduct, onOpenAuthor)
            return@Column
        }

        PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = AzzaTheme.colors.bg) {
            SearchTab.entries.forEach { t ->
                Tab(
                    selected = t == tab,
                    onClick = { viewModel.onTab(t) },
                    text = {
                        Text(
                            stringResource(
                                when (t) {
                                    SearchTab.PRODUCTS -> R.string.search_tab_products
                                    SearchTab.USERS -> R.string.search_tab_users
                                    SearchTab.STORES -> R.string.search_tab_stores
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (tab == SearchTab.PRODUCTS) SortChips(sort, viewModel::onSort)
        when (tab) {
            SearchTab.PRODUCTS -> Results(viewModel.products, { it.id }) { ProductRow(it) { onOpenProduct(it.id) } }
            SearchTab.USERS -> Results(viewModel.users, { it.id }) { UserRow(it) { onOpenUser(it.username) } }
            SearchTab.STORES -> Results(viewModel.stores, { it.id }) { StoreRow(it) { onOpenStore(it.slug) } }
        }
    }
}

@Composable
private fun ExploreFeed(viewModel: SearchViewModel, onOpenProduct: (String) -> Unit, onOpenAuthor: (PostAuthor) -> Unit) {
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val city by viewModel.city.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = scope == ExploreScope.GLOBAL,
            onClick = { viewModel.onScope(ExploreScope.GLOBAL) },
            label = { Text(stringResource(R.string.explore_global)) },
        )
        FilterChip(
            selected = scope == ExploreScope.CITY,
            onClick = { viewModel.onScope(ExploreScope.CITY) },
            label = { Text(city?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.explore_city)) },
        )
        if (scope == ExploreScope.CITY && city != null) {
            TextButton(onClick = viewModel::openPicker) { Text(stringResource(R.string.explore_change_city)) }
        }
    }
    when {
        scope == ExploreScope.GLOBAL -> ProductFeed(
            pager = viewModel.globalFeed,
            emptyText = stringResource(R.string.explore_empty_global),
            onOpenProduct = onOpenProduct,
            onOpenAuthor = onOpenAuthor,
        )
        city == null -> EmptyState(stringResource(R.string.explore_no_city))
        else -> ProductFeed(
            pager = viewModel.cityFeed,
            emptyText = stringResource(R.string.explore_empty_city),
            onOpenProduct = onOpenProduct,
            onOpenAuthor = onOpenAuthor,
        )
    }
}

@Composable
private fun CityPickerDialog(state: CityPickerState, viewModel: SearchViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closePicker,
        title = { Text(stringResource(if (state.province == null) R.string.explore_select_province else R.string.explore_select_city)) },
        text = {
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val areas = if (state.province == null) state.provinces else state.cities
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(areas, key = { it.id }) { area ->
                        Text(
                            area.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (state.province == null) viewModel.pickProvince(area) else viewModel.pickCity(area) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (state.province != null) viewModel.backToProvinces() else viewModel.closePicker() }) {
                Text(stringResource(if (state.province != null) R.string.explore_back else R.string.explore_cancel))
            }
        },
    )
}

@Composable
private fun SortChips(selected: SearchSort, onSelect: (SearchSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchSort.entries.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSelect(sort) },
                label = {
                    Text(
                        stringResource(
                            when (sort) {
                                SearchSort.RELEVANCE -> R.string.search_sort_relevance
                                SearchSort.NEWEST -> R.string.search_sort_newest
                                SearchSort.PRICE_ASC -> R.string.search_sort_price_asc
                                SearchSort.PRICE_DESC -> R.string.search_sort_price_desc
                                SearchSort.POPULAR -> R.string.search_sort_popular
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun <T> Results(pager: CursorPager<T>, key: (T) -> String, row: @Composable (T) -> Unit) {
    val state by pager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, pager::loadMore)
    when {
        state.loadingFirst -> LoadingState()
        state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { pager.refresh() })
        state.items.isEmpty() -> EmptyState(stringResource(R.string.search_no_results))
        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(state.items, key = key) { item ->
                row(item)
                HorizontalDivider(color = AzzaTheme.colors.navDivider)
            }
            loadingMoreFooter(state.loadingMore)
        }
    }
}

@Composable
private fun ProductRow(product: SearchProduct, onOpen: () -> Unit) {
    val colors = AzzaTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface)) {
            product.imageUrl?.let { AsyncImage(it, product.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.title, style = AzzaTextStyles.productTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(product.price.discounted(product.discountPercentage).format(), style = MaterialTheme.typography.titleSmall)
                if ((product.discountPercentage ?: 0.0) > 0) {
                    Text(product.price.format(), color = colors.textMuted, style = AzzaTextStyles.productMeta, textDecoration = TextDecoration.LineThrough)
                }
            }
            Text(
                listOfNotNull(product.storeName ?: product.sellerName, product.cityName, product.categoryName.ifBlank { null }).joinToString(" · "),
                color = colors.textMuted,
                style = AzzaTextStyles.productMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (product.availability) {
                Availability.OUT_OF_STOCK -> Text(stringResource(R.string.search_out_of_stock), color = colors.error, style = AzzaTextStyles.productMeta)
                Availability.LOW_STOCK -> Text(stringResource(R.string.search_low_stock), color = colors.warning, style = AzzaTextStyles.productMeta)
                else -> Unit
            }
        }
    }
}

@Composable
private fun UserRow(user: SearchUser, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(url = user.avatarUrl, size = 48)
        Column(Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
            Text(handle(user.username), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.search_followers, user.followerCount), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StoreRow(store: SearchStore, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(url = store.avatarUrl, isStore = true, size = 48)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(store.name, style = MaterialTheme.typography.titleSmall)
                if (store.verified) Icon(Icons.Outlined.Verified, stringResource(R.string.search_verified), tint = AzzaTheme.colors.primary, modifier = Modifier.size(16.dp))
            }
            Text(stringResource(R.string.search_branches, store.branchCount), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.search_followers, store.followerCount), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}
