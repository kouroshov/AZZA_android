package com.azza.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.map.dealColor
import com.azza.core.map.formatArea
import com.azza.core.map.rememberParcelUnits
import com.azza.core.map.toArgbInt
import com.azza.core.model.ParcelDeal
import com.azza.core.model.ParcelListing
import com.azza.core.model.ParcelMapItem
import com.azza.core.ui.Avatar
import com.azza.core.ui.format
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// ✅ (2026-10-22, product owner) The real-estate mode of the map: land and
// property listings coloured by deal. Zoomed out, every listing is a dot at
// its centre, clustered; from SHAPES_ZOOM the drawn boundaries show. A
// boundary several agencies drew for the same land arrives from apps/api as
// ONE item listing all of them; several deals in one item = grey (MIXED).
// A tap opens a card listing every ad on that spot, newest first.

internal const val SHAPES_ZOOM = 14f
internal const val PARCEL_DOTS = "azza-parcel-dots"
internal const val PARCEL_SHAPES = "azza-parcel-shapes"
internal const val L_PARCEL_FILL = "azza-parcel-fill"
internal const val L_PARCEL_OUTLINE = "azza-parcel-outline"
internal const val L_PARCEL_CLUSTERS = "azza-parcel-clusters"
private const val L_PARCEL_CLUSTER_COUNT = "azza-parcel-cluster-count"
internal const val L_PARCEL_DOTS = "azza-parcel-dot"
private const val L_PARCEL_COUNT = "azza-parcel-count"

/** The layers of the stores mode, hidden in the real-estate mode (and back). */
internal val STORE_LAYERS = listOf("azza-country-circle", "azza-country-count", "azza-city-circle", "azza-city-count", "azza-branches-dots")
internal val PARCEL_LAYERS = listOf(L_PARCEL_FILL, L_PARCEL_OUTLINE, L_PARCEL_CLUSTERS, L_PARCEL_CLUSTER_COUNT, L_PARCEL_DOTS, L_PARCEL_COUNT)

private fun dealExpression(): Expression = Expression.match(
    Expression.get("deal"),
    Expression.color(dealColor(ParcelDeal.MIXED).toArgbInt()),
    *ParcelDeal.entries.filter { it != ParcelDeal.MIXED }
        .map { Expression.stop(it.name, Expression.color(dealColor(it).toArgbInt())) }.toTypedArray(),
)

internal fun addParcelLayers(style: Style) {
    style.addSource(
        GeoJsonSource(PARCEL_DOTS, GeoJsonOptions().withCluster(true).withClusterMaxZoom(SHAPES_ZOOM.toInt() - 1).withClusterRadius(50)),
    )
    style.addSource(GeoJsonSource(PARCEL_SHAPES))
    style.addLayer(FillLayer(L_PARCEL_FILL, PARCEL_SHAPES).withProperties(
        PropertyFactory.fillColor(dealExpression()),
        PropertyFactory.fillOpacity(0.3f),
    ).apply { setMinZoom(SHAPES_ZOOM) })
    style.addLayer(LineLayer(L_PARCEL_OUTLINE, PARCEL_SHAPES).withProperties(
        PropertyFactory.lineColor(dealExpression()),
        PropertyFactory.lineWidth(2.5f),
    ).apply { setMinZoom(SHAPES_ZOOM) })
    style.addLayer(CircleLayer(L_PARCEL_CLUSTERS, PARCEL_DOTS).withProperties(
        PropertyFactory.circleColor(android.graphics.Color.rgb(55, 65, 81)),
        PropertyFactory.circleRadius(Expression.step(Expression.get("point_count"), Expression.literal(16f), Expression.stop(10, 22f), Expression.stop(50, 28f))),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
        PropertyFactory.circleStrokeWidth(2f),
    ).apply { setFilter(Expression.has("point_count")) })
    style.addLayer(SymbolLayer(L_PARCEL_CLUSTER_COUNT, PARCEL_DOTS).withProperties(
        PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textColor(android.graphics.Color.WHITE),
        PropertyFactory.textAllowOverlap(true),
    ).apply { setFilter(Expression.has("point_count")) })
    // Dots: every point, and a boundary's centre until the boundary itself shows.
    val dotFilter = Expression.all(
        Expression.not(Expression.has("point_count")),
        Expression.any(Expression.eq(Expression.get("boundary"), false), Expression.lt(Expression.zoom(), SHAPES_ZOOM)),
    )
    style.addLayer(CircleLayer(L_PARCEL_DOTS, PARCEL_DOTS).withProperties(
        PropertyFactory.circleColor(dealExpression()),
        PropertyFactory.circleRadius(Expression.switchCase(Expression.gt(Expression.get("count"), 1), Expression.literal(11f), Expression.literal(8f))),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
        PropertyFactory.circleStrokeWidth(2f),
    ).apply { setFilter(dotFilter) })
    // "3" on a place several listings share.
    style.addLayer(SymbolLayer(L_PARCEL_COUNT, PARCEL_DOTS).withProperties(
        PropertyFactory.textField(Expression.toString(Expression.get("count"))),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textColor(android.graphics.Color.WHITE),
        PropertyFactory.textHaloColor(android.graphics.Color.rgb(17, 24, 39)),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textAllowOverlap(true),
    ).apply { setFilter(Expression.all(Expression.not(Expression.has("point_count")), Expression.gt(Expression.get("count"), 1))) })
}

