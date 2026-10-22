package com.azza.core.map

import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.maps.Style

/** The web's NEXT_PUBLIC_MAP_STYLE_URL: OpenFreeMap, no key, the same tiles as the website. */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Satellite imagery (product owner, 2026-10-22: the map switches between
 * the plain map and satellite). The same source as the website's
 * NEXT_PUBLIC_MAP_SATELLITE_TILES — Esri World Imagery, for DEVELOPMENT:
 * production needs a licensed source (web repo, docs/PRODUCTION-READINESS.md
 * §8). Empty → the satellite option is not offered.
 */
const val SATELLITE_TILES = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
const val SATELLITE_ATTRIBUTION = "Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
private const val SATELLITE_MAX_ZOOM = 19f

val hasSatellite: Boolean get() = SATELLITE_TILES.isNotBlank()

private const val SATELLITE_ID = "azza-satellite"

/**
 * Map ↔ satellite without reloading the style (which would drop every layer
 * the app added): one raster layer right above the background; while it
 * shows, the base style's fills and lines are hidden and only its labels
 * stay on top — Google's "hybrid". The style's own hidden layers stay hidden.
 */
fun Style.applyBaseLook(satellite: Boolean) {
    if (!hasSatellite) return
    if (satellite && getSource(SATELLITE_ID) == null) {
        addSource(
            RasterSource(
                SATELLITE_ID,
                TileSet("2.2.0", SATELLITE_TILES).apply {
                    attribution = SATELLITE_ATTRIBUTION
                    maxZoom = SATELLITE_MAX_ZOOM
                },
                256,
            ),
        )
        val firstAboveBackground = layers.firstOrNull { it !is org.maplibre.android.style.layers.BackgroundLayer }
        val raster = RasterLayer(SATELLITE_ID, SATELLITE_ID)
        if (firstAboveBackground != null) addLayerBelow(raster, firstAboveBackground.id) else addLayer(raster)
    }
    getLayer(SATELLITE_ID)?.setProperties(PropertyFactory.visibility(if (satellite) Property.VISIBLE else Property.NONE))
    for (layer in layers) {
        if (layer.id.startsWith("azza-") || !layer.coversImagery()) continue
        val original = originalVisibility.getOrPut(layer.id) { layer.visibility.value ?: Property.VISIBLE }
        layer.setProperties(PropertyFactory.visibility(if (satellite) Property.NONE else original))
    }
}

/** The base style's layers' own visibility, read before the first switch (layer ids are the style's, shared by every map). */
private val originalVisibility = mutableMapOf<String, String>()

private fun Layer.coversImagery() = this is FillLayer || this is LineLayer || this is FillExtrusionLayer || this is HillshadeLayer
