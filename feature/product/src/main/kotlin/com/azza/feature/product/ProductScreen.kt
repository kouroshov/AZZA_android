package com.azza.feature.product

import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
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

fun NavGraphBuilder.productScreen(onBack: () -> Unit, onRequireSignIn: () -> Unit, onOpenBasket: () -> Unit) {
    composable<ProductRoute> { ProductScreen(onBack = onBack, onRequireSignIn = onRequireSignIn, onOpenBasket = onOpenBasket) }
}

@Composable
fun ProductScreen(
    onBack: () -> Unit,
    onRequireSignIn: () -> Unit,
    onOpenBasket: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val added = stringResource(R.string.product_added_to_basket)
    val viewBasket = stringResource(R.string.product_view_basket)
    val context = LocalContext.current

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
            if (ready != null && ready.product.offeringKind == OfferingKind.GOOD) {
                val soldOut = ready.product.quantityAvailable == 0
                Button(
                    onClick = viewModel::addToBasket,
                    enabled = !adding && !soldOut,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(48.dp),
                ) {
                    Text(stringResource(if (soldOut) R.string.product_out_of_stock else R.string.product_add_to_cart))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ProductUiState.Loading -> LoadingState()
                is ProductUiState.Failed -> ErrorState(s.error, onRetry = viewModel::load)
                is ProductUiState.Ready -> ProductBody(s.product)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductBody(product: ProductDetail) {
    val colors = AzzaTheme.colors
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
            product.quantityAvailable?.let { qty ->
                Text(
                    if (qty > 0) stringResource(R.string.product_in_stock) else stringResource(R.string.product_out_of_stock),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (qty > 0) colors.success else colors.error,
                )
            }
            if (!product.description.isNullOrBlank()) {
                Text(product.description!!, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
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