/** Shows one mode's layers and hides the other's. */
internal fun Style.showRealEstate(on: Boolean) {
    STORE_LAYERS.forEach { getLayer(it)?.setProperties(PropertyFactory.visibility(if (on) Property.NONE else Property.VISIBLE)) }
    PARCEL_LAYERS.forEach { getLayer(it)?.setProperties(PropertyFactory.visibility(if (on) Property.VISIBLE else Property.NONE)) }
}

internal fun Style.setParcels(items: List<ParcelMapItem>) {
    val dots = items.mapIndexed { i, item ->
        Feature.fromGeometry(Point.fromLngLat(item.centre.lng, item.centre.lat)).apply { describe(item, i) }
    }
    val shapes = items.mapIndexedNotNull { i, item ->
        if (!item.isBoundary || item.ring.size < 3) return@mapIndexedNotNull null
        Feature.fromGeometry(Polygon.fromLngLats(listOf((item.ring + item.ring[0]).map { Point.fromLngLat(it.lng, it.lat) }))).apply { describe(item, i) }
    }
    getSourceAs<GeoJsonSource>(PARCEL_DOTS)?.setGeoJson(FeatureCollection.fromFeatures(dots))
    getSourceAs<GeoJsonSource>(PARCEL_SHAPES)?.setGeoJson(FeatureCollection.fromFeatures(shapes))
}

private fun Feature.describe(item: ParcelMapItem, index: Int) {
    addNumberProperty("index", index)
    addStringProperty("deal", (item.deal ?: ParcelDeal.MIXED).name)
    addNumberProperty("count", item.listings.size)
    addBooleanProperty("boundary", item.isBoundary)
}

/** The card a tap opens: every listing on that spot, newest first. */
@Composable
internal fun ParcelCard(
    item: ParcelMapItem,
    onClose: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenOwner: (ParcelListing) -> Unit,
    modifier: Modifier,
) {
    val colors = AzzaTheme.colors
    val units = rememberParcelUnits()
    Column(
        modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(colors.navPopover).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (item.listings.size > 1) pluralStringResource(R.plurals.map_parcel_listings_here, item.listings.size, item.listings.size)
                else stringResource(R.string.map_parcel_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.map_close)) }
        }
        if (item.listings.size > 1) Text(stringResource(R.string.map_parcel_same_land), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(item.listings, key = { it.productId }) { l ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { onOpenProduct(l.productId) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
                            if (l.imageUrl != null) AsyncImage(l.imageUrl, l.title, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                            else Icon(Icons.Outlined.Home, null, tint = colors.textMuted)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // The deal in words as well as colour.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(dealColor(l.deal)))
                                Text(stringResource(dealLabel(l.deal)), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                            }
                            Text(l.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.price.format(), style = MaterialTheme.typography.bodyMedium)
                            l.areaM2?.let { Text(formatArea(it, units), style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
                        }
                    }
                    OutlinedButton(onClick = { onOpenOwner(l) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Avatar(url = l.owner.avatarUrl, isStore = l.owner.isStore, size = 22)
                        Text(
                            l.owner.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(stringResource(if (l.owner.isStore) R.string.map_parcel_agency_page else R.string.map_parcel_seller_page), color = colors.primaryText)
                    }
                }
            }
        }
    }
}

internal fun dealLabel(deal: ParcelDeal?): Int = when (deal) {
    ParcelDeal.SALE -> com.azza.core.map.R.string.parcel_deal_SALE
    ParcelDeal.RENT -> com.azza.core.map.R.string.parcel_deal_RENT
    ParcelDeal.SHORT_TERM -> com.azza.core.map.R.string.parcel_deal_SHORT_TERM
    ParcelDeal.PARTNERSHIP -> com.azza.core.map.R.string.parcel_deal_PARTNERSHIP
    ParcelDeal.PRESALE -> com.azza.core.map.R.string.parcel_deal_PRESALE
    ParcelDeal.MIXED -> com.azza.core.map.R.string.parcel_deal_MIXED
    null -> com.azza.core.map.R.string.parcel_deal_OTHER
}
