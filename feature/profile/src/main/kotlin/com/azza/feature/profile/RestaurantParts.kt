package com.azza.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.MenuDish
import com.azza.core.model.RestaurantInfo
import com.azza.core.model.StoreMenu
import com.azza.core.ui.discounted
import com.azza.core.ui.format
import com.azza.core.ui.priceLabel
import java.text.NumberFormat
import java.util.Locale

// ✅ (2026-10-23, Phase 4 — restaurants). A restaurant's page as Snappfood
// and Uber Eats lay one out, the web's RestaurantStrip + RestaurantMenu: a
// strip with whether it takes an order now, its score, the delivery fee,
// the minimum order and the delivery time; then the menu — a row of
// headings to jump between and, under each, its dishes with a photo, a line
// of description, the price and an "add" button. A dish with extras opens
// its own page, where they are chosen. While the restaurant is paused or
// closed the menu stays readable, but nothing can be added (checkout would
// refuse it: STORE_CLOSED).

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestaurantStrip(info: RestaurantInfo, ratingAverage: Double?, ratingCount: Int) {
    val numbers = NumberFormat.getInstance(Locale.getDefault())
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AzzaTheme.colors.surface).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val (label, color) = when (info.ordering) {
                "OPEN" -> stringResource(R.string.restaurant_open) to AzzaTheme.colors.success
                "PAUSED" -> stringResource(R.string.restaurant_paused) to AzzaTheme.colors.warning
                else -> stringResource(R.string.restaurant_closed) to AzzaTheme.colors.error
            }
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            if (ratingAverage != null) {
                Fact(Icons.Outlined.Star, "${numbers.format(ratingAverage)} (${numbers.format(ratingCount)})")
            }
            Fact(
                Icons.Outlined.DeliveryDining,
                if (info.deliveryFee.amountMinor == 0L) stringResource(R.string.restaurant_free_delivery) else info.deliveryFee.format(),
            )
            if (info.minOrder.amountMinor > 0) {
                Fact(Icons.Outlined.ShoppingBag, stringResource(R.string.restaurant_min_order_short, info.minOrder.format()))
            }
            info.deliveryMinutes?.let { range ->
                Fact(Icons.Outlined.Schedule, stringResource(R.string.restaurant_delivery_minutes, numbers.format(range.first), numbers.format(range.last)))
            }
        }
        if (!info.open) {
            Text(
                stringResource(if (info.ordering == "PAUSED") R.string.restaurant_paused_notice else R.string.restaurant_closed_notice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AzzaTheme.colors.surface).padding(12.dp),
            )
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = AzzaTheme.colors.textSecondary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textSecondary)
    }
}

/**
 * The menu as list items: a row of headings (a tap scrolls to one), then
 * each heading with its dishes, then the dishes under none. [headingIndex]
 * is told where each heading's item lands, so the row can scroll there.
 */
internal fun LazyListScope.restaurantMenu(
    menu: StoreMenu,
    canOrder: Boolean,
    inBasket: Map<String, Int>,
    otherDishesTitle: String,
    onJump: (Int) -> Unit,
    onAdd: (MenuDish) -> Unit,
    onOpen: (String) -> Unit,
    firstIndex: Int,
) {
    val groups = menu.sections.filter { it.items.isNotEmpty() }.map { it.name to it.items } +
        listOfNotNull((if (menu.sections.isEmpty()) "" else otherDishesTitle).let { t -> menu.unsectioned.takeIf { it.isNotEmpty() }?.let { t to it } })
    if (groups.isEmpty()) {
        item(key = "menu-empty") { Text(stringResource(R.string.profile_no_posts), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(16.dp)) }
        return
    }
    // Where each heading lands: after the chips row, each group is a heading item plus its dishes.
    var at = firstIndex + 1
    val starts = groups.map { (_, items) -> at.also { at += 1 + items.size } }
    if (groups.size > 1) {
        item(key = "menu-chips") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEachIndexed { i, (name, _) -> AssistChip(onClick = { onJump(starts[i]) }, label = { Text(name) }) }
            }
        }
    } else {
        item(key = "menu-chips") { Box(Modifier) }
    }
    groups.forEachIndexed { i, (name, dishes) ->
        item(key = "menu-h-$i") {
            if (name.isNotEmpty()) Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
            else Box(Modifier)
        }
        items(dishes, key = { "menu-d-${it.id}" }) { dish ->
            DishRow(dish, canOrder, inBasket[dish.id] ?: 0, { onAdd(dish) }, { onOpen(dish.id) })
            HorizontalDivider(color = AzzaTheme.colors.navDivider, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun DishRow(dish: MenuDish, canOrder: Boolean, count: Int, onAdd: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(AzzaTheme.colors.surface)) {
            if (dish.imageUrl != null) AsyncImage(dish.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                dish.title,
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            dish.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    color = AzzaTheme.colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dish.price.priceLabel(dish.discountPercentage, dish.priceNegotiable), style = MaterialTheme.typography.labelLarge)
                if (!dish.priceNegotiable && (dish.discountPercentage ?: 0.0) > 0.0 && dish.price.discounted(dish.discountPercentage) != dish.price) {
                    Text(
                        dish.price.format(),
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                        color = AzzaTheme.colors.textMuted,
                    )
                }
            }
        }
        // A dish with extras (or no fixed price) is chosen on its own page.
        if (dish.hasOptions || dish.priceNegotiable) {
            OutlinedIconButton(onClick = onOpen) { Icon(Icons.Outlined.Add, dish.title) }
        } else {
            Box {
                FilledIconButton(onClick = onAdd, enabled = canOrder) { Icon(Icons.Outlined.Add, dish.title) }
                if (count > 0) {
                    Text(
                        NumberFormat.getInstance(Locale.getDefault()).format(count),
                        style = MaterialTheme.typography.labelSmall,
                        color = AzzaTheme.colors.bg,
                        modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(AzzaTheme.colors.textPrimary).padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }
}
