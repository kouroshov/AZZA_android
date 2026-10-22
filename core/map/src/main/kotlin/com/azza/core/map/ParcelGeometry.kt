package com.azza.core.map

import com.azza.core.model.MapPoint
import com.azza.core.model.ParcelDraft
import com.azza.core.model.ParcelMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The drawing tool's live measurements — the same formulas as apps/api's
// parcels/parcel-geometry.ts, which measures again on save (its numbers are
// the ones stored and shown).

private const val EARTH_RADIUS_M = 6_378_137.0
private const val RAD = Math.PI / 180

object ParcelLimits {
    const val MIN_VERTICES = 3
    const val MAX_VERTICES = 100
    const val MIN_AREA_M2 = 10.0
    const val MAX_AREA_M2 = 100_000_000.0
    const val MIN_EDGE_M = 0.05
}

fun distanceM(a: MapPoint, b: MapPoint): Double {
    val dLat = (b.lat - a.lat) * RAD
    val dLng = (b.lng - a.lng) * RAD
    val h = sin(dLat / 2).let { it * it } + cos(a.lat * RAD) * cos(b.lat * RAD) * sin(dLng / 2).let { it * it }
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
}

/** The area on the sphere (spherical excess), in m². */
fun ringAreaM2(ring: List<MapPoint>): Double {
    var total = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        total += (b.lng - a.lng) * RAD * (2 + sin(a.lat * RAD) + sin(b.lat * RAD))
    }
    return abs(total * EARTH_RADIUS_M * EARTH_RADIUS_M / 2)
}

/** Side i runs from vertex i to vertex i+1 (the last closes the shape); two points are one side. */
fun sideLengthsM(ring: List<MapPoint>): List<Double> = when {
    ring.size < 2 -> emptyList()
    ring.size == 2 -> listOf(distanceM(ring[0], ring[1]))
    else -> ring.indices.map { distanceM(ring[it], ring[(it + 1) % ring.size]) }
}

private fun toMetres(ring: List<MapPoint>): List<Pair<Double, Double>> {
    val o = ring.first()
    val k = cos(o.lat * RAD)
    return ring.map { (it.lng - o.lng) * RAD * EARTH_RADIUS_M * k to (it.lat - o.lat) * RAD * EARTH_RADIUS_M }
}

private fun crosses(p1: Pair<Double, Double>, p2: Pair<Double, Double>, p3: Pair<Double, Double>, p4: Pair<Double, Double>): Boolean {
    fun cross(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>) =
        (b.first - a.first) * (c.second - a.second) - (b.second - a.second) * (c.first - a.first)
    val d1 = cross(p3, p4, p1)
    val d2 = cross(p3, p4, p2)
    val d3 = cross(p1, p2, p3)
    val d4 = cross(p1, p2, p4)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

fun selfIntersects(ring: List<MapPoint>): Boolean {
    if (ring.size < 4) return false
    val p = toMetres(ring)
    val n = p.size
    for (i in 0 until n) for (j in i + 1 until n) {
        if (j == i + 1 || (i == 0 && j == n - 1)) continue
        if (crosses(p[i], p[(i + 1) % n], p[j], p[(j + 1) % n])) return true
    }
    return false
}

enum class RingProblem { TOO_FEW_VERTICES, TOO_MANY_VERTICES, REPEATED_VERTEX, SELF_INTERSECTING, TOO_SMALL, TOO_LARGE }

/** Why the drawn shape cannot be saved yet, or null when it can. */
fun ringProblem(ring: List<MapPoint>): RingProblem? = when {
    ring.size < ParcelLimits.MIN_VERTICES -> RingProblem.TOO_FEW_VERTICES
    ring.size > ParcelLimits.MAX_VERTICES -> RingProblem.TOO_MANY_VERTICES
    sideLengthsM(ring).any { it < ParcelLimits.MIN_EDGE_M } -> RingProblem.REPEATED_VERTEX
    selfIntersects(ring) -> RingProblem.SELF_INTERSECTING
    ringAreaM2(ring) < ParcelLimits.MIN_AREA_M2 -> RingProblem.TOO_SMALL
    ringAreaM2(ring) > ParcelLimits.MAX_AREA_M2 -> RingProblem.TOO_LARGE
    else -> null
}

/** The chosen mode is finished (or nothing was chosen). */
fun ParcelDraft.isComplete(): Boolean = when (mode) {
    ParcelMode.NONE -> true
    ParcelMode.POINT -> point != null
    ParcelMode.POLYGON -> ringProblem(ring) == null
}

/** Unit words from the resources ("m", "متر", …). */
data class ParcelUnits(val m: String, val km: String, val m2: String, val ha: String)

fun formatLength(m: Double, units: ParcelUnits, locale: Locale = Locale.getDefault()): String {
    val nf = NumberFormat.getNumberInstance(locale)
    return if (m >= 1000) {
        nf.maximumFractionDigits = 2
        "${nf.format(m / 1000)} ${units.km}"
    } else {
        nf.maximumFractionDigits = if (m < 100) 1 else 0
        "${nf.format(m)} ${units.m}"
    }
}

/** Hectares past 10,000 m² (land is priced by the hectare). */
fun formatArea(m2: Double, units: ParcelUnits, locale: Locale = Locale.getDefault()): String {
    val nf = NumberFormat.getNumberInstance(locale)
    return if (m2 >= 10_000) {
        nf.maximumFractionDigits = 2
        "${nf.format(m2 / 10_000)} ${units.ha}"
    } else {
        nf.maximumFractionDigits = 0
        "${nf.format(m2)} ${units.m2}"
    }
}
