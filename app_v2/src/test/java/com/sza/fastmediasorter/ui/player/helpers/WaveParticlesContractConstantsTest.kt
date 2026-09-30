package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * WAVE-PARTICLES section 7, rung 1 made mechanical: every section 3 constant of the phone renderer
 * ([AudioWaveParticleView]) and the watch renderer (`wear/.../ui/common/WaveParticleBackground.kt`) is
 * paired with the same value in the contract's reference implementation, which the website serves
 * byte-identical at `documentation/assets/wave-particles.js`.
 *
 * The three files are read as text, not loaded: the renderers keep their constants private, and the
 * watch lives in another module. A pair whose constant is missing on either side fails too - a rename
 * must not turn the check into a silent pass.
 */
class WaveParticlesContractConstantsTest {

    private val repoRoot = findRepoRoot()
    private val reference = read("documentation/assets/wave-particles.js")
    private val phone = kotlinConstants(
        read("app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt")
    )
    private val watch = kotlinConstants(
        read("wear/src/main/java/com/sza/fastmediasorter/wear/ui/common/WaveParticleBackground.kt")
    )

    @Test
    fun `the reference is the contract version this product declares`() {
        assertTrue(
            "reference implementation header no longer says contract version 0.12",
            reference.contains("contract version 0.12")
        )
    }

    @Test
    fun `every phone constant equals the reference`() {
        assertPairs("phone", phone, phonePairs())
    }

    @Test
    fun `every watch constant equals the reference`() {
        assertPairs("watch", watch, watchPairs())
    }

    @Test
    fun `the watch stroke is one fixed value inside the range the reduced profile allows`() {
        val stroke = requireNotNull(watch["WAVE_STROKE_PX"]) { "watch WAVE_STROKE_PX is missing" }
        val range = refArray("STROKE_WIDTH")
        assertTrue("watch stroke $stroke outside $range", stroke >= range[0] && stroke <= range[1])
    }

    // ---- pairs: Kotlin constant name to its expected value from the reference ----

    private fun sharedPairs(): Map<String, Double> = mapOf(
        "TIME_INCREMENT" to refNumber("TIME_STEP"),
        "REFERENCE_FRAME_NANOS" to refNumber("REFERENCE_FRAME_MS") * NANOS_PER_MILLI,
        "SPEED_MULT_MIN" to refArray("SPEED_MULTIPLIER")[0],
        "SPEED_MULT_MAX" to refArray("SPEED_MULTIPLIER")[1],
        "PARTICLE_BASE_SPEED" to refNumber("PARTICLE_SPEED_BASE"),
        "PARTICLE_DIRECTIONAL_BIAS" to refNumber("PARTICLE_SPEED_RANGE"),
        "PARTICLE_RANDOM_SPREAD" to refNumber("PARTICLE_JITTER"),
        "COUNTER_DRIFT_CHANCE" to refNumber("COUNTER_DRIFT_PROBABILITY"),
        "COUNTER_DRIFT_SIGN" to refNumber("COUNTER_DRIFT_FACTOR"),
        "HUE_SPREAD_DEG" to dynamicPalette("spread"),
        "WAVE_HUE_STEP_MIN" to dynamicLineStep()[0],
        "WAVE_HUE_STEP_SPAN" to dynamicLineStep()[1] - dynamicLineStep()[0],
        "WAVE_SATURATION" to refNumber("LINE_SATURATION"),
        "PARTICLE_SATURATION" to refNumber("PARTICLE_SATURATION"),
        "WAVE_ALPHA_BASE" to refNumber("LINE_OPACITY_BASE"),
        "WAVE_ALPHA_GAIN" to refNumber("LINE_OPACITY_GAIN"),
        "PARTICLE_ALPHA_BASE" to refNumber("PARTICLE_OPACITY_BASE"),
        "PARTICLE_ALPHA_GAIN" to refNumber("PARTICLE_OPACITY_GAIN"),
        "OPACITY_SCALE" to refNumber("OPACITY_SCALE"),
        "RAMP_GAIN_FLOOR" to refNumber("RAMP_GAIN_START"),
        "RAMP_GAIN_SPAN" to 1.0 - refNumber("RAMP_GAIN_START"),
        "CENTER_DRIFT_RATE" to refNumber("CENTRE_DRIFT_RATE"),
        "CENTER_DRIFT_FRACTION" to refNumber("CENTRE_DRIFT_FRACTION"),
        "WAVE_LANE_SPACING_FRACTION" to refNumber("LANE_FRACTION"),
        "TRAVEL_MARGIN_STEPS" to refNumber("SPAN_EXTRA_STEPS"),
        "WAVE_FREQUENCY" to refNumber("SPATIAL_FREQUENCY"),
        "WAVE_PHASE_STEP" to refNumber("LANE_PHASE_STEP"),
        "WAVE_ENVELOPE_BASE" to refNumber("ENVELOPE_BASE"),
        "WAVE_ENVELOPE_SWING" to refNumber("ENVELOPE_GAIN"),
        "WAVE_ENVELOPE_RATE" to refNumber("ENVELOPE_RATE"),
        "WAVE_ENVELOPE_LANE_STEP" to refNumber("ENVELOPE_LANE_STEP")
    )

