package com.azza.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azza.core.designsystem.theme.AzzaTextStyles
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.PostAuthor
import com.azza.core.model.ProductCard
import java.util.Locale

/**
 * The feed's post: author row, square photo, then title and price — the
 * web's ProductCardView in its phone layout (Instagram-style header).
 */
@Composable
fun ProductPostCard(
    card: ProductCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAuthor: ((PostAuthor) -> Unit)? = null,
) {
    val colors = AzzaTheme.colors
    Column(modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onOpenAuthor != null) Modifier.clickable { onOpenAuthor(card.author) } else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(url = card.author.avatarUrl, isStore = card.author.isStore, size = 32)
            Text(
                card.author.handle,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surface),
        ) {
            if (card.imageUrl != null) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                card.title,
                style = AzzaTextStyles.productTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    card.price.discounted(card.discountPercentage).format(),
                    style = AzzaTextStyles.productPrice,
                    color = colors.textPrimary,
                )
                if ((card.discountPercentage ?: 0.0) > 0.0) {
                    Text(
                        card.price.format(),
                        style = AzzaTextStyles.productMeta,
                        color = colors.textMuted,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    card.categoryName.forLanguage(Locale.getDefault().language),
                    style = AzzaTextStyles.productMeta,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Counter(Icons.Outlined.FavoriteBorder, card.favoriteCount)
                Counter(Icons.Outlined.Visibility, card.viewCount)
            }
        }
    }
}

@Composable
private fun Counter(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = AzzaTheme.colors.textMuted, modifier = Modifier.size(14.dp))
        Text(
            java.text.NumberFormat.getIntegerInstance().format(value),
            style = AzzaTextStyles.productMeta,
            color = AzzaTheme.colors.textMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun Avatar(url: String?, isStore: Boolean = false, size: Int = 40, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(AzzaTheme.colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else if (isStore) {
            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = AzzaTheme.colors.textMuted, modifier = Modifier.size((size / 2).dp))
        }
    }
}
