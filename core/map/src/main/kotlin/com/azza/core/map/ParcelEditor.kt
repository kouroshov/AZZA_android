package com.azza.core.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.RectF
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Area
import com.azza.core.model.MapPoint
import com.azza.core.model.ParcelDraft
import com.azza.core.model.ParcelMode
import com.azza.core.ui.ChoiceMenu
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// ✅ (2026-10-22, product owner: "like the measure tool in Google Maps and
// Google Earth") A real-estate listing's place on the map, drawn full
// screen: a point (tap, drag the pin), or the land's borders — tap each
// corner; every side shows its length as you draw, area and perimeter are
// shown live; drag a corner to move it; drag the small dot in the middle of
// a side to add a corner there. apps/api measures the shape again on save.
// While a boundary is drawn, an existing boundary of the same land (another
// agency's listing) is offered — a copy, so both show as one place.

private const val SRC_SHAPE = "azza-draw-shape"
private const val SRC_HANDLES = "azza-draw-handles"
private const val SRC_SUGGEST = "azza-draw-suggest"
private const val L_FILL = "azza-draw-fill"
private const val L_LINE = "azza-draw-line"
private const val L_SUGGEST = "azza-draw-suggest"
private const val L_LABELS = "azza-draw-labels"
private const val L_MIDS = "azza-draw-mids"
private const val L_VERTICES = "azza-draw-vertices"
private const val L_POINT = "azza-draw-point"
private const val ACCENT = 0xFFF59E0B.toInt()

/** The unit words, from this module's strings. */
@Composable
fun rememberParcelUnits(): ParcelUnits {
    val m = stringResource(R.string.parcel_unit_m)
    val km = stringResource(R.string.parcel_unit_km)
    val m2 = stringResource(R.string.parcel_unit_m2)
    val ha = stringResource(R.string.parcel_unit_ha)
    return remember(m, km, m2, ha) { ParcelUnits(m, km, m2, ha) }
}

/**
 * The form's section: the choice (not on the map / a point / the borders)
 * and what was drawn; drawing itself opens full screen.
 */