    private fun phonePairs(): Map<String, Double> = sharedPairs() + mapOf(
        "STARTUP_RAMP_FRAMES" to refNumber("RAMP_FRAMES"),
        "WAVE_COUNT_MIN" to profile("full", "lines")[0],
        "WAVE_COUNT_MAX" to profile("full", "lines")[1],
        "PARTICLE_MIN" to profile("full", "particles")[0],
        "PARTICLE_MAX" to profile("full", "particles")[1],
        "WAVE_COUNT_MIN_LOW" to profile("reduced", "lines")[0],
        "WAVE_COUNT_MAX_LOW" to profile("reduced", "lines")[1],
        "PARTICLE_MIN_LOW" to profile("reduced", "particles")[0],
        "PARTICLE_MAX_LOW" to profile("reduced", "particles")[1],
        "STEP_PX_BASE" to refNumber("SAMPLE_STEP"),
        "STEP_JITTER_MIN" to refArray("SAMPLE_STEP_JITTER")[0],
        "STEP_JITTER_SPAN" to refArray("SAMPLE_STEP_JITTER")[1] - refArray("SAMPLE_STEP_JITTER")[0],
        "STROKE_MIN" to refArray("STROKE_WIDTH")[0],
        "STROKE_MAX" to refArray("STROKE_WIDTH")[1],
        "AMPLITUDE_MIN" to refArray("AMPLITUDE_FRACTION")[0],
        "AMPLITUDE_MAX" to refArray("AMPLITUDE_FRACTION")[1],
        "PARTICLE_R_MIN" to refArray("PARTICLE_RADIUS")[0],
        "PARTICLE_R_MAX" to refArray("PARTICLE_RADIUS")[1],
        "PALETTE_WAVE_STEP_MIN" to bandedLineStep()[0],
        "PALETTE_WAVE_STEP_RANGE" to bandedLineStep()[1] - bandedLineStep()[0],
        "PALETTE_PARTICLE_SPREAD_DEG" to bandedSpread(),
        "FADE_FRACTION" to refNumber("WASH_ALPHA"),
        "FADE_CHANNEL_DARK" to washChannel("WASH_DARK"),
        "FADE_CHANNEL_LIGHT" to washChannel("WASH_LIGHT"),
        "WAVE_LIGHTNESS_DARK" to refProperty("LINE_LIGHTNESS", "dark"),
        "WAVE_LIGHTNESS_LIGHT" to refProperty("LINE_LIGHTNESS", "light"),
        "PARTICLE_LIGHTNESS_DARK" to refProperty("PARTICLE_LIGHTNESS", "dark"),
        "PARTICLE_LIGHTNESS_LIGHT" to refProperty("PARTICLE_LIGHTNESS", "light"),
        "SPEED_SCALE_MIN" to bound("speed")[0],
        "SPEED_SCALE_MAX" to bound("speed")[1],
        "DENSITY_SCALE_MIN" to bound("density")[0],
        "DENSITY_SCALE_MAX" to bound("density")[1]
    ) + bandedPalettePairs()

