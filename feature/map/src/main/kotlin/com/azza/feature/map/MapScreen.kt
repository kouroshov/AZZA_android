package com.azza.feature.map

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import com.azza.core.ui.ChoiceMenu
import com.azza.core.model.CategoryNode
import com.azza.core.model.Area
import com.azza.core.data.GeographyRepository
import com.azza.core.data.ListingRepository
import androidx.compose.material.icons.outlined.MyLocation
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

/** Where the phone is, once "near me" found it — for the distance on a preview. */
data class Here(val latitude: Double, val longitude: Double)

/** Iran's box: a fix outside it does not move the map (the web's "outsideIran"). */
internal fun insideIran(lat: Double, lng: Double) = lat in 24.8..40.0 && lng in 43.9..63.5

data class MapUiState(
    val markers: List<BranchMarker> = emptyList(),
    /** The map shows only stores holding this category (the web's category filter). */
    val categoryId: String? = null,
    val categories: List<CategoryNode> = emptyList(),
    val here: Here? = null,
    /** Zoomed out past what is worth listing (the web lists from city level). */
    val tooFarOut: Boolean = false,
    val provinces: List<Area> = emptyList(),
    val cities: List<Area> = emptyList(),
    val loading: Boolean = false,
    val error: AppError? = null,
    val selected: String? = null,
    val preview: BranchPreview? = null,
    val previewLoading: Boolean = false,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: MapRepository,
    private val listings: ListingRepository,
    private val geography: GeographyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()
    private var viewportJob: Job? = null
    private var lastViewport: DoubleArray? = null

    init {
        viewModelScope.launch {
            (listings.categoryTree() as? Outcome.Success)?.let { r -> _state.update { it.copy(categories = r.value) } }
            (geography.provinces() as? Outcome.Success)?.let { r -> _state.update { it.copy(provinces = r.value) } }
        }
    }

    fun setCategory(id: String?) {
        _state.update { it.copy(categoryId = id) }
        lastViewport?.let { (n, s, e, w) -> onViewport(n, s, e, w) }
    }

    fun loadCities(province: Area) = viewModelScope.launch {
        _state.update { it.copy(cities = emptyList()) }
        (geography.cities(province.id) as? Outcome.Success)?.let { r -> _state.update { it.copy(cities = r.value) } }
    }

    fun setHere(here: Here?) = _state.update { it.copy(here = here) }

    /** Called when the camera settles; a short pause so panning does not fire a request per frame. */
    fun onViewport(north: Double, south: Double, east: Double, west: Double) {
        lastViewport = doubleArrayOf(north, south, east, west)
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            delay(250)
            _state.update { it.copy(loading = true, tooFarOut = north - south > 6.0) }
            // apps/api refuses an area wider than 22° a side (GEOGRAPHY_QUERY_TOO_LARGE):
            // zoomed far out, ask for the middle of the screen instead of failing.
            val (n, s) = clampSpan(south, north)
            val (e, w) = clampSpan(west, east)
            when (val r = repository.branches(n, s, e, w, _state.value.categoryId)) {
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
            val here = _state.value.here
            val r = repository.preview(branchId, here?.latitude, here?.longitude)
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

    // "Near me": the phone's own position service (not Google's — no Play Services needed), asked for when used.
    var locating by remember { mutableStateOf(false) }
    var locationProblem by remember { mutableStateOf(false) }
    var outsideIran by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var cityOpen by remember { mutableStateOf(false) }
    val goToMe = {
        locating = true
        locationProblem = false
        outsideIran = false
        currentLocation(context) { lat, lng ->
            locating = false
            when {
                lat == null || lng == null -> locationProblem = true
                // Outside Iran: the map stays (the web's message), and no distance is measured from there.
                !insideIran(lat, lng) -> outsideIran = true
                else -> {
                    viewModel.setHere(Here(lat, lng))
                    map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 13.0))
                }
            }
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) goToMe() else locationProblem = true }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        androidx.compose.material3.SmallFloatingActionButton(
            onClick = {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) goToMe() else permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(androidx.compose.material.icons.Icons.Outlined.MyLocation, stringResource(R.string.map_near_me))
        }
        // Category chips, the city chooser and the list of stores in view (the web's map section).
        Column(Modifier.align(Alignment.TopStart).padding(start = 64.dp, top = 12.dp, end = 48.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { cityOpen = true }, label = { Text(stringResource(R.string.map_choose_city)) }, colors = chipColors())
                AssistChip(onClick = { listOpen = true }, label = { Text(pluralStringResource(R.plurals.map_stores_here, state.markers.size, state.markers.size)) }, colors = chipColors())
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val language = LocalConfiguration.current.locales[0].language
                FilterChip(selected = state.categoryId == null, onClick = { viewModel.setCategory(null) }, label = { Text(stringResource(R.string.map_category_all)) }, colors = filterColors())
                state.categories.forEach { c ->
                    FilterChip(selected = state.categoryId == c.id, onClick = { viewModel.setCategory(c.id) }, label = { Text(c.name.forLanguage(language)) }, colors = filterColors())
                }
            }
        }
        if (outsideIran) {
            Text(stringResource(R.string.map_outside_iran), color = AzzaTheme.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center).padding(24.dp).background(AzzaTheme.colors.bg, RoundedCornerShape(8.dp)).padding(8.dp))
        }
        if (locationProblem) {
            Text(stringResource(R.string.map_location_unavailable), color = AzzaTheme.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).background(AzzaTheme.colors.bg, RoundedCornerShape(8.dp)).padding(8.dp))
        }
        if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        state.error?.takeIf { state.selected == null }?.let {
            Text(errorMessage(it), color = AzzaTheme.colors.error, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                .background(AzzaTheme.colors.bg, RoundedCornerShape(8.dp)).padding(8.dp))
        }
        if (listOpen) {
            StoresInView(state, onDismiss = { listOpen = false }) { marker ->
                listOpen = false
                map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(LatLng(marker.latitude, marker.longitude)))
                viewModel.select(marker.branchId)
            }
        }
        if (cityOpen) {
            CityDialog(state, viewModel::loadCities, onDismiss = { cityOpen = false }) { area ->
                cityOpen = false
                val lat = area.latitude
                val lng = area.longitude
                if (lat != null && lng != null) map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 11.5))
            }
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
        p.distanceKm?.let { Text(stringResource(R.string.map_distance, String.format(java.util.Locale.getDefault(), "%.1f", it)), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
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

@Composable
private fun chipColors() = AssistChipDefaults.assistChipColors(containerColor = AzzaTheme.colors.bg)

@Composable
private fun filterColors() = FilterChipDefaults.filterChipColors(containerColor = AzzaTheme.colors.bg)

/** The web's "Stores in this area" list, for the branches the map is showing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoresInView(state: MapUiState, onDismiss: () -> Unit, onPick: (BranchMarker) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            pluralStringResource(R.plurals.map_stores_here, state.markers.size, state.markers.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.tooFarOut -> Text(stringResource(R.string.map_list_zoom_in), color = AzzaTheme.colors.textMuted, modifier = Modifier.padding(16.dp))
            state.markers.isEmpty() -> Text(
                stringResource(if (state.categoryId != null) R.string.map_list_empty_filtered else R.string.map_list_empty),
                color = AzzaTheme.colors.textMuted,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                if (state.markers.size >= 200) {
                    item { Text(stringResource(R.string.map_list_truncated), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
                items(state.markers, key = { it.branchId }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(m) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(url = m.logoUrl, isStore = true, size = 40)
                        Column(Modifier.weight(1f)) {
                            Text(m.storeName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(m.branchName.ifBlank { null }, m.address).joinToString(" · "),
                                color = AzzaTheme.colors.textMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            stringResource(if (m.status == "OPEN") R.string.map_open_now else if (m.status == "CLOSED") R.string.map_closed_now else R.string.map_hours_unknown),
                            color = if (m.status == "OPEN") AzzaTheme.colors.success else AzzaTheme.colors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/** Province, then city: the map moves to the city (the web's map section selectors). */
@Composable
private fun CityDialog(state: MapUiState, onProvince: (Area) -> Unit, onDismiss: () -> Unit, onCity: (Area) -> Unit) {
    var province by remember { mutableStateOf<Area?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_choose_city)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceMenu(stringResource(R.string.map_province), province?.name.orEmpty(), state.provinces.map { it.name }, { name ->
                    state.provinces.firstOrNull { it.name == name }?.let { province = it; onProvince(it) }
                }, Modifier.fillMaxWidth())
                ChoiceMenu(stringResource(R.string.map_city), "", state.cities.map { it.name }, { name ->
                    state.cities.firstOrNull { it.name == name }?.let(onCity)
                }, Modifier.fillMaxWidth())
                // A province on its own is somewhere to go too.
                province?.takeIf { it.latitude != null }?.let { p ->
                    TextButton(onClick = { onCity(p) }) { Text(stringResource(R.string.map_go_to_province, p.name)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_close)) } },
    )
}

/**
 * One position fix from the phone's own location service (network first —
 * fast and enough for "near me" — else GPS); the last known one when that
 * is all there is. [onResult] gets nulls when nothing is available.
 */
@android.annotation.SuppressLint("MissingPermission") // checked by the caller
private fun currentLocation(context: android.content.Context, onResult: (Double?, Double?) -> Unit) {
    val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    val provider = listOf(android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.GPS_PROVIDER)
        .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return onResult(null, null)
    val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        manager.getCurrentLocation(provider, null, androidx.core.content.ContextCompat.getMainExecutor(context)) { fix ->
            val l = fix ?: last
            onResult(l?.latitude, l?.longitude)
        }
    } else {
        onResult(last?.latitude, last?.longitude)
    }
}
