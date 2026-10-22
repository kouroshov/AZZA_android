package com.azza.feature.map

import com.azza.core.map.currentLocation
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationListener
import android.location.LocationManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.DirectionStep
import com.azza.core.data.Directions
import com.azza.core.data.GeoPoint
import com.azza.core.data.MapRepository
import com.azza.core.data.TravelMode
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** The way to a branch, drawn and followed in the app ([name] is what the screen calls the place). */
@Serializable data class DirectionsRoute(val branchId: String, val name: String)

fun NavGraphBuilder.directionsScreen(onBack: () -> Unit, onOpenInMapsApp: (latitude: Double, longitude: Double, label: String) -> Unit) {
    composable<DirectionsRoute> { DirectionsScreen(onBack, onOpenInMapsApp) }
}

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SOURCE = "azza-route"
private const val ENDS_SOURCE = "azza-route-ends"
private const val ME_SOURCE = "azza-me"
/** After leaving the way, ask for a new one at most this often. */
private const val REROUTE_EVERY_MS = 8_000L

data class DirectionsState(
    val mode: TravelMode = TravelMode.CAR,
    val loading: Boolean = false,
    val directions: Directions? = null,
    val error: AppError? = null,
    /** Where the phone is; null until the first fix. */
    val here: GeoPoint? = null,
    val locating: Boolean = true,
    val noLocation: Boolean = false,
    val following: Boolean = false,
    val progress: Progress? = null,
    val voice: Boolean = false,
)

@HiltViewModel
class DirectionsViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val map: MapRepository) : ViewModel() {
    val route = savedStateHandle.toRoute<DirectionsRoute>()
    private val _state = MutableStateFlow(DirectionsState())
    val state: StateFlow<DirectionsState> = _state.asStateFlow()
    private var follower: RouteFollower? = null
    private var lastRouteAt = 0L

    fun noLocation() = _state.update { it.copy(locating = false, noLocation = true) }

    /** The first fix: ask for the way from here. */
    fun located(here: GeoPoint) {
        _state.update { it.copy(here = here, locating = false, noLocation = false) }
        if (_state.value.directions == null) load(here)
    }

    fun setMode(mode: TravelMode) {
        if (mode == _state.value.mode) return
        _state.update { it.copy(mode = mode) }
        _state.value.here?.let(::load)
    }

    fun retry() = _state.value.here?.let(::load)