    private fun bandedPalettePairs(): Map<String, Double> =
        listOf("GREEN", "PINK", "BLUE").flatMap { key ->
            val (low, high, centre) = bandedPalette(key)
            listOf(
                "${key}_HUE_BASE_MIN" to low,
                "${key}_HUE_BASE_RANGE" to high - low,
                "${key}_PARTICLE_HUE_BASE" to centre
            )
        }.toMap()

    private fun watchPairs(): Map<String, Double> = sharedPairs() + mapOf(
        "RAMP_FRAMES" to refNumber("RAMP_FRAMES"),
        "WAVE_COUNT_MIN" to profile("reduced", "lines")[0],
        "WAVE_COUNT_MAX" to profile("reduced", "lines")[1],
        "PARTICLE_COUNT_MIN" to profile("reduced", "particles")[0],
        "PARTICLE_COUNT_MAX" to profile("reduced", "particles")[1],
        "WAVE_STEP_PX" to refNumber("SAMPLE_STEP"),
        "WAVE_STEP_JITTER_MIN" to refArray("SAMPLE_STEP_JITTER")[0],
        "WAVE_STEP_JITTER_SPAN" to refArray("SAMPLE_STEP_JITTER")[1] - refArray("SAMPLE_STEP_JITTER")[0],
        "WAVE_AMPLITUDE_MIN" to refArray("AMPLITUDE_FRACTION")[0],
        "WAVE_AMPLITUDE_MAX" to refArray("AMPLITUDE_FRACTION")[1],
        "PARTICLE_RADIUS_MIN" to refArray("PARTICLE_RADIUS")[0],
        "PARTICLE_RADIUS_MAX" to refArray("PARTICLE_RADIUS")[1],
        "WASH_ALPHA" to refNumber("WASH_ALPHA"),
        "WAVE_LIGHTNESS" to refProperty("LINE_LIGHTNESS", "dark"),
        "PARTICLE_LIGHTNESS" to refProperty("PARTICLE_LIGHTNESS", "dark")
    )

    private fun assertPairs(surface: String, actual: Map<String, Double>, expected: Map<String, Double>) {
        val failures = expected.mapNotNull { (name, want) ->
            val got = actual[name]
            when {
                got == null -> "$name: missing from the $surface renderer"
                !close(got, want) -> "$name: $surface has $got, the reference has $want"
                else -> null
            }
        }
        assertEquals(
            "WAVE-PARTICLES section 3 drift - fix the constant or record a registry exception:\n" +
                failures.joinToString("\n"),
            emptyList<String>(),
            failures
        )
    }

    // ---- the reference implementation, read as text ----

    private fun refNumber(name: String): Double {
        val match = Regex("""var $name = ([^;\[{]+);""").find(reference)
        return evaluate(requireNotNull(match) { "reference has no var $name" }.groupValues[1], emptyMap())
    }

    private fun refArray(name: String): List<Double> {
        val match = Regex("""var $name = \[([^\]]+)\];""").find(reference)
        return numbers(requireNotNull(match) { "reference has no array $name" }.groupValues[1])
    }

    private fun refProperty(name: String, property: String): Double {
        val body = Regex("""var $name = \{([^}]+)\};""").find(reference)
        val value = Regex("""$property: ([0-9.]+)""").find(requireNotNull(body) { "no object $name" }.value)
        return requireNotNull(value) { "$name has no $property" }.groupValues[1].toDouble()
    }

