package com.azza.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.ProductDetail
import com.azza.core.ui.priceLabel

/** ✅ (2026-10-23) «آگهی‌های مشابه» under a listing (Divar, leboncoin): same category, a similar price. */
@Composable
internal fun SimilarListings(items: List<ProductDetail>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) return
    val colors = AzzaTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.product_similar),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                Column(
                    Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(item.id) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(colors.surface)) {
                        item.imageUrls.firstOrNull()?.let {
                            AsyncImage(it, item.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        }
                    }
                    Text(item.title, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.price.priceLabel(item.discountPercentage, item.priceNegotiable),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}
