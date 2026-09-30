package com.sza.fastmediasorter.core.letterbox

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Rung 1 of the letterbox-fill conformance ladder: [LetterboxFillMath] reproduces every value of the
 * catalog's vectors - integers and strings exactly, reals to 1e-4. The provenance check is named to
 * run first, so a stale vendored copy fails before any value comparison can mislead.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LetterboxFillVectorsTest {

    private val root: JsonObject by lazy {
        InputStreamReader(resource(VECTORS), Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    }
    private val bars: JsonObject get() = root.getAsJsonObject("bars")
    private val halo: JsonObject get() = root.getAsJsonObject("halo")

    @Test
    fun `a00 the vendored vectors match their provenance record`() {
        val recorded = String(resource(PROVENANCE).readBytes(), Charsets.UTF_8).lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size == PROVENANCE_ROW_FIELDS && it[0] == "sha256" }
            .associate { it[1] to it[2].lowercase() }
        assertEquals(setOf(VECTORS_NAME), recorded.keys)
        val digest = MessageDigest.getInstance("SHA-256").digest(resource(VECTORS).readBytes())
        val actual = digest.joinToString("") { "%02x".format(it) }
        assertEquals(
            "vectors differ from PROVENANCE.txt - re-vendor them from the catalog",
            recorded[VECTORS_NAME],
            actual
        )
    }

    @Test
    fun `b01 bars constants`() {
        val c = bars.getAsJsonObject("constants")
        assertEquals(c["aspectDecimals"].asInt, LetterboxFillMath.ASPECT_DECIMALS)
        assertEquals(c["edgeInset"].asInt, LetterboxFillMath.EDGE_INSET)
        assertEquals(c["edgeOverlap"].asInt, LetterboxFillMath.EDGE_OVERLAP)
        assertEquals(c["seamBand"].asInt, LetterboxFillMath.SEAM_BAND)
        assertEquals(c["sampleStep"].asInt, LetterboxFillMath.SAMPLE_STEP)
        assertEquals(c["trimPercent"].asInt, LetterboxFillMath.TRIM_PERCENT)
        assertEquals(c["deviationPercent"].asInt, LetterboxFillMath.DEVIATION_PERCENT)
        assertEquals(c["spreadThreshold"].asDouble, LetterboxFillMath.spreadThreshold(), TOLERANCE)
        assertEquals(c["smoothFactor"].asDouble, LetterboxFillMath.SMOOTH_FACTOR, TOLERANCE)
    }

    @Test
    fun `b02 image rect and axis`() {
        for (case in bars.getAsJsonArray("imageRect").map { it.asJsonObject }) {
            val image = ints(case.getAsJsonArray("image"))
            val surface = ints(case.getAsJsonArray("surface"))
            val label = "image ${image.toList()} in ${surface.toList()}"
            val axis = LetterboxFillMath.barsAxis(image[0], image[1], surface[0], surface[1])
            val expectedAxis = case["axis"]
            if (expectedAxis == null || expectedAxis.isJsonNull) {
                assertNull(label, axis)
            } else {
                assertEquals(label, expectedAxis.asString, axis?.wireName)
            }
            val rect = LetterboxFillMath.imageRect(image[0], image[1], surface[0], surface[1])
            assertArrayEquals(
                label,
                ints(case.getAsJsonArray("rect")),
                intArrayOf(rect.left, rect.top, rect.width, rect.height)
            )
        }
    }

    @Test
    fun `b03 edge index and stepped samples`() {
        for (case in bars.getAsJsonArray("edgeIndex").map { it.asJsonObject }) {
            val size = case["displayed"].asInt
            assertEquals("near of $size", case["near"].asInt, LetterboxFillMath.edgeIndex(size, farEdge = false))
            assertEquals("far of $size", case["far"].asInt, LetterboxFillMath.edgeIndex(size, farEdge = true))
        }
        for (case in bars.getAsJsonArray("steppedIndices").map { it.asJsonObject }) {
            val count = case["count"].asInt
            assertArrayEquals(
                "count $count",
                ints(case.getAsJsonArray("indices")),
                LetterboxFillMath.steppedIndices(count)
            )
        }
    }

    @Test
    fun `b04 band modes and uniform colours`() {
        for (case in bars.getAsJsonArray("modes").map { it.asJsonObject }) {
            val name = case["name"].asString
            val samples = colors(case.getAsJsonArray("samples"))
            assertEquals(name, case["spread"].asDouble, LetterboxFillMath.edgeSpread(samples).toDouble(), TOLERANCE)
            assertEquals(name, case["mode"].asString, LetterboxFillMath.barMode(samples).wireName)
            assertArrayEquals(
                name,
                ints(case.getAsJsonArray("uniformColor")),
                channels(LetterboxFillMath.trimmedMeanColor(samples))
            )
        }
    }

    @Test
    fun `b05 smoothing radius and box filter`() {
        for (case in bars.getAsJsonArray("smoothRadius").map { it.asJsonObject }) {
            val count = case["count"].asInt
            assertEquals("count $count", case["radius"].asInt, LetterboxFillMath.smoothRadius(count))
        }
        val smoothed = bars.getAsJsonObject("smoothed")
        val output = LetterboxFillMath.smoothedColors(
            colors(smoothed.getAsJsonArray("input")),
            smoothed["radius"].asInt
        )
        val expected = colors(smoothed.getAsJsonArray("output"))
        assertArrayEquals(expected, output)
    }

    @Test
    fun `b06 bands and seams`() {
        for (case in bars.getAsJsonArray("bands").map { it.asJsonObject }) {
            val surface = case["surface"].asInt
            val start = case["imageStart"].asInt
            val length = case["imageLength"].asInt
            val label = "surface $surface image $start+$length"
            val bands = LetterboxFillMath.barBands(surface, start, length)
            val expected = case.getAsJsonObject("bands")
            assertSpan(label, expected.getAsJsonArray("nearBar"), bands.nearBar)
            assertSpan(label, expected.getAsJsonArray("farBar"), bands.farBar)
            assertSpan(label, expected.getAsJsonArray("nearSeam"), bands.nearSeam)
            assertSpan(label, expected.getAsJsonArray("farSeam"), bands.farSeam)
        }
    }

    @Test
    fun `h01 halo constants and durations`() {
        val c = halo.getAsJsonObject("constants")
        assertEquals(c["falloffGamma"].asDouble, LetterboxFillMath.FALLOFF_GAMMA, TOLERANCE)
        assertEquals(c["blendStops"].asInt, LetterboxFillMath.BLEND_STOPS)
        assertEquals(c["durationSlowMs"].asDouble, LetterboxFillMath.DURATION_SLOW_MS, TOLERANCE)
        assertEquals(c["frameIntervalMs"].asLong, LetterboxFillMath.FRAME_INTERVAL_MS)
        val durations = halo.getAsJsonObject("durationsMs")
        val keys =
            mapOf("slow" to "slow", "medium" to "medium", "fast" to "fast", "missing" to "", "unknown" to "turbo")
        for ((field, key) in keys) {
            assertEquals(field, durations[field].asDouble, LetterboxFillMath.haloDurationMs(key), TOLERANCE)
        }
        assertEquals(durations["missing"].asDouble, LetterboxFillMath.haloDurationMs(null), TOLERANCE)
    }

    @Test
    fun `h02 easing`() {
        for (case in halo.getAsJsonArray("ease").map { it.asJsonObject }) {
            val p = case["progress"].asDouble
            assertEquals("progress $p", case["eased"].asDouble, LetterboxFillMath.haloEase(p), TOLERANCE)
        }
    }

    @Test
    fun `h03 stop table and its error`() {
        val stops = halo.getAsJsonArray("stops").map { it.asJsonArray }
        assertEquals(stops.size, LetterboxFillMath.BLEND_STOPS)
        stops.forEachIndexed { j, stop ->
            assertEquals("stop $j position", stop[0].asDouble, LetterboxFillMath.haloStopPositions[j], TOLERANCE)
            assertEquals("stop $j alpha", stop[1].asInt, LetterboxFillMath.haloStopAlphas[j])
        }
        assertEquals(
            halo["stopApproximationMaxError"].asDouble,
            LetterboxFillMath.stopApproximationMaxError(),
            TOLERANCE,
        )
    }

    @Test
    fun `h04 reach and alpha rows`() {
        for (case in halo.getAsJsonArray("reach").map { it.asJsonObject }) {
            val e = case["eased"].asDouble
            assertEquals("eased $e", case["reach"].asInt, LetterboxFillMath.haloReach(case["barLength"].asInt, e))
        }
        for (case in halo.getAsJsonArray("alphaRows").map { it.asJsonObject }) {
            val length = case["barLength"].asInt
            val e = case["eased"].asDouble
            val expected = case.getAsJsonArray("alpha").map { it.asDouble }.toDoubleArray()
            assertArrayEquals("L=$length e=$e", expected, LetterboxFillMath.haloAlphaRow(length, e), TOLERANCE)
        }
    }

    @Test
    fun `h05 underlay opacity and frame schedule`() {
        for (case in halo.getAsJsonArray("underlayOpacity").map { it.asJsonObject }) {
            val e = case["eased"].asDouble
            assertEquals("eased $e", case["opacity"].asDouble, LetterboxFillMath.underlayOpacity(e), TOLERANCE)
        }
        val schedule = halo.getAsJsonObject("frameSchedule")
        for (speed in listOf("slow", "medium", "fast")) {
            val expected = schedule.getAsJsonArray(speed).map { it.asDouble }.toDoubleArray()
            val actual = LetterboxFillMath.frameSchedule(LetterboxFillMath.haloDurationMs(speed))
            assertArrayEquals(speed, expected, actual, TOLERANCE)
        }
    }

    private fun assertSpan(label: String, expected: JsonArray, actual: LetterboxFillMath.Span) {
        assertArrayEquals(label, ints(expected), intArrayOf(actual.start, actual.length))
    }

    private fun ints(array: JsonArray): IntArray = array.map(JsonElement::getAsInt).toIntArray()

    private fun colors(array: JsonArray): IntArray = array.map { entry ->
        val c = entry.asJsonArray
        LetterboxFillMath.rgb(c[0].asInt, c[1].asInt, c[2].asInt)
    }.toIntArray()

    private fun channels(color: Int): IntArray = intArrayOf(
        LetterboxFillMath.red(color),
        LetterboxFillMath.green(color),
        LetterboxFillMath.blue(color),
    )

    private fun resource(name: String) = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
        "$name is missing from the test resources"
    }

    private companion object {
        const val VECTORS_NAME = "letterbox-fill-vectors.json"
        const val VECTORS = "letterbox-fill/$VECTORS_NAME"
        const val PROVENANCE = "letterbox-fill/PROVENANCE.txt"
        const val PROVENANCE_ROW_FIELDS = 3
        const val TOLERANCE = 1e-4
    }
}
