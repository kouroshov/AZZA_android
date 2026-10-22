package com.azza.core.map

import com.azza.core.model.MapPoint
import com.azza.core.model.ParcelDraft
import com.azza.core.model.ParcelMode
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlin.math.cos
import org.junit.Test

/** The drawing tool's live numbers agree with apps/api's (its parcel-geometry spec uses the same field). */
class ParcelGeometryTest {
    private val lat = 36.521
    private val lng = 46.209
    private fun dLat(m: Double) = m / 111_320
    private fun dLng(m: Double) = m / (111_320 * cos(Math.toRadians(lat)))
    private fun rect(w: Double, h: Double) = listOf(
        MapPoint(lng, lat),
        MapPoint(lng + dLng(w), lat),
        MapPoint(lng + dLng(w), lat + dLat(h)),
        MapPoint(lng, lat + dLat(h)),
    )

    @Test fun `measures a 100 m by 50 m field`() {
        assertThat(ringAreaM2(rect(100.0, 50.0))).isWithin(10.0).of(5_000.0)
        assertThat(sideLengthsM(rect(100.0, 50.0)).map { Math.round(it) }).containsExactly(100L, 50L, 100L, 50L).inOrder()
    }

    @Test fun `names what is wrong with a shape`() {
        assertThat(ringProblem(rect(100.0, 50.0).take(2))).isEqualTo(RingProblem.TOO_FEW_VERTICES)
        val (a, b, c, d) = rect(50.0, 50.0)
        assertThat(ringProblem(listOf(a, c, b, d))).isEqualTo(RingProblem.SELF_INTERSECTING)
        assertThat(ringProblem(rect(2.0, 2.0))).isEqualTo(RingProblem.TOO_SMALL)
        assertThat(ringProblem(rect(100.0, 50.0))).isNull()
    }

    @Test fun `a draft is complete only when its mode is finished`() {
        assertThat(ParcelDraft().isComplete()).isTrue()
        assertThat(ParcelDraft(ParcelMode.POINT).isComplete()).isFalse()
        assertThat(ParcelDraft(ParcelMode.POINT, MapPoint(lng, lat)).isComplete()).isTrue()
        assertThat(ParcelDraft(ParcelMode.POLYGON, ring = rect(100.0, 50.0).take(2)).isComplete()).isFalse()
        assertThat(ParcelDraft(ParcelMode.POLYGON, ring = rect(100.0, 50.0)).isComplete()).isTrue()
    }

    @Test fun `formats with the locale's words and digits`() {
        val en = ParcelUnits("m", "km", "m²", "ha")
        assertThat(formatLength(12.34, en, Locale.US)).isEqualTo("12.3 m")
        assertThat(formatLength(1_250.0, en, Locale.US)).isEqualTo("1.25 km")
        assertThat(formatArea(850.0, en, Locale.US)).isEqualTo("850 m²")
        assertThat(formatArea(64_600.0, ParcelUnits("متر", "کیلومتر", "متر مربع", "هکتار"), Locale.forLanguageTag("fa"))).isEqualTo("۶٫۴۶ هکتار")
    }
}
