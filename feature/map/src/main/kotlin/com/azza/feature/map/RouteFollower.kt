package com.azza.feature.map

import com.azza.core.data.DirectionStep
import com.azza.core.data.Directions
import com.azza.core.data.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Meters between two points (great circle). */
internal fun metersBetween(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

/** The nearest point of [line] to [p]: its distance in meters and how far along the line it lies. */
internal fun nearestOnLine(p: GeoPoint, line: List<GeoPoint>): Pair<Double, Double> {
    if (line.isEmpty()) return Double.MAX_VALUE to 0.0
    if (line.size == 1) return metersBetween(p, line[0]) to 0.0
    // A local flat projection around p: plenty for the few hundred meters that matter here.
    val kx = 111_320.0 * cos(Math.toRadians(p.latitude))
    val ky = 110_540.0
    var best = Double.MAX_VALUE
    var bestAlong = 0.0
    var walked = 0.0
    for (i in 0 until line.size - 1) {
        val a = line[i]
        val b = line[i + 1]
        val ax = (a.longitude - p.longitude) * kx
        val ay = (a.latitude - p.latitude) * ky
        val bx = (b.longitude - p.longitude) * kx
        val by = (b.latitude - p.latitude) * ky
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val d = sqrt(cx * cx + cy * cy)
        val segment = sqrt(len2)
        if (d < best) {
            best = d
            bestAlong = walked + t * segment
        }
        walked += segment
    }
    return best to bestAlong
}

/** Where the traveller is on the way, recomputed at every position fix. */
data class Progress(
    /** The step whose maneuver comes next. */
    val nextStep: Int,
    val metersToNextStep: Double,
    val metersLeft: Double,
    val secondsLeft: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
)

/**
 * Follows a route as the phone moves: which maneuver is next and how far,
 * what is left, whether the traveller has left the way (then the way is
 * asked for again) or arrived. Pure, so it is tested without a phone.
 */
class RouteFollower(private val directions: Directions) {
    private val totalMeters = lengthOf(directions.line)
    private var current = 1.coerceAtMost(directions.steps.lastIndex.coerceAtLeast(0))
    /** How far along the line each maneuver is, to tell which ones are behind. */
    private val stepAlong = directions.steps.map { nearestOnLine(it.at, directions.line).second }

    fun update(at: GeoPoint): Progress {
        val steps = directions.steps
        val (offBy, along) = nearestOnLine(at, directions.line)
        // A maneuver is done when the traveller is at it, or (between two fixes far apart) already past it.
        while (current < steps.lastIndex &&
            (metersBetween(at, steps[current].at) < STEP_REACHED_M || (offBy < OFF_ROUTE_M && stepAlong[current] < along - STEP_REACHED_M))
        ) current++
        val left = (totalMeters - along).coerceAtLeast(0.0)
        val arrived = metersBetween(at, directions.destination) < ARRIVED_M
        return Progress(
            nextStep = current,
            metersToNextStep = steps.getOrNull(current)?.let { metersBetween(at, it.at) } ?: left,
            metersLeft = left,
            secondsLeft = if (totalMeters > 0) directions.durationSeconds * left / totalMeters else 0.0,
            offRoute = !arrived && offBy > OFF_ROUTE_M,
            arrived = arrived,
        )
    }

    companion object {
        const val STEP_REACHED_M = 25.0
        const val ARRIVED_M = 30.0
        const val OFF_ROUTE_M = 60.0

        fun lengthOf(line: List<GeoPoint>): Double = line.zipWithNext { a, b -> metersBetween(a, b) }.sum()
    }
}

/** What a maneuver means, before it is put into words in the app's language. */
enum class Maneuver {
    DEPART, ARRIVE, ARRIVE_LEFT, ARRIVE_RIGHT,
    STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT, UTURN,
    KEEP_LEFT, KEEP_RIGHT, ROUNDABOUT, MERGE, RAMP,
}

/** OSRM's type + modifier → one [Maneuver] (left and right are the street's, in every language). */
fun maneuverOf(step: DirectionStep): Maneuver {
    val m = step.modifier
    fun turn() = when (m) {
        "left" -> Maneuver.LEFT
        "right" -> Maneuver.RIGHT
        "slight left" -> Maneuver.SLIGHT_LEFT
        "slight right" -> Maneuver.SLIGHT_RIGHT
        "sharp left" -> Maneuver.SHARP_LEFT
        "sharp right" -> Maneuver.SHARP_RIGHT
        "uturn" -> Maneuver.UTURN
        else -> Maneuver.STRAIGHT
    }
    return when (step.type) {
        "depart" -> Maneuver.DEPART
        "arrive" -> when (m) {
            "left", "slight left", "sharp left" -> Maneuver.ARRIVE_LEFT
            "right", "slight right", "sharp right" -> Maneuver.ARRIVE_RIGHT
            else -> Maneuver.ARRIVE
        }
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> Maneuver.ROUNDABOUT
        "fork" -> if (m?.contains("left") == true) Maneuver.KEEP_LEFT else if (m?.contains("right") == true) Maneuver.KEEP_RIGHT else Maneuver.STRAIGHT
        "merge" -> Maneuver.MERGE
        "on ramp", "off ramp" -> Maneuver.RAMP
        // "new name", "continue", "end of road", "turn", "notification": the modifier says it.
        else -> turn()
    }
}