@Composable
fun ParcelSection(
    draft: ParcelDraft,
    allowBoundary: Boolean,
    onChange: (ParcelDraft) -> Unit,
    findSimilar: suspend (List<MapPoint>) -> List<List<MapPoint>>,
    provinces: List<Area>,
    cities: List<Area>,
    onProvince: (Area) -> Unit,
    error: String? = null,
) {
    val colors = AzzaTheme.colors
    val units = rememberParcelUnits()
    var editing by remember { mutableStateOf<ParcelMode?>(null) }
    // A category that takes only a point keeps a boundary's centre.
    LaunchedEffect(allowBoundary, draft.mode) {
        if (!allowBoundary && draft.mode == ParcelMode.POLYGON) {
            val c = draft.ring.takeIf { it.isNotEmpty() }?.let { r -> MapPoint(r.sumOf { it.lng } / r.size, r.sumOf { it.lat } / r.size) }
            onChange(ParcelDraft(if (c != null) ParcelMode.POINT else ParcelMode.NONE, c))
        }
    }
    Column(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.parcel_section_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(if (allowBoundary) R.string.parcel_section_hint_boundary else R.string.parcel_section_hint_point),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = draft.mode == ParcelMode.NONE, onClick = { onChange(draft.copy(mode = ParcelMode.NONE)) }, label = { Text(stringResource(R.string.parcel_mode_none)) })
            FilterChip(selected = draft.mode == ParcelMode.POINT, onClick = { editing = ParcelMode.POINT }, label = { Text(stringResource(R.string.parcel_mode_point)) })
            if (allowBoundary) {
                FilterChip(selected = draft.mode == ParcelMode.POLYGON, onClick = { editing = ParcelMode.POLYGON }, label = { Text(stringResource(R.string.parcel_mode_boundary)) })
            }
        }
        when (draft.mode) {
            ParcelMode.POINT -> if (draft.point != null) Text(stringResource(R.string.parcel_point_set), style = MaterialTheme.typography.bodyMedium)
            ParcelMode.POLYGON -> if (draft.ring.size >= 3) {
                Text(
                    stringResource(R.string.parcel_summary, formatArea(ringAreaM2(draft.ring), units), formatLength(sideLengthsM(draft.ring).sum(), units)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ParcelMode.NONE -> Unit
        }
        if (draft.mode != ParcelMode.NONE) {
            OutlinedButton(onClick = { editing = draft.mode }) { Text(stringResource(R.string.parcel_edit_on_map)) }
        }
        error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
    }
    editing?.let { mode ->
        ParcelEditorDialog(
            initial = if (draft.mode == mode) draft else draft.copy(mode = mode),
            onDone = { onChange(it); editing = null },
            onDismiss = { editing = null },
            findSimilar = findSimilar,
            provinces = provinces,
            cities = cities,
            onProvince = onProvince,
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ParcelEditorDialog(
    initial: ParcelDraft,
    onDone: (ParcelDraft) -> Unit,
    onDismiss: () -> Unit,
    findSimilar: suspend (List<MapPoint>) -> List<List<MapPoint>>,
    provinces: List<Area>,
    cities: List<Area>,
    onProvince: (Area) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val colors = AzzaTheme.colors
        val context = LocalContext.current
        val units = rememberParcelUnits()
        var draft by remember { mutableStateOf(initial) }
        val latestDraft by rememberUpdatedState(draft)
        val history = remember { mutableStateListOf<List<MapPoint>>() }
        var map by remember { mutableStateOf<MapLibreMap?>(null) }
        var styleReady by remember { mutableStateOf(false) }
        var satellite by remember { mutableStateOf(hasSatellite) }
        val mapView = rememberMapViewWithLifecycle()

        fun setRing(ring: List<MapPoint>, remember: Boolean = true) {
            if (remember) history.add(latestDraft.ring)
            draft = latestDraft.copy(ring = ring)
        }

        LaunchedEffect(mapView) {
            mapView.getMapAsync { m ->
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.setLatLngBoundsForCameraTarget(IRAN_BOUNDS)
                m.setMaxZoomPreference(20.0)
                val d = latestDraft
                m.cameraPosition = when {
                    d.ring.size >= 2 -> m.getCameraForLatLngBounds(
                        LatLngBounds.Builder().apply { d.ring.forEach { include(LatLng(it.lat, it.lng)) } }.build(),
                        intArrayOf(80, 200, 80, 260),
                    ) ?: CameraPosition.Builder().target(LatLng(d.ring[0].lat, d.ring[0].lng)).zoom(17.0).build()
                    d.point != null -> CameraPosition.Builder().target(LatLng(d.point!!.lat, d.point!!.lng)).zoom(17.0).build()
                    else -> CameraPosition.Builder().target(LatLng(32.6, 53.7)).zoom(4.2).build()
                }
                m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                    style.addSource(GeoJsonSource(SRC_SUGGEST))
                    style.addSource(GeoJsonSource(SRC_SHAPE))
                    style.addSource(GeoJsonSource(SRC_HANDLES))
                    style.addLayer(LineLayer(L_SUGGEST, SRC_SUGGEST).withProperties(
                        PropertyFactory.lineColor(android.graphics.Color.WHITE),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                    ))
                    style.addLayer(FillLayer(L_FILL, SRC_SHAPE).withProperties(
                        PropertyFactory.fillColor(ACCENT),
                        PropertyFactory.fillOpacity(0.25f),
                    ).apply { setFilter(Expression.eq(Expression.geometryType(), "Polygon")) })
                    style.addLayer(LineLayer(L_LINE, SRC_SHAPE).withProperties(PropertyFactory.lineColor(ACCENT), PropertyFactory.lineWidth(3f)))
                    style.addLayer(SymbolLayer(L_LABELS, SRC_HANDLES).withProperties(
                        PropertyFactory.textField(Expression.get("text")),
                        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                        PropertyFactory.textSize(13f),
                        PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true),
                        PropertyFactory.textColor(android.graphics.Color.WHITE),
                        PropertyFactory.textHaloColor(android.graphics.Color.rgb(17, 24, 39)),
                        PropertyFactory.textHaloWidth(2f),
                    ).apply { setFilter(Expression.eq(Expression.get("role"), "label")) })
                    style.addLayer(CircleLayer(L_MIDS, SRC_HANDLES).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeColor(ACCENT),
                        PropertyFactory.circleStrokeWidth(2f),
                    ).apply { setFilter(Expression.eq(Expression.get("role"), "mid")) })
                    style.addLayer(CircleLayer(L_VERTICES, SRC_HANDLES).withProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleColor(ACCENT),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2f),
                    ).apply { setFilter(Expression.eq(Expression.get("role"), "vertex")) })
                    style.addLayer(CircleLayer(L_POINT, SRC_HANDLES).withProperties(
                        PropertyFactory.circleRadius(12f),
                        PropertyFactory.circleColor(ACCENT),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(3f),
                    ).apply { setFilter(Expression.eq(Expression.get("role"), "point")) })
                    style.applyBaseLook(satellite)
                    styleReady = true
                }
                m.addOnMapClickListener { latLng ->
                    val d = latestDraft
                    val at = MapPoint(latLng.longitude, latLng.latitude)
                    when (d.mode) {
                        ParcelMode.POINT -> draft = d.copy(point = at)
                        ParcelMode.POLYGON -> setRing(d.ring + at)
                        ParcelMode.NONE -> Unit
                    }
                    true
                }
                map = m
            }
            // Dragging a corner, a side's middle (a new corner) or the pin. A
            // touch that starts on none of them is the map's (pan, zoom, tap).
            var dragging: Int? = null
            var draggingPoint = false
            mapView.setOnTouchListener { _, ev ->
                val m = map ?: return@setOnTouchListener false
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val slop = 20 * context.resources.displayMetrics.density
                        val box = RectF(ev.x - slop, ev.y - slop, ev.x + slop, ev.y + slop)
                        val d = latestDraft
                        if (d.mode == ParcelMode.POINT && m.queryRenderedFeatures(box, L_POINT).isNotEmpty()) {
                            draggingPoint = true
                            return@setOnTouchListener true
                        }
                        if (d.mode != ParcelMode.POLYGON) return@setOnTouchListener false
                        m.queryRenderedFeatures(box, L_VERTICES).firstOrNull()?.getNumberProperty("index")?.toInt()?.let {
                            history.add(d.ring)
                            dragging = it
                            return@setOnTouchListener true
                        }
                        m.queryRenderedFeatures(box, L_MIDS).firstOrNull()?.getNumberProperty("side")?.toInt()?.let { side ->
                            val at = m.projection.fromScreenLocation(android.graphics.PointF(ev.x, ev.y))
                            setRing(d.ring.toMutableList().apply { add(side + 1, MapPoint(at.longitude, at.latitude)) })
                            dragging = side + 1
                            return@setOnTouchListener true
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val at = m.projection.fromScreenLocation(android.graphics.PointF(ev.x, ev.y))
                        val p = MapPoint(at.longitude, at.latitude)
                        when {
                            draggingPoint -> { draft = latestDraft.copy(point = p); true }
                            dragging != null -> {
                                setRing(latestDraft.ring.toMutableList().apply { this[dragging!!] = p }, remember = false)
                                true
                            }
                            else -> false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val was = dragging != null || draggingPoint
                        dragging = null
                        draggingPoint = false
                        was
                    }
                    else -> dragging != null || draggingPoint
                }
            }
        }

        LaunchedEffect(satellite, styleReady) { if (styleReady) map?.style?.applyBaseLook(satellite) }

        // Draw the current value.
        val locale = java.util.Locale.getDefault()
        LaunchedEffect(draft, styleReady) {
            val style = map?.style ?: return@LaunchedEffect
            if (!styleReady) return@LaunchedEffect
            val ring = if (draft.mode == ParcelMode.POLYGON) draft.ring else emptyList()
            val shape = when {
                ring.size >= 3 -> listOf(Feature.fromGeometry(Polygon.fromLngLats(listOf((ring + ring[0]).map { Point.fromLngLat(it.lng, it.lat) }))))
                ring.size == 2 -> listOf(Feature.fromGeometry(LineString.fromLngLats(ring.map { Point.fromLngLat(it.lng, it.lat) })))
                else -> emptyList()
            }
            style.getSourceAs<GeoJsonSource>(SRC_SHAPE)?.setGeoJson(FeatureCollection.fromFeatures(shape))
            val handles = mutableListOf<Feature>()
            sideLengthsM(ring).forEachIndexed { i, length ->
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                val mid = Point.fromLngLat((a.lng + b.lng) / 2, (a.lat + b.lat) / 2)
                handles += Feature.fromGeometry(mid).apply { addStringProperty("role", "label"); addStringProperty("text", formatLength(length, units, locale)) }
                handles += Feature.fromGeometry(mid).apply { addStringProperty("role", "mid"); addNumberProperty("side", i) }
            }
            ring.forEachIndexed { i, p ->
                handles += Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply { addStringProperty("role", "vertex"); addNumberProperty("index", i) }
            }
            if (draft.mode == ParcelMode.POINT) draft.point?.let { p ->
                handles += Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply { addStringProperty("role", "point") }
            }
            style.getSourceAs<GeoJsonSource>(SRC_HANDLES)?.setGeoJson(FeatureCollection.fromFeatures(handles))
        }

        // "This land may already be on the map."
        var suggestion by remember { mutableStateOf<List<MapPoint>?>(null) }
        val dismissed = remember { mutableStateListOf<List<MapPoint>>() }
        val problem = if (draft.mode == ParcelMode.POLYGON) ringProblem(draft.ring) else null
        LaunchedEffect(draft.ring, draft.mode) {
            suggestion = null
            if (draft.mode != ParcelMode.POLYGON || ringProblem(draft.ring) != null) return@LaunchedEffect
            delay(800)
            suggestion = runCatching { findSimilar(draft.ring) }.getOrDefault(emptyList())
                .firstOrNull { it != draft.ring && it !in dismissed }
        }
        LaunchedEffect(suggestion, styleReady) {
            val s = suggestion
            map?.style?.getSourceAs<GeoJsonSource>(SRC_SUGGEST)?.setGeoJson(
                FeatureCollection.fromFeatures(
                    if (s == null) emptyList()
                    else listOf(Feature.fromGeometry(LineString.fromLngLats((s + s[0]).map { Point.fromLngLat(it.lng, it.lat) }))),
                ),
            )
        }

        // My location, and go to a city.
        var locating by remember { mutableStateOf(false) }
        var locationProblem by remember { mutableStateOf(false) }
        val goToMe = {
            locating = true
            locationProblem = false
            currentLocation(context) { lat, lng ->
                locating = false
                if (lat == null || lng == null || !insideIran(lat, lng)) locationProblem = true
                else map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17.0), 800)
            }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) goToMe() else locationProblem = true
        }
        var cityOpen by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(colors.bg)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            if (!styleReady) CircularProgressIndicator(Modifier.align(Alignment.Center))

            // Top: close, the mode's hint, and the map tools.
            Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.parcel_cancel)) }
                    Text(
                        stringResource(if (draft.mode == ParcelMode.POINT) R.string.parcel_hint_point else R.string.parcel_hint_boundary),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false).background(colors.bg.copy(alpha = 0.92f), RoundedCornerShape(8.dp)).padding(8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { cityOpen = true },
                        label = { Text(stringResource(R.string.parcel_go_to_city)) },
                        leadingIcon = { Icon(Icons.Outlined.LocationCity, null, Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = colors.bg),
                    )
                    if (hasSatellite) {
                        AssistChip(
                            onClick = { satellite = !satellite },
                            label = { Text(stringResource(if (satellite) R.string.map_style_map else R.string.map_style_satellite)) },
                            leadingIcon = { Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = colors.bg),
                        )
                    }
                    SmallFloatingActionButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) goToMe()
                        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }) {
                        if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.MyLocation, stringResource(R.string.parcel_my_location))
                    }
                }
                if (locationProblem) {
                    Text(stringResource(R.string.parcel_location_unavailable), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                        modifier = Modifier.background(colors.bg, RoundedCornerShape(8.dp)).padding(8.dp))
                }
            }

            // Bottom: the numbers, the suggestion, undo/clear and Done.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                    .background(colors.bg.copy(alpha = 0.96f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draft.mode == ParcelMode.POLYGON) {
                    val ring = draft.ring
                    Text(
                        if (ring.size >= 3) stringResource(R.string.parcel_summary, formatArea(ringAreaM2(ring), units), formatLength(sideLengthsM(ring).sum(), units))
                        else pluralStringResource(R.plurals.parcel_corners, ring.size, ring.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (problem != null && ring.isNotEmpty()) {
                        Text(stringResource(problem.message()), color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
                    suggestion?.let { s ->
                        Column(Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.parcel_similar_found), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { setRing(s); suggestion = null }) { Text(stringResource(R.string.parcel_similar_use)) }
                                TextButton(onClick = { dismissed.add(s); suggestion = null }) { Text(stringResource(R.string.parcel_similar_keep)) }
                            }
                        }
                    }
                } else if (draft.point == null) {
                    Text(stringResource(R.string.parcel_point_missing), style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (draft.mode == ParcelMode.POLYGON) {
                        IconButton(onClick = { history.removeLastOrNull()?.let { draft = latestDraft.copy(ring = it) } }, enabled = history.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.parcel_undo))
                        }
                        IconButton(onClick = { setRing(emptyList()) }, enabled = draft.ring.isNotEmpty()) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.parcel_clear))
                        }
                    }
                    Box(Modifier.weight(1f))
                    Button(onClick = { onDone(draft) }, enabled = draft.isComplete() && draft.mode != ParcelMode.NONE) {
                        Text(stringResource(R.string.parcel_done))
                    }
                }
            }
        }

        if (cityOpen) {
            CityPicker(provinces, cities, onProvince, onDismiss = { cityOpen = false }) { area ->
                cityOpen = false
                val lat = area.latitude
                val lng = area.longitude
                if (lat != null && lng != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 13.0), 800)
            }
        }
    }
}

