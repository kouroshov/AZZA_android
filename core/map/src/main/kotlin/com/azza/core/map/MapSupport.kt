package com.azza.core.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.azza.core.model.ParcelDeal
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView

/** Iran's box: the camera never leaves it. */
val IRAN_BOUNDS: LatLngBounds = LatLngBounds.from(39.9, 63.5, 24.9, 43.9)

fun insideIran(lat: Double, lng: Double) = lat in 24.8..40.0 && lng in 43.9..63.5

/**
 * The colour of each deal on the map (product decision, 2026-10-22) — the
 * website's DEAL_COLOURS. Never the only signal: every card names the deal.
 */
fun dealColor(deal: ParcelDeal?): Color = when (deal) {
    ParcelDeal.SALE -> Color(0xFFDC2626)
    ParcelDeal.RENT -> Color(0xFF2563EB)
    ParcelDeal.SHORT_TERM -> Color(0xFF0891B2)
    ParcelDeal.PARTNERSHIP -> Color(0xFF16A34A)
    ParcelDeal.PRESALE -> Color(0xFFEA580C)
    ParcelDeal.MIXED, null -> Color(0xFF6B7280)
}

fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** A MapView that follows the screen's lifecycle, made once per composition. */
@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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
        // A new observer is brought up to the current state at once (ON_START,
        // ON_RESUME), so a map that appears in a screen already showing starts too.
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/**
 * One position fix from the phone's own location service (no Play
 * Services): every enabled provider (GPS and network) is asked at once and
 * the first answer wins; after [timeoutMs] the freshest last-known position
 * is used instead. [onResult] is called exactly once, with nulls when there
 * is nothing at all.
 */
@SuppressLint("MissingPermission") // checked by the caller
fun currentLocation(context: Context, timeoutMs: Long = 12_000L, onResult: (Double?, Double?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return onResult(null, null)
    val main = Handler(Looper.getMainLooper())
    var done = false
    val finish: (Location?) -> Unit = { l ->
        if (!done) {
            done = true
            onResult(l?.latitude, l?.longitude)
        }
    }
    val lastKnown = {
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }
    if (Build.VERSION.SDK_INT >= 30) {
        val executor = ContextCompat.getMainExecutor(context)
        var unanswered = providers.size
        providers.forEach { provider ->
            runCatching {
                manager.getCurrentLocation(provider, null, executor) { fix ->
                    unanswered--
                    if (fix != null) finish(fix) else if (unanswered == 0) finish(lastKnown())
                }
            }.onFailure { unanswered-- }
        }
        main.postDelayed({ finish(lastKnown()) }, timeoutMs)
    } else {
        finish(lastKnown())
    }
}