    private fun load(from: GeoPoint) {
        lastRouteAt = System.currentTimeMillis()
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = map.directions(route.branchId, from, _state.value.mode)) {
                is Outcome.Success -> {
                    follower = RouteFollower(r.value)
                    _state.update { it.copy(loading = false, directions = r.value, progress = follower?.update(from)) }
                }
                is Outcome.Failure -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun start() = _state.update { it.copy(following = true, progress = it.here?.let { h -> follower?.update(h) }) }

    fun stop() = _state.update { it.copy(following = false) }

    fun toggleVoice() = _state.update { it.copy(voice = !it.voice) }

    /** Every fix while following: progress, and a new way when the phone has left this one. */
    fun moved(here: GeoPoint) {
        val progress = follower?.update(here)
        _state.update { it.copy(here = here, progress = progress) }
        if (progress?.offRoute == true && !_state.value.loading && System.currentTimeMillis() - lastRouteAt > REROUTE_EVERY_MS) load(here)
        if (progress?.arrived == true) _state.update { it.copy(following = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionsScreen(
    onBack: () -> Unit,
    onOpenInMapsApp: (latitude: Double, longitude: Double, label: String) -> Unit,
    viewModel: DirectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val primary = AzzaTheme.colors.primary
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var stepsOpen by remember { mutableStateOf(false) }

    // ---- the phone's position: asked for when the screen opens (that is what it is for)
    val hasPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val locateOnce = {
        currentLocation(context) { lat, lng ->
            if (lat != null && lng != null) viewModel.located(GeoPoint(lat, lng)) else viewModel.noLocation()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locateOnce() else viewModel.noLocation()
    }
    val hasPrecise = { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    // Following the way needs a PRECISE position (GPS); the map's "near me" may have allowed only an
    // approximate one, so ask again here when that is all there is. The phone lets the person choose.
    LaunchedEffect(Unit) {
        if (hasPrecise()) locateOnce()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // While following: a fix every couple of seconds; the screen stays on.
    val view = LocalView.current
    DisposableEffect(state.following) {
        view.keepScreenOn = state.following
        val listener = if (state.following && hasPermission()) startUpdates(context) { viewModel.moved(it) } else null
        onDispose {
            view.keepScreenOn = false
            listener?.let { (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        }
    }

    // ---- voice: the phone's own text-to-speech, in the app's language (silent when it has none)
    val language = LocalConfiguration.current.locales[0]
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var voiceMissing by remember { mutableStateOf(false) }
    DisposableEffect(state.voice) {
        var engine: TextToSpeech? = null
        if (state.voice) {
            engine = TextToSpeech(context) { status ->
                val ok = status == TextToSpeech.SUCCESS &&
                    engine?.setLanguage(language)?.let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED } == true
                voiceMissing = !ok
                tts = if (ok) engine else null
            }
        }
        onDispose {
            engine?.shutdown()
            tts = null
        }
    }
    val directions = state.directions
    val progress = state.progress
    val nextStep = progress?.let { directions?.steps?.getOrNull(it.nextStep) }
    val nextText = nextStep?.let { instruction(it) }
    // Say each maneuver once when it becomes the next one, and once more when it is close.
    var spoken by remember { mutableStateOf(setOf<String>()) }
    val speakNow by rememberUpdatedState(nextText)
    LaunchedEffect(state.following, progress?.nextStep, (progress?.metersToNextStep ?: 0.0) < 120.0) {
        val p = progress ?: return@LaunchedEffect
        val text = speakNow ?: return@LaunchedEffect
        if (!state.following) return@LaunchedEffect
        val key = "${p.nextStep}-${p.metersToNextStep < 120.0}"
        if (key in spoken) return@LaunchedEffect
        spoken = spoken + key
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
    }
    val arrivedText = stringResource(R.string.dir_arrived)
    LaunchedEffect(progress?.arrived) { if (progress?.arrived == true) tts?.speak(arrivedText, TextToSpeech.QUEUE_FLUSH, null, "arrived") }

    // ---- the map
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
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
            m.uiSettings.isRotateGesturesEnabled = false
            m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder().target(LatLng(32.4, 53.4)).zoom(4.5).build()
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                val blue = android.graphics.Color.argb(255, (primary.red * 255).toInt(), (primary.green * 255).toInt(), (primary.blue * 255).toInt())
                style.addSource(GeoJsonSource(ROUTE_SOURCE))
                style.addSource(GeoJsonSource(ENDS_SOURCE))
                style.addSource(GeoJsonSource(ME_SOURCE))
                style.addLayer(
                    LineLayer("azza-route-casing", ROUTE_SOURCE).withProperties(
                        PropertyFactory.lineColor(Color.WHITE), PropertyFactory.lineWidth(9f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                style.addLayer(
                    LineLayer("azza-route-line", ROUTE_SOURCE).withProperties(
                        PropertyFactory.lineColor(blue), PropertyFactory.lineWidth(6f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                style.addLayer(
                    CircleLayer("azza-route-ends", ENDS_SOURCE).withProperties(
                        PropertyFactory.circleRadius(9f), PropertyFactory.circleColor(android.graphics.Color.rgb(229, 57, 53)),
                        PropertyFactory.circleStrokeColor(Color.WHITE), PropertyFactory.circleStrokeWidth(3f),
                    ),
                )
                // "You are here": the phone's position, as a blue dot with a white ring.
                style.addLayer(
                    CircleLayer("azza-me", ME_SOURCE).withProperties(
                        PropertyFactory.circleRadius(8f), PropertyFactory.circleColor(blue),
                        PropertyFactory.circleStrokeColor(Color.WHITE), PropertyFactory.circleStrokeWidth(3f),
                    ),
                )
                styleReady = true
            }
            map = m
        }
    }
    // The route, and the camera framing it (or following the phone).
    LaunchedEffect(directions, styleReady) {
        val m = map ?: return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val d = directions ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(
            Feature.fromGeometry(LineString.fromLngLats(d.line.map { Point.fromLngLat(it.longitude, it.latitude) })),
        )
        style.getSourceAs<GeoJsonSource>(ENDS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(d.destination.longitude, d.destination.latitude))),
        )
        if (!state.following && d.line.size >= 2) {
            val bounds = LatLngBounds.Builder().apply { d.line.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120, 260, 120, 420))
        }
    }
    LaunchedEffect(state.here, state.following, styleReady) {
        val m = map ?: return@LaunchedEffect
        val here = state.here ?: return@LaunchedEffect
        m.style?.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(here.longitude, here.latitude)))
        if (state.following) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(here.latitude, here.longitude), 17.0), 600)
    }

    if (stepsOpen && directions != null) {
        ModalBottomSheet(onDismissRequest = { stepsOpen = false }) {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                itemsIndexed(directions.steps) { i, step ->
                    Row(
                        Modifier.fillMaxWidth().background(if (i == progress?.nextStep && state.following) AzzaTheme.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(iconOf(maneuverOf(step)), null)
                        Text(instruction(step), modifier = Modifier.weight(1f))
                        if (step.distanceMeters > 0) Text(distanceText(step.distanceMeters), color = AzzaTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = AzzaTheme.colors.navDivider)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Top: back, and while following, the next maneuver in large type.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.clip(RoundedCornerShape(50)).background(AzzaTheme.colors.bg)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.dir_back))
                }
                Text(
                    viewModel.route.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(12.dp)).background(AzzaTheme.colors.bg).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (state.following && progress?.offRoute == true) {
                // Left the way: say so, instead of distances measured to a line no longer followed.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AzzaTheme.colors.warning).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = AzzaTheme.colors.bg)
                    Text(stringResource(R.string.dir_rerouting), color = AzzaTheme.colors.bg, fontWeight = FontWeight.Bold)
                }
            } else if (state.following && nextStep != null && progress != null && !progress.arrived) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AzzaTheme.colors.primary).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(iconOf(maneuverOf(nextStep)), null, tint = AzzaTheme.colors.textOnPrimary, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(distanceText(progress.metersToNextStep), color = AzzaTheme.colors.textOnPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(nextText.orEmpty(), color = AzzaTheme.colors.textOnPrimary)
                    }
                }
            }
        }

        // Bottom: how to travel, how far and how long, and the actions.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .clip(RoundedCornerShape(16.dp)).background(AzzaTheme.colors.navPopover).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.following) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelMode.entries.forEach { m ->
                        FilterChip(
                            selected = state.mode == m,
                            onClick = { viewModel.setMode(m) },
                            label = { Text(stringResource(m.label())) },
                            leadingIcon = { Icon(m.icon(), null, Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            when {
                state.locating -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.dir_locating))
                }
                state.noLocation -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.dir_no_location))
                    TextButton(onClick = {
                        if (hasPermission()) locateOnce()
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(stringResource(R.string.dir_retry)) }
                }
                state.loading && directions == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.dir_finding))
                }
                state.error != null && directions == null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(directionsError(state.error!!), color = AzzaTheme.colors.error)
                    TextButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.dir_retry)) }
                }
                progress?.arrived == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Flag, null, tint = AzzaTheme.colors.success)
                    Text(arrivedText, style = MaterialTheme.typography.titleMedium)
                }
                directions != null -> {
                    val onTrack = state.following && progress != null && !progress.offRoute
                    val meters = if (onTrack) progress!!.metersLeft else directions.distanceMeters
                    val seconds = if (onTrack) progress!!.secondsLeft else directions.durationSeconds
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(durationText(seconds), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AzzaTheme.colors.success)
                        Text(distanceText(meters), color = AzzaTheme.colors.textSecondary)
                        if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (!state.locating && !state.noLocation && !hasPrecise()) {
                Text(stringResource(R.string.dir_approximate), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.warning)
            }
            if (voiceMissing && state.voice) Text(stringResource(R.string.dir_voice_missing), style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (directions != null && progress?.arrived != true) {
                    if (state.following) {
                        OutlinedButton(onClick = viewModel::stop) { Text(stringResource(R.string.dir_stop)) }
                    } else {
                        Button(onClick = viewModel::start, enabled = state.here != null) {
                            Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.dir_start), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    IconButton(onClick = { stepsOpen = true }) { Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.dir_steps)) }
                    IconButton(onClick = viewModel::toggleVoice) {
                        Icon(if (state.voice) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff, stringResource(if (state.voice) R.string.dir_voice_off else R.string.dir_voice_on))
                    }
                    IconButton(onClick = {
                        state.here?.let { h -> map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(h.latitude, h.longitude), 16.0)) }
                    }) { Icon(Icons.Filled.MyLocation, stringResource(R.string.dir_my_position)) }
                }
                Box(Modifier.weight(1f))
                // Another app on the phone may navigate too (its own voice, its own maps).
                directions?.destination?.let { d ->
                    TextButton(onClick = { onOpenInMapsApp(d.latitude, d.longitude, viewModel.route.name) }) { Text(stringResource(R.string.dir_other_app), maxLines = 1) }
                }
            }
        }
    }
}

