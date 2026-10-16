@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.azza.feature.search

import com.azza.core.model.SearchFilters
import com.azza.core.model.ItemCondition
import com.azza.core.model.Area
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.InputChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.QrCodeScanner
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
    onScan: () -> Unit,
) {
    composable<SearchRoute> { SearchScreen(onOpenProduct, onOpenUser, onOpenStore, onOpenAuthor, onScan) }
}

@Composable
fun SearchScreen(
    onOpenProduct: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenStore: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
    onScan: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val active by viewModel.activeQuery.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    if (picker.open) CityPickerDialog(picker, viewModel)
    if (categories.open) CategoryBrowserDialog(categories, viewModel)
    if (filtersOpen) FiltersSheet(filters, viewModel, onDismiss = { filtersOpen = false })

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear)) }
                } else {
                    // A listing's label (Android only): the camera, or its 13 digits typed.
                    IconButton(onClick = onScan) { Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.search_scan)) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        val searching = active.length >= MIN_QUERY_LENGTH
        if (!searching && filters.isEmpty) {
            ExploreFeed(viewModel, onOpenProduct, onOpenAuthor, onOpenFilters = { filtersOpen = true })
            return@Column
        }

        // Filters without a query are the marketplace: products only.
        if (searching) {
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
        }
        val shown = if (searching) tab else SearchTab.PRODUCTS
        if (shown == SearchTab.PRODUCTS) {
            FilterBar(filters, viewModel, onOpenFilters = { filtersOpen = true })
            SortChips(sort, viewModel::onSort)
        }
        when (shown) {
            SearchTab.PRODUCTS -> Results(viewModel.products, { it.id }, onClearFilters = viewModel::clearFilters.takeIf { !filters.isEmpty }) {
                ProductRow(it) { onOpenProduct(it.id) }
            }
            SearchTab.USERS -> Results(viewModel.users, { it.id }) { UserRow(it) { onOpenUser(it.username) } }
            SearchTab.STORES -> Results(viewModel.stores, { it.id }) { StoreRow(it) { onOpenStore(it.slug) } }
        }
    }
}

/** "Filters (n)", the chosen ones as removable chips, and the result count. */
@Composable
private fun FilterBar(filters: SearchFilters, viewModel: SearchViewModel, onOpenFilters: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val state by viewModel.products.state.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onOpenFilters,
            label = { Text(if (filters.isEmpty) stringResource(R.string.filters) else stringResource(R.string.filters_count, filters.count)) },
            leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
        )
        filters.categoryName?.let { name ->
            InputChip(selected = true, onClick = { viewModel.chooseCategory(null, null) }, label = { Text(name.forLanguage(language)) }, trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.filters_remove), Modifier.size(16.dp)) })
        }
        filters.condition?.let { c ->
            InputChip(selected = true, onClick = { viewModel.setCondition(null) }, label = { Text(stringResource(c.label())) }, trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.filters_remove), Modifier.size(16.dp)) })
        }
        filters.availability?.let { a ->
            InputChip(selected = true, onClick = { viewModel.setAvailability(null) }, label = { Text(stringResource(a.label())) }, trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.filters_remove), Modifier.size(16.dp)) })
        }
        if (filters.priceMin.isNotBlank() || filters.priceMax.isNotBlank()) {
            InputChip(
                selected = true,
                onClick = { viewModel.applyFilters(filters.copy(priceMin = "", priceMax = "")) },
                label = { Text(stringResource(R.string.filters_price_chip, filters.priceMin.ifBlank { "0" }, filters.priceMax.ifBlank { "∞" })) },
                trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.filters_remove), Modifier.size(16.dp)) },
            )
        }
        if (!filters.isEmpty) TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.filters_clear)) }
        state.total?.takeIf { !state.loadingFirst }?.let {
            Text(pluralStringResource(R.plurals.search_result_count, it, it), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The web's Filters panel: category, condition, availability, price range. */
@Composable
private fun FiltersSheet(current: SearchFilters, viewModel: SearchViewModel, onDismiss: () -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    var condition by remember { mutableStateOf(current.condition) }
    var availability by remember { mutableStateOf(current.availability) }
    var priceMin by remember { mutableStateOf(current.priceMin) }
    var priceMax by remember { mutableStateOf(current.priceMax) }
    val digits = { s: String -> s.filter { it.isDigit() || it == '.' } }
    val badRange = priceMin.toDoubleOrNull() != null && priceMax.toDoubleOrNull() != null && priceMin.toDouble() > priceMax.toDouble()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleMedium)

            Text(stringResource(R.string.filters_category), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { onDismiss(); viewModel.openCategories() }, modifier = Modifier.fillMaxWidth()) {
                Text(current.categoryName?.forLanguage(language) ?: stringResource(R.string.filters_all_categories), modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null)
            }

            Text(stringResource(R.string.filters_condition), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = condition == null, onClick = { condition = null }, label = { Text(stringResource(R.string.filters_any)) })
                ItemCondition.entries.forEach { c ->
                    FilterChip(selected = condition == c, onClick = { condition = c }, label = { Text(stringResource(c.label())) })
                }
            }

            Text(stringResource(R.string.filters_availability), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = availability == null, onClick = { availability = null }, label = { Text(stringResource(R.string.filters_any)) })
                listOf(Availability.IN_STOCK, Availability.LOW_STOCK, Availability.OUT_OF_STOCK).forEach { a ->
                    FilterChip(selected = availability == a, onClick = { availability = a }, label = { Text(stringResource(a.label())) })
                }
            }

            Text(stringResource(R.string.filters_price), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceMin,
                    onValueChange = { priceMin = digits(it) },
                    label = { Text(stringResource(R.string.filters_price_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = priceMax,
                    onValueChange = { priceMax = digits(it) },
                    label = { Text(stringResource(R.string.filters_price_max)) },
                    singleLine = true,
                    isError = badRange,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            if (badRange) Text(stringResource(R.string.filters_price_invalid), color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.clearFilters(); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filters_clear)) }
                Button(
                    enabled = !badRange,
                    onClick = {
                        viewModel.applyFilters(current.copy(condition = condition, availability = availability, priceMin = priceMin.trim(), priceMax = priceMax.trim()))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filters_apply)) }
            }
        }
    }
}

