package com.azza.feature.product

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.hilt.navigation.compose.hiltViewModel
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

@Serializable data class ProductRoute(val productId: String)

fun NavGraphBuilder.productScreen(onBack: () -> Unit) {
    composable<ProductRoute> { ProductScreen(onBack = onBack) }
}

@Composable
fun ProductScreen(onBack: () -> Unit, viewModel: ProductViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = AzzaTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.product_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AzzaTheme.colors.bg),
            )
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