/** A fix every ~2 s / 3 m from the best provider there is; returns the listener to remove later. */
@SuppressLint("MissingPermission") // checked by the caller
private fun startUpdates(context: Context, onFix: (GeoPoint) -> Unit): LocationListener? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { l -> onFix(GeoPoint(l.latitude, l.longitude)) }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return null
    providers.forEach { runCatching { manager.requestLocationUpdates(it, 2_000L, 3f, listener, android.os.Looper.getMainLooper()) } }
    return listener
}

private fun TravelMode.label() = when (this) {
    TravelMode.CAR -> R.string.dir_mode_car
    TravelMode.FOOT -> R.string.dir_mode_foot
    TravelMode.BIKE -> R.string.dir_mode_bike
}

private fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.CAR -> Icons.Filled.DirectionsCar
    TravelMode.FOOT -> Icons.Filled.DirectionsWalk
    TravelMode.BIKE -> Icons.Filled.DirectionsBike
}

private fun iconOf(m: Maneuver): ImageVector = when (m) {
    Maneuver.DEPART -> Icons.Filled.Navigation
    Maneuver.ARRIVE, Maneuver.ARRIVE_LEFT, Maneuver.ARRIVE_RIGHT -> Icons.Filled.Flag
    Maneuver.STRAIGHT -> Icons.Filled.Straight
    Maneuver.LEFT -> Icons.Filled.TurnLeft
    Maneuver.RIGHT -> Icons.Filled.TurnRight
    Maneuver.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    Maneuver.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    Maneuver.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    Maneuver.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    Maneuver.UTURN -> Icons.Filled.UTurnLeft
    Maneuver.KEEP_LEFT -> Icons.Filled.ForkLeft
    Maneuver.KEEP_RIGHT -> Icons.Filled.ForkRight
    Maneuver.ROUNDABOUT -> Icons.Filled.RoundaboutRight
    Maneuver.MERGE -> Icons.Filled.Merge
    Maneuver.RAMP -> Icons.Filled.RampRight
}

