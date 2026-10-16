package com.azza.feature.product

import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import com.azza.core.ui.BarcodeDialog
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.azza.core.model.ProductOption
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Share
import com.azza.core.ui.LocalWebLinks
import com.azza.core.ui.shareLink
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.azza.core.ui.ReportDialog
import com.azza.core.data.ReportTarget
import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.azza.core.common.AppError
import com.azza.core.model.OfferingKind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.azza.core.designsystem.theme.AzzaTextStyles
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.ProductDetail
import com.azza.core.ui.ErrorState
import com.azza.core.ui.LoadingState
import com.azza.core.ui.discounted
import com.azza.core.ui.format
import kotlinx.serialization.Serializable

/** [source]: where the person found it, for apps/api's view analytics (see ProductsApi.byId). */
@Serializable data class ProductRoute(val productId: String, val source: String? = null)

fun NavGraphBuilder.productScreen(
    onBack: () -> Unit,
    onRequireSignIn: () -> Unit,
    onOpenBasket: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenAppointments: () -> Unit,
    onBookingSettings: (String) -> Unit,
    onOpenTimetable: (storeId: String?) -> Unit,
) {
    composable<ProductRoute> {
        ProductScreen(
            onBack = onBack,
            onRequireSignIn = onRequireSignIn,
            onOpenBasket = onOpenBasket,
            onEdit = onEdit,
            onOpenAppointments = onOpenAppointments,
            onBookingSettings = onBookingSettings,
            onOpenTimetable = onOpenTimetable,
        )
    }
}

