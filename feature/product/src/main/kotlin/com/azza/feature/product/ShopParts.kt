package com.azza.feature.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.DispatchEstimate
import com.azza.core.model.ProductDetail
import com.azza.core.model.SchemaRules
import com.azza.core.ui.discounted
import com.azza.core.ui.format
import java.text.NumberFormat
import java.util.Locale

// ✅ (2026-10-25, Phase 5 — shops, after Digikala; the web's ShopFacts,
// VariantPicker and VariantsOwnerPanel). Everything here is read from apps/api
// (GET /products/:id): the stock line, the other sizes and colours, and when
// a store's goods would be sent. Each variant is a listing of its own, so
// choosing one opens its page.

/** The score under the title: ★ 4.5 · 12 reviews. */
@Composable
internal fun RatingLine(average: Double, count: Int) {
    val numbers = NumberFormat.getInstance(Locale.getDefault()).apply { maximumFractionDigits = 1 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Star, null, tint = AzzaTheme.colors.warning, modifier = Modifier.size(16.dp))
        Text(numbers.format(average), style = MaterialTheme.typography.bodyMedium)
        Text(
            "· " + pluralStringResource(R.plurals.shop_review_count, count, numbers.format(count)),
            style = MaterialTheme.typography.bodyMedium,
            color = AzzaTheme.colors.textMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VariantChips(product: ProductDetail, onOpen: (String) -> Unit) {
    if (product.variants.size < 2) return
    val colors = AzzaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.shop_variants) + (product.variantLabel?.let { "  ·  $it" } ?: ""),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            product.variants.forEach { v ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if (v.current) 2.dp else 1.dp, if (v.current) colors.primary else colors.navDivider),
                    color = if (v.current) colors.selectedContainer else colors.bg,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !v.current) { onOpen(v.id) },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            v.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = TextDirection.Content,
                                textDecoration = if (v.inStock) null else TextDecoration.LineThrough,
                            ),
                            color = if (v.inStock) colors.textPrimary else colors.textMuted,
                        )
                        Text(
                            if (v.inStock) v.price.discounted(v.discountPercentage).format() else stringResource(R.string.product_out_of_stock),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** «موجود» / «تنها ۲ عدد باقی مانده» / «ناموجود», and «ارسال ۱ تا ۳ روز کاری (۴ تا ۶ آبان)». */
@Composable
internal fun StockAndDispatch(product: ProductDetail) {
    val colors = AzzaTheme.colors
    val state = product.stockState?.takeIf { it != "UNKNOWN" && product.status == "ACTIVE" && product.purchasable }
    val dispatch = product.dispatch?.takeIf { product.status == "ACTIVE" && product.purchasable }
    if (state == null && dispatch == null) return
    val numbers = NumberFormat.getInstance(Locale.getDefault())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state?.let { s ->
            val (text, tint) = when {
                s == "OUT_OF_STOCK" -> stringResource(R.string.product_out_of_stock) to colors.error
                product.stockLeft != null -> pluralStringResource(R.plurals.shop_only_left, product.stockLeft!!, numbers.format(product.stockLeft!!)) to colors.warning
                s == "LOW_STOCK" -> stringResource(R.string.shop_low_stock) to colors.warning
                else -> stringResource(R.string.product_in_stock) to colors.success
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Inventory2, null, tint = tint, modifier = Modifier.size(18.dp))
                Text(text, color = tint, style = MaterialTheme.typography.bodyMedium)
            }
        }
        dispatch?.let { d ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.LocalShipping, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                Text(dispatchText(d), color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun dispatchText(d: DispatchEstimate): String {
    if (d.maxDays == 0) return stringResource(R.string.shop_dispatch_today)
    val language = LocalConfiguration.current.locales[0].language
    val numbers = NumberFormat.getInstance(Locale.getDefault())
    return stringResource(
        R.string.shop_dispatch_range,
        numbers.format(d.minDays),
        numbers.format(d.maxDays),
        dayMonth(d.from, language),
        dayMonth(d.to, language),
    )
}

/** "2026-10-26" as «۴ آبان» (the Solar Hijri calendar for fa and ckb) or "26 October". */
private fun dayMonth(iso: String, language: String): String = runCatching {
    val (y, m, d) = iso.split("-").map { it.toInt() }
    val persian = language == "fa" || language == "ckb"
    val locale = android.icu.util.ULocale(if (persian) "${language}_IR@calendar=persian" else language)
    val calendar = android.icu.util.Calendar.getInstance(android.icu.util.TimeZone.getTimeZone("UTC"), locale)
    val gregorian = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d, 12, 0) }
    calendar.timeInMillis = gregorian.timeInMillis
    android.icu.text.SimpleDateFormat("d MMMM", locale).apply { timeZone = android.icu.util.TimeZone.getTimeZone("UTC") }.format(calendar.time)
}.getOrDefault(iso)

/** The owner of a store listing adds another size or colour: a name, a price (toman) and a stock count. */
@Composable
internal fun AddVariantButton(product: ProductDetail, busy: Boolean, onAdd: (label: String, priceToman: Double, quantity: Int?, mainLabel: String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = !busy, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.shop_add_variant))
    }
    if (!open) return
    val needsMainLabel = product.variantLabel == null && product.variants.size < 2
    var mainLabel by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    fun number(text: String) = SchemaRules.toLatinDigits(text).replace(",", "").replace("٬", "").trim().toDoubleOrNull()
    val priceValue = number(price)
    val ready = label.isNotBlank() && (priceValue ?: 0.0) > 0 && (!needsMainLabel || mainLabel.isNotBlank())
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.shop_add_variant)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.shop_variants_hint), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                if (needsMainLabel) {
                    OutlinedTextField(
                        value = mainLabel,
                        onValueChange = { mainLabel = it.take(60) },
                        label = { Text(stringResource(R.string.shop_main_label)) },
                        placeholder = { Text(stringResource(R.string.shop_main_label_placeholder)) },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(60) },
                    label = { Text(stringResource(R.string.shop_variant_label)) },
                    placeholder = { Text(stringResource(R.string.shop_variant_label_placeholder)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.take(15) },
                    label = { Text(stringResource(R.string.shop_variant_price)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.shop_variant_quantity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready && !busy,
                onClick = {
                    onAdd(label, priceValue ?: 0.0, quantity.toIntOrNull(), mainLabel.takeIf { needsMainLabel })
                    open = false
                },
            ) { Text(stringResource(R.string.shop_add)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.shop_cancel)) } },
    )
}