    private fun profile(name: String, field: String): List<Double> {
        val body = Regex("""$name: \{([^}]+)\}""").find(reference)
        val value = Regex("""$field: \[([^\]]+)\]""").find(requireNotNull(body) { "no profile $name" }.value)
        return numbers(requireNotNull(value) { "profile $name has no $field" }.groupValues[1])
    }

    private fun bound(control: String): List<Double> {
        val body = Regex("""var BOUNDS = \{([^;]+)\};""").find(reference)
        val value = Regex("""$control: \[([^\]]+)\]""").find(requireNotNull(body) { "no BOUNDS" }.value)
        return numbers(requireNotNull(value) { "BOUNDS has no $control" }.groupValues[1])
    }

    private fun dynamicPalette(field: String): Double {
        val value = Regex("""DYNAMIC:[^}]*$field: ([0-9.]+)""").find(reference)
        return requireNotNull(value) { "DYNAMIC has no $field" }.groupValues[1].toDouble()
    }

    private fun dynamicLineStep(): List<Double> {
        val value = Regex("""DYNAMIC:[^}]*lineStep: u\(([^)]+)\)""").find(reference)
        return numbers(requireNotNull(value) { "DYNAMIC has no lineStep" }.groupValues[1])
    }

    private fun bandedPalette(key: String): List<Double> {
        val value = Regex("""$key: function \(u\) \{ return banded\(u, ([^)]+)\)""").find(reference)
        return numbers(requireNotNull(value) { "no banded palette $key" }.groupValues[1])
    }

    private fun bandedLineStep(): List<Double> {
        val value = Regex("""function banded[^}]*lineStep: u\(([^)]+)\)""").find(reference)
        return numbers(requireNotNull(value) { "banded() has no lineStep" }.groupValues[1])
    }

    private fun bandedSpread(): Double {
        val value = Regex("""function banded[^}]*spread: ([0-9.]+)""").find(reference)
        return requireNotNull(value) { "banded() has no spread" }.groupValues[1].toDouble()
    }

    private fun washChannel(name: String): Double {
        val value = Regex("""var $name = 'rgb\(([0-9]+),""").find(reference)
        return requireNotNull(value) { "reference has no $name" }.groupValues[1].toDouble()
    }

    // ---- the Kotlin sources, read as text ----

    private fun kotlinConstants(source: String): Map<String, Double> {
        val raw = Regex("""const val ([A-Z_]+) = ([^/\n]+(?:/ [^/\n]+)?)""")
            .findAll(source)
            .associate { it.groupValues[1] to it.groupValues[2].trim() }
        val resolved = mutableMapOf<String, Double>()
        raw.forEach { (name, expr) -> runCatching { evaluate(expr, raw) }.onSuccess { resolved[name] = it } }
        return resolved
    }

    private fun evaluate(expression: String, symbols: Map<String, String>): Double {
        val parts = expression.split("/").map { it.trim() }
        val values = parts.map { term ->
            symbols[term]?.let { evaluate(it, symbols) } ?: literal(term)
        }
        return values.drop(1).fold(values.first()) { acc, divisor -> acc / divisor }
    }

    private fun literal(term: String): Double =
        term.replace("_", "").trimEnd('f', 'F', 'L').toDouble()

    private fun numbers(list: String): List<Double> = list.split(",").map { it.trim().toDouble() }

    private fun close(a: Double, b: Double): Boolean = abs(a - b) <= TOLERANCE * max(1.0, max(abs(a), abs(b)))

    private fun read(relative: String): String {
        val file = File(repoRoot, relative)
        assertTrue("missing $relative under $repoRoot", file.isFile)
        return file.readText()
    }

    // Gradle runs unit tests with the module dir as working dir; walk up to the repo root.
    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return dir
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0

        // Float constants carry about seven significant digits.
        const val TOLERANCE = 1e-6
    }
}