@Composable
fun ProductScreen(
    onBack: () -> Unit,
    onRequireSignIn: () -> Unit,
    onOpenBasket: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenAppointments: () -> Unit,
    onBookingSettings: (String) -> Unit,
    onOpenTimetable: (storeId: String?) -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val isMine by viewModel.isMine.collectAsStateWithLifecycle()
    val appeal by viewModel.appeal.collectAsStateWithLifecycle()
    val chosenOptions by viewModel.options.collectAsStateWithLifecycle()
    val quantity by viewModel.quantity.collectAsStateWithLifecycle()
    val branchStock by viewModel.branchStock.collectAsStateWithLifecycle()
    var showBarcode by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshQuietly()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val added = stringResource(R.string.product_added_to_basket)
    val viewBasket = stringResource(R.string.product_view_basket)
    val context = LocalContext.current
    var reporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProductEvent.SignInRequired -> onRequireSignIn()
                ProductEvent.AddedToBasket ->
                    if (snackbar.showSnackbar(added, actionLabel = viewBasket, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                        onOpenBasket()
                    }
                is ProductEvent.Failed -> snackbar.showSnackbar(errorText(context, event.error))
            }
        }
    }

    if (showBarcode) {
        val p = (state as? ProductUiState.Ready)?.product
        if (p != null) BarcodeDialog(p.barcode, p.title, onDismiss = { showBarcode = false })
    }
    if (reporting) {
        ReportDialog(ReportTarget.PRODUCT, onDismiss = { reporting = false }, submit = viewModel::report)
    }

    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.product_back))
                    }
                },
                actions = {
                    val ready = state as? ProductUiState.Ready
                    if (ready != null && isMine && ready.product.storeId != null && ready.product.barcode != null) {
                        IconButton(onClick = { showBarcode = true }) {
                            Icon(Icons.Outlined.QrCode2, contentDescription = stringResource(R.string.product_barcode))
                        }
                    }
                    if (ready != null && isMine && ready.product.hold == null) {
                        TextButton(onClick = { onEdit(ready.product.id) }) { Text(stringResource(R.string.product_edit)) }
                    }
                    if (ready != null && ready.product.status == "ACTIVE") {
                        val url = LocalWebLinks.current.product(ready.product.id)
                        IconButton(onClick = { shareLink(context, ready.product.title, url) }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(com.azza.core.ui.R.string.share))
                        }
                    }
                    if (ready != null && !isMine) {
                        IconButton(onClick = { scope.launch { if (viewModel.canReport()) reporting = true } }) {
                            Icon(Icons.Outlined.Flag, contentDescription = stringResource(com.azza.core.ui.R.string.report_button), tint = AzzaTheme.colors.textSecondary)
                        }
                    }
                    if (state is ProductUiState.Ready) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(if (isFavorite) R.string.product_remove_favorite else R.string.product_add_favorite),
                                tint = if (isFavorite) AzzaTheme.colors.error else AzzaTheme.colors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
        },
        bottomBar = {
            val ready = state as? ProductUiState.Ready
            // Only a physical good goes through the basket; checkout refuses a
            // bookable line (BOOKING_REQUIRED) — that is booked on a timetable.
            if (ready != null && ready.product.offeringKind == OfferingKind.GOOD && !isMine && ready.product.status == "ACTIVE") {
                val soldOut = ready.product.quantityAvailable == 0
                // Price × quantity, with the chosen options for each item (the web's "Total with options").
                val unit = ready.product.price.discounted(ready.product.discountPercentage).amountMinor +
                    ready.product.options.sumOf { o -> o.extra.amountMinor * (chosenOptions[o.id] ?: 0) }
                val total = com.azza.core.model.Money(unit * quantity, ready.product.price.currency)
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!soldOut) {
                        IconButton(onClick = { viewModel.setQuantity(quantity - 1) }, enabled = quantity > 1) {
                            Icon(Icons.Outlined.Remove, stringResource(R.string.product_quantity_less))
                        }
                        Text(quantity.toString(), style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = { viewModel.setQuantity(quantity + 1) },
                            enabled = quantity < (ready.product.quantityAvailable ?: 99).coerceAtMost(99),
                        ) { Icon(Icons.Outlined.Add, stringResource(R.string.product_quantity_more)) }
                    }
                    Button(
                        onClick = viewModel::addToBasket,
                        enabled = !adding && !soldOut,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            if (soldOut) stringResource(R.string.product_out_of_stock)
                            else stringResource(R.string.product_add_with_total, total.format()),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ProductUiState.Loading -> LoadingState()
                is ProductUiState.Failed -> ErrorState(s.error, onRetry = viewModel::load)
                is ProductUiState.Ready -> ProductBody(s.product, top = {
                    if (isMine) {
                        OwnerStatus(s.product, appeal, viewModel::appeal)
                        // A timed service's own settings (length, gaps, notice, deposit); the week is the store's timetable.
                        if (s.product.offeringKind == OfferingKind.BOOKABLE && s.product.hold == null) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onBookingSettings(s.product.id) }) { Text(stringResource(R.string.product_booking_settings)) }
                                OutlinedButton(onClick = { onOpenTimetable(s.product.storeId) }) { Text(stringResource(R.string.product_view_calendar)) }
                            }
                        }
                        if (s.product.offeringKind == OfferingKind.GOOD) {
                            LaunchedEffect(s.product.id) { viewModel.loadOwnerStock(s.product) }
                            OwnerStock(s.product, branchStock)
                        }
                    }
                }) {
                    // A bookable listing is booked on its timetable, not through the basket (BOOKING_REQUIRED);
                    // you cannot book yourself (BOOKING_SELF_NOT_ALLOWED).
                    // The seller's options, for a buyer: each one item may take up to its cap.
                    if (s.product.options.isNotEmpty() && s.product.offeringKind == OfferingKind.GOOD && !isMine && s.product.status == "ACTIVE") {
                        OptionsPicker(s.product.options, chosenOptions, viewModel::setOption)
                    }
                    if (s.product.offeringKind == OfferingKind.BOOKABLE && !isMine) {
                        BookingPanel(onRequireSignIn = onRequireSignIn, onOpenAppointments = onOpenAppointments)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductBody(product: ProductDetail, top: @Composable () -> Unit = {}, extra: @Composable () -> Unit = {}) {
    val colors = AzzaTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        top()
        val images = product.imageUrls
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surface)) {
            if (images.isNotEmpty()) {
                val pager = rememberPagerState { images.size }
                HorizontalPager(state = pager, modifier = Modifier.matchParentSize()) { page ->
                    AsyncImage(
                        model = images[page],
                        contentDescription = product.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (images.size > 1) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(images.size) { i ->
                            Box(
                                Modifier.size(6.dp).clip(CircleShape).background(
                                    if (i == pager.currentPage) colors.primary else colors.textPrimary.copy(alpha = 0.4f),
                                ),
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(product.title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    product.price.discounted(product.discountPercentage).format(),
                    style = AzzaTextStyles.productPrice,
                    color = colors.textPrimary,
                )
                if ((product.discountPercentage ?: 0.0) > 0.0) {
                    Text(
                        product.price.format(),
                        style = AzzaTextStyles.productMeta,
                        color = colors.textMuted,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            // Stock means nothing on a listing that is not on sale.
            product.quantityAvailable?.takeIf { product.status == "ACTIVE" }?.let { qty ->
                Text(
                    if (qty > 0) stringResource(R.string.product_in_stock) else stringResource(R.string.product_out_of_stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (qty > 0) colors.success else colors.error,
                )
            }
            if (!product.description.isNullOrBlank()) {
                Text(product.description!!, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Specifications(product)
        }
        extra()
    }
}

/** Outside composition (a snackbar from an event), so the core:ui sentences are read from resources here. */
private fun errorText(context: Context, error: AppError): String = when (error) {
    AppError.Network -> context.getString(com.azza.core.ui.R.string.core_ui_error_network)
    AppError.Unauthenticated -> context.getString(com.azza.core.ui.R.string.core_ui_error_signed_out)
    is AppError.Api -> when (error.code) {
        "PRODUCT_NOT_AVAILABLE" -> context.getString(R.string.product_not_available)
        else -> error.message.ifBlank { context.getString(com.azza.core.ui.R.string.core_ui_error_generic) }
    }
    is AppError.Unexpected -> context.getString(com.azza.core.ui.R.string.core_ui_error_generic)
}

@Composable
private fun OptionsPicker(options: List<ProductOption>, chosen: Map<String, Int>, onChange: (String, Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.product_options), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.product_options_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
        options.forEach { o ->
            val n = chosen[o.id] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${o.name}  +${o.extra.format()}", modifier = Modifier.weight(1f))
                if (o.maxQuantity == 1) {
                    androidx.compose.material3.Checkbox(checked = n > 0, onCheckedChange = { onChange(o.id, if (it) 1 else 0) })
                } else {
                    TextButton(onClick = { onChange(o.id, (n - 1).coerceAtLeast(0)) }, enabled = n > 0) { Text("−") }
                    Text(n.toString())
                    TextButton(onClick = { onChange(o.id, (n + 1).coerceAtMost(o.maxQuantity)) }, enabled = n < o.maxQuantity) { Text("+") }
                }
            }
        }
    }
}

/** The web's "Specifications": the category's typed details, then a legacy listing's free-form features. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Specifications(product: ProductDetail) {
    if (product.specs.isEmpty() && product.features.isEmpty()) return
    val fa = LocalConfiguration.current.locales[0].language.let { it == "fa" || it == "ckb" }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.product_specifications), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            product.specs.forEach { spec ->
                val value = spec.yes?.let { stringResource(if (it) R.string.product_yes else R.string.product_no) }
                    ?: (if (fa) spec.valueFa else spec.valueEn).let { v ->
                        // An option's label may already carry the unit ("32 GB").
                        val u = spec.unit?.takeIf { it.isNotBlank() && !v.endsWith(it, ignoreCase = true) }
                        if (u != null) "$v $u" else v
                    }
                SpecCell(if (fa) spec.labelFa else spec.labelEn, value)
            }
            product.features.forEach { (name, value) -> SpecCell(name, value) }
        }
    }
}

@Composable
private fun SpecCell(label: String, value: String) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(AzzaTheme.colors.surfaceRaised).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.textMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), fontWeight = FontWeight.SemiBold)
    }
}

/** The web's OwnerStockPanel: a personal listing's count, or each branch's for a store listing. */
@Composable
private fun OwnerStock(product: ProductDetail, branches: List<Pair<com.azza.core.data.Branch, com.azza.core.data.StockLine>>?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(AzzaTheme.colors.surfaceRaised).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.product_your_stock), style = MaterialTheme.typography.titleSmall)
        if (product.storeId == null) {
            val q = product.stockQuantity ?: 0
            val r = product.stockReserved ?: 0
            Text(stringResource(R.string.product_stock_line, q, r, (q - r).coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall)
        } else {
            when {
                branches == null -> Text("…", style = MaterialTheme.typography.bodySmall)
                branches.all { it.second.quantity == 0 } -> Text(stringResource(R.string.product_not_stocked), style = MaterialTheme.typography.bodySmall)
                else -> branches.forEach { (b, line) ->
                    Text(
                        b.name + " · " + stringResource(R.string.product_stock_line, line.quantity, line.reserved, (line.quantity - line.reserved).coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
