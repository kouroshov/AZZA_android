package com.azza.feature.map

import com.azza.core.data.DirectionStep
import com.azza.core.data.Directions
import com.azza.core.data.GeoPoint
import com.azza.core.data.TravelMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteFollowerTest {
    // A straight street going east for ~900 m, then a left turn north for ~550 m (Tehran).
    private val start = GeoPoint(35.70, 51.40)
    private val corner = GeoPoint(35.70, 51.41)
    private val end = GeoPoint(35.705, 51.41)

    private fun step(type: String, modifier: String?, at: GeoPoint) = DirectionStep(type, modifier, "Azadi", null, 0.0, 0.0, at)

    private val directions = Directions(
        mode = TravelMode.CAR,
        distanceMeters = 1460.0,
        durationSeconds = 146.0,
        line = listOf(start, corner, end),
        steps = listOf(step("depart", null, start), step("turn", "left", corner), step("arrive", "right", end)),
        destination = end,
    )

    @Test
    fun `measures a street`() {
        assertThat(metersBetween(start, corner)).isWithin(15.0).of(903.0)
        assertThat(RouteFollower.lengthOf(directions.line)).isWithin(20.0).of(1459.0)
    }

    @Test
    fun `at the start the turn is next, the whole way ahead`() {
        val p = RouteFollower(directions).update(start)
        assertThat(p.nextStep).isEqualTo(1)
        assertThat(p.metersToNextStep).isWithin(15.0).of(903.0)
        assertThat(p.metersLeft).isWithin(20.0).of(1459.0)
        assertThat(p.offRoute).isFalse()
        assertThat(p.arrived).isFalse()
    }

    @Test
    fun `passing the corner moves on to the arrival, and time left shrinks with the way`() {
        val f = RouteFollower(directions)
        f.update(start)
        val p = f.update(GeoPoint(35.7001, 51.41))
        assertThat(p.nextStep).isEqualTo(2)
        assertThat(p.metersLeft).isLessThan(560.0)
        assertThat(p.secondsLeft).isLessThan(60.0)
    }

    @Test
    fun `a turn passed between two far-apart fixes is not still the next one`() {
        val f = RouteFollower(directions)
        f.update(start)
        // 200 m up the second street, never within 25 m of the corner itself.
        val p = f.update(GeoPoint(35.7018, 51.41))
        assertThat(p.nextStep).isEqualTo(2)
    }

    @Test
    fun `a street away from the way is off route`() {
        val p = RouteFollower(directions).update(GeoPoint(35.6985, 51.405))
        assertThat(p.offRoute).isTrue()
    }

    @Test
    fun `near the door is arrived, and arrived is never off route`() {
        val p = RouteFollower(directions).update(GeoPoint(35.7051, 51.4101))
        assertThat(p.arrived).isTrue()
        assertThat(p.offRoute).isFalse()
    }

    @Test
    fun `reads OSRM maneuvers`() {
        assertThat(maneuverOf(step("depart", "right", start))).isEqualTo(Maneuver.DEPART)
        assertThat(maneuverOf(step("turn", "sharp left", start))).isEqualTo(Maneuver.SHARP_LEFT)
        assertThat(maneuverOf(step("continue", "uturn", start))).isEqualTo(Maneuver.UTURN)
        assertThat(maneuverOf(step("new name", "straight", start))).isEqualTo(Maneuver.STRAIGHT)
        assertThat(maneuverOf(step("roundabout", "right", start))).isEqualTo(Maneuver.ROUNDABOUT)
        assertThat(maneuverOf(step("fork", "slight left", start))).isEqualTo(Maneuver.KEEP_LEFT)
        assertThat(maneuverOf(step("arrive", "left", start))).isEqualTo(Maneuver.ARRIVE_LEFT)
        assertThat(maneuverOf(step("end of road", "right", start))).isEqualTo(Maneuver.RIGHT)
    }
}