/** Browse the category tree (every level can be chosen) or find one by name. */
@Composable
private fun CategoryBrowserDialog(state: CategoryBrowserState, viewModel: SearchViewModel) {
    val language = LocalConfiguration.current.locales[0].language
    AlertDialog(
        onDismissRequest = viewModel::closeCategories,
        title = { Text(stringResource(R.string.filters_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::searchCategories,
                    placeholder = { Text(stringResource(R.string.filters_search_categories)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.query.trim().length >= 2 -> LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(state.hits, key = { it.id }) { hit ->
                            Column(Modifier.fillMaxWidth().clickable { viewModel.chooseCategory(hit.id, hit.name) }.padding(vertical = 10.dp)) {
                                Text(hit.name.forLanguage(language))
                                if (hit.path.isNotEmpty()) {
                                    Text(hit.path.joinToString(" › ") { it.forLanguage(language) }, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                                }
                            }
                        }
                    }
                    else -> {
                        val here = state.trail.lastOrNull()
                        val nodes = here?.children ?: state.roots
                        if (here != null) {
                            TextButton(onClick = viewModel::categoryUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                Text(state.trail.joinToString(" › ") { it.name.forLanguage(language) })
                            }
                        }
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            item {
                                Text(
                                    if (here == null) stringResource(R.string.filters_all_categories) else stringResource(R.string.filters_all_in, here.name.forLanguage(language)),
                                    color = AzzaTheme.colors.primary,
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.chooseCategory(here?.id, here?.name) }.padding(vertical = 12.dp),
                                )
                            }
                            items(nodes, key = { it.id }) { node ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        if (node.children.isEmpty()) viewModel.chooseCategory(node.id, node.name) else viewModel.openCategory(node)
                                    }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(node.name.forLanguage(language), modifier = Modifier.weight(1f))
                                    if (node.children.isNotEmpty()) Icon(Icons.Outlined.ChevronRight, null, tint = AzzaTheme.colors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::closeCategories) { Text(stringResource(R.string.explore_cancel)) } },
    )
}

@Composable
private fun ExploreFeed(
    viewModel: SearchViewModel,
    onOpenProduct: (String) -> Unit,
    onOpenAuthor: (PostAuthor) -> Unit,
    onOpenFilters: () -> Unit,
) {
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = scope == ExploreScope.GLOBAL,
            onClick = { viewModel.onScope(ExploreScope.GLOBAL) },
            label = { Text(stringResource(R.string.explore_global)) },
            leadingIcon = { Icon(Icons.Outlined.Public, null, Modifier.size(18.dp)) },
        )
        // The city chip IS the picker (the web's ExploreCitySwitcher): one control, one job.
        FilterChip(
            selected = scope == ExploreScope.CITY,
            onClick = { if (scope == ExploreScope.CITY || cities.isEmpty()) viewModel.openPicker() else viewModel.onScope(ExploreScope.CITY) },
            label = {
                Text(
                    when {
                        cities.isEmpty() -> stringResource(R.string.explore_city)
                        cities.size == 1 -> cities[0].name
                        else -> stringResource(R.string.explore_cities_more, cities[0].name, cities.size - 1)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Outlined.Place, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = viewModel::openCategories,
            label = { Text(stringResource(R.string.explore_categories)) },
            leadingIcon = { Icon(Icons.Outlined.Category, null, Modifier.size(18.dp)) },
        )
        AssistChip(
            onClick = onOpenFilters,
            label = { Text(stringResource(R.string.filters)) },
            leadingIcon = { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) },
        )
    }
    when {
        scope == ExploreScope.GLOBAL -> ProductFeed(
            pager = viewModel.globalFeed,
            emptyText = stringResource(R.string.explore_empty_global),
            onOpenProduct = onOpenProduct,
            onOpenAuthor = onOpenAuthor,
        )
        cities.isEmpty() -> EmptyState(stringResource(R.string.explore_no_city))
        else -> ProductFeed(
            pager = viewModel.cityFeed,
            emptyText = stringResource(R.string.explore_empty_city),
            onOpenProduct = onOpenProduct,
            onOpenAuthor = onOpenAuthor,
        )
    }
}

/**
 * The web's CitySelector: what is staged (removable), All Iran, previous
 * selections, a search by name, and provinces → cities, each city a
 * checkbox. Nothing changes until Confirm.
 */
@Composable
private fun CityPickerDialog(state: CityPickerState, viewModel: SearchViewModel) {
    val allIran = Area(ALL_IRAN_ID, stringResource(R.string.explore_all_iran))
    val staged = state.staged.map { if (it.id == ALL_IRAN_ID) allIran else it }
    fun picked(id: String) = state.staged.any { it.id == id }
    AlertDialog(
        onDismissRequest = viewModel::closePicker,
        title = { Text(stringResource(if (state.province == null) R.string.explore_choose_cities else R.string.explore_select_city)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (staged.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        staged.forEach { area ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.toggleCity(area) },
                                label = { Text(area.name) },
                                trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.filters_remove), Modifier.size(16.dp)) },
                            )
                        }
                        TextButton(onClick = viewModel::clearStaged) { Text(stringResource(R.string.explore_clear_all)) }
                    }
                }
                if (state.province == null) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::searchPlaces,
                        placeholder = { Text(stringResource(R.string.explore_search_city)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.province == null && state.query.trim().length >= 2 -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        if (state.hits.isEmpty()) item { Text(stringResource(R.string.explore_no_places), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(vertical = 12.dp)) }
                        items(state.hits, key = { it.area.id }) { hit ->
                            if (hit.isProvince) {
                                PlaceRow(hit.area.name, stringResource(R.string.explore_province), picked = null) { viewModel.openProvince(hit.area) }
                            } else {
                                PlaceRow(hit.area.name, hit.provinceName, picked = picked(hit.area.id)) { viewModel.toggleCity(hit.area) }
                            }
                        }
                    }
                    state.province == null -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        item { PlaceRow(allIran.name, null, picked = picked(ALL_IRAN_ID)) { viewModel.toggleCity(allIran) } }
                        val recent = state.recent.filter { r -> r.id != ALL_IRAN_ID }
                        if (recent.isNotEmpty()) {
                            item { SectionLabel(stringResource(R.string.explore_previous)) }
                            items(recent, key = { "recent-" + it.id }) { area ->
                                PlaceRow(area.name, null, picked = picked(area.id)) { viewModel.toggleCity(area) }
                            }
                        }
                        item { SectionLabel(stringResource(R.string.explore_select_province)) }
                        items(state.provinces, key = { it.id }) { province ->
                            PlaceRow(province.name, null, picked = null) { viewModel.openProvince(province) }
                        }
                    }
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        item {
                            TextButton(onClick = viewModel::backToProvinces) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                Text(state.province.name)
                            }
                        }
                        items(state.cities, key = { it.id }) { city ->
                            PlaceRow(city.name, null, picked = picked(city.id)) { viewModel.toggleCity(city) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.confirmCities() }) { Text(stringResource(R.string.explore_confirm)) } },
        dismissButton = { TextButton(onClick = viewModel::closePicker) { Text(stringResource(R.string.explore_cancel)) } },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** One place: a checkbox when it can be picked ([picked] non-null), a chevron when it opens. */