/** One step in words: "Turn left onto Azadi St", "At the roundabout, take the 2nd exit". */
@Composable
private fun instruction(step: DirectionStep): String {
    val road = step.road.takeIf { it.isNotBlank() }
    val res = LocalContext.current.resources
    fun pick(withRoad: Int, plain: Int) = if (road != null) res.getString(withRoad, road) else res.getString(plain)
    return when (maneuverOf(step)) {
        Maneuver.DEPART -> pick(R.string.dir_depart_on, R.string.dir_depart)
        Maneuver.ARRIVE -> stringResource(R.string.dir_arrive)
        Maneuver.ARRIVE_LEFT -> stringResource(R.string.dir_arrive_left)
        Maneuver.ARRIVE_RIGHT -> stringResource(R.string.dir_arrive_right)
        Maneuver.STRAIGHT -> pick(R.string.dir_straight_on, R.string.dir_straight)
        Maneuver.LEFT -> pick(R.string.dir_left_on, R.string.dir_left)
        Maneuver.RIGHT -> pick(R.string.dir_right_on, R.string.dir_right)
        Maneuver.SLIGHT_LEFT -> pick(R.string.dir_slight_left_on, R.string.dir_slight_left)
        Maneuver.SLIGHT_RIGHT -> pick(R.string.dir_slight_right_on, R.string.dir_slight_right)
        Maneuver.SHARP_LEFT -> pick(R.string.dir_sharp_left_on, R.string.dir_sharp_left)
        Maneuver.SHARP_RIGHT -> pick(R.string.dir_sharp_right_on, R.string.dir_sharp_right)
        Maneuver.UTURN -> pick(R.string.dir_uturn_on, R.string.dir_uturn)
        Maneuver.KEEP_LEFT -> pick(R.string.dir_keep_left_on, R.string.dir_keep_left)
        Maneuver.KEEP_RIGHT -> pick(R.string.dir_keep_right_on, R.string.dir_keep_right)
        Maneuver.MERGE -> pick(R.string.dir_merge_on, R.string.dir_merge)
        Maneuver.RAMP -> pick(R.string.dir_ramp_on, R.string.dir_ramp)
        Maneuver.ROUNDABOUT -> step.exit?.let { exit ->
            if (road != null) res.getString(R.string.dir_roundabout_exit_on, exit, road) else res.getString(R.string.dir_roundabout_exit, exit)
        } ?: pick(R.string.dir_roundabout_on, R.string.dir_roundabout)
    }
}

@Composable
private fun distanceText(meters: Double): String =
    if (meters < 1000) stringResource(R.string.dir_meters, (Math.round(meters / 10.0) * 10).toInt())
    else stringResource(R.string.dir_km, String.format(Locale.getDefault(), "%.1f", meters / 1000))

@Composable
private fun durationText(seconds: Double): String {
    val minutes = Math.max(1, Math.round(seconds / 60.0).toInt())
    return if (minutes < 60) stringResource(R.string.dir_minutes, minutes) else stringResource(R.string.dir_hours_minutes, minutes / 60, minutes % 60)
}

@Composable
private fun directionsError(error: AppError): String = when ((error as? AppError.Api)?.code) {
    "DIRECTIONS_NOT_FOUND" -> stringResource(R.string.dir_error_no_way)
    "DIRECTIONS_TOO_FAR" -> stringResource(R.string.dir_error_too_far)
    "DIRECTIONS_UNAVAILABLE" -> stringResource(R.string.dir_error_unavailable)
    "BRANCH_NOT_FOUND" -> stringResource(R.string.dir_error_no_branch)
    else -> errorMessage(error)
}