private fun RingProblem.message(): Int = when (this) {
    RingProblem.TOO_FEW_VERTICES -> R.string.parcel_problem_too_few
    RingProblem.TOO_MANY_VERTICES -> R.string.parcel_problem_too_many
    RingProblem.REPEATED_VERTEX -> R.string.parcel_problem_repeated
    RingProblem.SELF_INTERSECTING -> R.string.parcel_problem_crossing
    RingProblem.TOO_SMALL -> R.string.parcel_problem_too_small
    RingProblem.TOO_LARGE -> R.string.parcel_problem_too_large
}

/** Province, then city — where to start drawing. */
@Composable
private fun CityPicker(provinces: List<Area>, cities: List<Area>, onProvince: (Area) -> Unit, onDismiss: () -> Unit, onCity: (Area) -> Unit) {
    var province by remember { mutableStateOf<Area?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parcel_go_to_city)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceMenu(stringResource(R.string.parcel_province), province?.name.orEmpty(), provinces.map { it.name }, { name ->
                    provinces.firstOrNull { it.name == name }?.let { province = it; onProvince(it) }
                }, Modifier.fillMaxWidth())
                ChoiceMenu(stringResource(R.string.parcel_city), "", cities.map { it.name }, { name ->
                    cities.firstOrNull { it.name == name }?.let(onCity)
                }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.parcel_cancel)) } },
    )
}