@Composable
private fun PlaceRow(name: String, detail: String?, picked: Boolean?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted) }
        }
        if (picked != null) Checkbox(checked = picked, onCheckedChange = { onClick() }) else Icon(Icons.Outlined.ChevronRight, null, tint = AzzaTheme.colors.textMuted)
    }
}

private fun ItemCondition.label(): Int = when (this) {
    ItemCondition.NEW -> R.string.condition_new
    ItemCondition.LIKE_NEW -> R.string.condition_like_new
    ItemCondition.USED -> R.string.condition_used
    ItemCondition.FOR_PARTS -> R.string.condition_for_parts
}

private fun Availability.label(): Int = when (this) {
    Availability.IN_STOCK -> R.string.availability_in_stock
    Availability.LOW_STOCK -> R.string.search_low_stock
    Availability.OUT_OF_STOCK -> R.string.search_out_of_stock
    Availability.UNKNOWN -> R.string.filters_any
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
private fun <T> Results(pager: CursorPager<T>, key: (T) -> String, onClearFilters: (() -> Unit)? = null, row: @Composable (T) -> Unit) {
    val state by pager.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, state.items.size, pager::loadMore)
    when {
        state.loadingFirst -> LoadingState()
        state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { pager.refresh() })
        state.items.isEmpty() && onClearFilters != null -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.filters_no_results), color = AzzaTheme.colors.textMuted)
            TextButton(onClick = onClearFilters) { Text(stringResource(R.string.filters_clear)) }
        }
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(handle(user.username), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                if (user.isSeller) {
                    Text(
                        stringResource(R.string.search_seller),
                        color = AzzaTheme.colors.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AzzaTheme.colors.surface).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
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
