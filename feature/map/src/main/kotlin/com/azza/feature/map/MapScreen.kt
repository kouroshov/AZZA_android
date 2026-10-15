package com.azza.feature.map

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.BranchMarker
import com.azza.core.data.BranchPreview
import com.azza.core.data.MapRepository
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.Avatar
import com.azza.core.ui.errorMessage
import com.azza.core.ui.format
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** The web's "/" — the marketplace map of store branches. */
@Serializable data object MapRoute

fun NavGraphBuilder.mapScreen(onOpenStore: (String) -> Unit, onOpenProduct: (String) -> Unit) {
    composable<MapRoute> { MapScreen(onOpenStore, onOpenProduct) }
}

/** The web's NEXT_PUBLIC_MAP_STYLE_URL: OpenFreeMap, no key, the same tiles as the website. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE = "azza-branches"
private const val LAYER = "azza-branches-dots"

/** apps/api's limit on a map query, in degrees per side (less a little, for rounding). */
private const val MAX_SPAN = 21.9

/** Iran as a whole, as the map opens before anything is known about the person. */
private val START = CameraPosition.Builder().target(LatLng(32.4, 53.4)).zoom(3.9).build()

data class MapUiState(
    val markers: List<BranchMarker> = emptyList(),
    val loading: Boolean = false,
    val error: AppError? = null,
    val selected: String? = null,
    val preview: BranchPreview? = null,
    val previewLoading: Boolean = false,
)

@HiltViewModel
class MapViewModel @Inject constructor(private val repository: MapRepository) : ViewModel() {
    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()
    private var viewportJob: Job? = null

    /** Called when the camera settles; a short pause so panning does not fire a request per frame. */
    fun onViewport(north: Double, south: Double, east: Double, west: Double) {
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(250)
            _state.update { it.copy(loading = true) }
            // apps/api refuses an area wider than 22° a side (GEOGRAPHY_QUERY_TOO_LARGE):
            // zoomed far out, ask for the middle of the screen instead of failing.
            val (n, s) = clampSpan(south, north)
            val (e, w) = clampSpan(west, east)
            when (val r = repository.branches(n, s, e, w)) {
                is Outcome.Success -> _state.update { it.copy(markers = r.value, loading = false, error = null) }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    /** (high, low) around the middle, at most [MAX_SPAN] apart. */
    private fun clampSpan(low: Double, high: Double): Pair<Double, Double> {
        if (high - low <= MAX_SPAN) return high to low
        val mid = (high + low) / 2
        return (mid + MAX_SPAN / 2) to (mid - MAX_SPAN / 2)
    }

    fun select(branchId: String) {
        _state.update { it.copy(selected = branchId, preview = null, previewLoading = true) }
        viewModelScope.launch {
            val r = repository.preview(branchId)
            _state.update {
                if (it.selected != branchId) it
                else it.copy(previewLoading = false, preview = (r as? Outcome.Success)?.value, error = (r as? Outcome.Failure)?.error)
            }
        }
    }

    fun closePreview() = _state.update { it.copy(selected = null, preview = null, previewLoading = false) }
}

@Composable
fun MapScreen(onOpenStore: (String) -> Unit, onOpenProduct: (String) -> Unit, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    val primary = AzzaTheme.colors.primary

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    // MapView has its own lifecycle; forward the screen's to it.
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = START
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                style.addSource(GeoJsonSource(SOURCE))
                style.addLayer(
                    CircleLayer(LAYER, SOURCE).withProperties(
                        PropertyFactory.circleRadius(8f),
                        // Open now: the brand blue; closed or unknown: grey — the web's open-status colours.
                        PropertyFactory.circleColor(
                            Expression.match(
                                Expression.get("status"),
                                Expression.color(Color.GRAY),
                                Expression.stop("OPEN", Expression.color(android.graphics.Color.argb(255, (primary.red * 255).toInt(), (primary.green * 255).toInt(), (primary.blue * 255).toInt()))),
                            ),
                        ),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2f),
                    ),
                )
                styleReady = true
                val bounds = m.projection.visibleRegion.latLngBounds
                viewModel.onViewport(bounds.latitudeNorth, bounds.latitudeSouth, bounds.longitudeEast, bounds.longitudeWest)
            }
            m.addOnCameraIdleListener {
                val bounds = m.projection.visibleRegion.latLngBounds
                viewModel.onViewport(bounds.latitudeNorth, bounds.latitudeSouth, bounds.longitudeEast, bounds.longitudeWest)
            }
            m.addOnMapClickListener { latLng ->
                val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(latLng), LAYER).firstOrNull()
                val id = hit?.getStringProperty("branchId")
                if (id != null) viewModel.select(id) else viewModel.closePreview()
                id != null
            }
            map = m
        }
    }

    // Markers → the GeoJSON source, whenever they change.
    LaunchedEffect(state.markers, styleReady) {
        val style = map?.style ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                state.markers.map { marker ->
                    Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude)).apply {
                        addStringProperty("branchId", marker.branchId)
                        addStringProperty("status", marker.status)
                    }
                },
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        state.error?.takeIf { state.selected == null }?.let {
            Text(errorMessage(it), color = AzzaTheme.colors.error, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                .background(AzzaTheme.colors.bg, RoundedCornerShape(8.dp)).padding(8.dp))
        }
        if (state.selected != null) {
            PreviewCard(
                state = state,
                onClose = viewModel::closePreview,
                onOpenStore = onOpenStore,
                onOpenProduct = onOpenProduct,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The web's BranchPreviewPanel: the store, whether it is open, and some of what it sells. */
@Composable
private fun PreviewCard(
    state: MapUiState,
    onClose: () -> Unit,
    onOpenStore: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier,
) {
    val colors = AzzaTheme.colors
    Column(
        modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(colors.navPopover).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val p = state.preview
        if (p == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.map_close)) }
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(url = p.logoUrl, isStore = true, size = 44)
            Column(Modifier.weight(1f)) {
                Text(p.storeName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(
                        when (p.openStatus) {
                            "OPEN" -> R.string.map_open_now
                            "CLOSED" -> R.string.map_closed_now
                            else -> R.string.map_hours_unknown
                        },
                    ),
                    color = if (p.openStatus == "OPEN") colors.success else colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.map_close)) }
        }
        if (p.address.isNotBlank()) Text(p.address, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        if (p.products.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.products.forEach { product ->
                    Column(Modifier.width(96.dp).clickable { onOpenProduct(product.id) }) {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(colors.surface)) {
                            product.imageUrl?.let { AsyncImage(it, product.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
                        }
                        Text(product.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Text(product.price.format(), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
        Button(onClick = { onOpenStore(p.storeSlug) }, modifier = Modifier.fillMaxWidth()) {
            Text(pluralStringResource(R.plurals.map_open_store, p.productCount, p.productCount))
        }
    }
}
