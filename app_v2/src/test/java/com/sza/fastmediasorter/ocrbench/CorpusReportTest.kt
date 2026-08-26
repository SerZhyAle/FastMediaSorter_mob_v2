package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.ocr.OcrBlockFilter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * S1716: the one command's other half - run the whole corpus and write the dated report.
 *
 * It is a test rather than a main() because the corpus already lives in the test source set, which
 * is what keeps the bench out of every shipped APK. `scripts/ocrbench/run-corpus.ps1` is the command;
 * this class is what it runs.
 *
 * **What the numbers here are and are not.** No recogniser runs: the pixel path a recogniser check
 * would need is unavailable on this host (strategic §6.1) and now belongs to S1782. The blocks fed
 * to the geometry are therefore derived from each scene's own annotation, which measures the plate
 * rectangle honestly and measures recall not at all - so the `found` axis is reported Unmeasured
 * with that reason rather than as the 100 % it would arithmetically be. Reporting it as measured
 * would be exactly the silent perfect score the ticket exists to prevent (§2.4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CorpusReportTest {

    @Test
    fun `the corpus runs and writes a dated report`() {
        val scenes = corpus()
        val metrics = scenes.map(::scoreScene)
        val summary = CorpusSummary.of(metrics)

        val report = reportFile()
        report.parentFile?.mkdirs()
        report.writeText(render(scenes, metrics, summary))
        File(reportRoot(), LAST_REPORT).writeText(report.absolutePath)

        assertTrue("the corpus must carry at least the synthetic scenes", scenes.isNotEmpty())
        assertTrue("the report must exist after the run", report.isFile)
    }

    /**
     * Synthetic scenes plus every real scene the manifest asks for. A registered real scene missing
     * from the cache throws out of [RealSceneSource] and fails this test on purpose: strategic
     * pillar 3 requires an absent dependency to stop the run rather than shrink it silently.
     */
    private fun corpus(): List<SceneAnnotation> =
        SyntheticScene.all().map { it.annotation } + RealSceneSource.all().map { it.annotation }

    private fun scoreScene(annotation: SceneAnnotation): SceneMetrics {
        val blocks = annotation.textAreas.map { area -> standInBlock(area.box) }
        val result = OverlayRectangleRun.run(annotation, blocks, annotation.heightPx.toFloat())
        return SceneScorer.score(annotation, result).copy(found = Measured.Unmeasured(NO_RECOGNISER))
    }

    /**
     * A translation is normally longer than its source; the growth factor is the stand-in this
     * report states in its own caveat section rather than presenting as a measurement.
     */
    private fun standInBlock(box: Rect) = OverlayRectangleRun.TranslatedBlock(
        sourceBox = box,
        translationWidth = box.width() * TRANSLATION_GROWTH,
        translationHeight = box.height().toFloat(),
        padding = PLATE_PADDING,
    )

    private fun render(
        scenes: List<SceneAnnotation>,
        metrics: List<SceneMetrics>,
        summary: CorpusSummary,
    ): String = buildString {
        val build = "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE})"
        appendLine("# OCR overlay rectangle report")
        appendLine()
        appendLine("**Date:** ${LocalDate.now()}")
        appendLine("**Build measured:** $build")
        appendLine("**Scenes:** ${scenes.size} total, ${RealSceneSource.manifest().size} of them real")
        appendLine()
        appendLine("## Per scene, per axis")
        appendLine()
        appendLine("| scene | axis | measured | value |")
        appendLine("|-------|------|:--------:|------:|")
        for (scene in metrics) {
            row(scene.sceneId, CorpusSummary.AXIS_FOUND, scene.found)
            row(scene.sceneId, CorpusSummary.AXIS_OVERLAP, scene.overlap)
            row(scene.sceneId, CorpusSummary.AXIS_SPILL, scene.spill)
            row(scene.sceneId, CorpusSummary.AXIS_DURATION, scene.durationNanos)
            // Two rows, never one: the relations are derived from different quantities and read for
            // different decisions, and this table is where a reader would take a combined row for an
            // answer to both (S2036).
            row(scene.sceneId, CorpusSummary.AXIS_LINE_TO_WORD, scene.lineToWord)
            row(scene.sceneId, CorpusSummary.AXIS_WORD_TO_MEDIAN, scene.wordToMedian)
        }
        appendLine()
        appendLine("## Per axis, over measured scenes only")
        appendLine()
        appendLine("| axis | worst | median | measured | unmeasured |")
        appendLine("|------|------:|-------:|---------:|-----------:|")
        for (axis in summary.axes) {
            appendLine(
                "| ${axis.axis} | ${format(axis.worst)} | ${format(axis.median)} " +
                    "| ${axis.measuredCount} | ${axis.unmeasuredCount} |"
            )
        }
        appendLine()
        appendLine("### Why a scene went unmeasured")
        appendLine()
        for (axis in summary.axes) {
            for (reason in axis.unmeasuredReasons) {
                appendLine("- ${axis.axis}: $reason")
            }
        }
        appendLine()
        appendThresholdFractions(scenes)
        append(CAVEAT)
        appendLine()
    }

    /**
     * S2036: what the live absolute box thresholds are worth on each scene, as a fraction of its median
     * annotated line height - the one number ADR-3 of `docs/OCR_OVERLAY_ACCURACY.md` §12.1 is read from.
     */
    private fun StringBuilder.appendThresholdFractions(scenes: List<SceneAnnotation>) {
        val fractions = scenes.map(ThresholdFraction::of)
        appendLine("## Live box thresholds as a fraction of line height")
        appendLine()
        appendLine(
            "Read live from `OcrBlockFilter`: MIN_BOX_WIDTH=${OcrBlockFilter.MIN_BOX_WIDTH}, " +
                "MIN_BOX_HEIGHT=${OcrBlockFilter.MIN_BOX_HEIGHT}."
        )
        appendLine()
        appendLine("| scene | resolution | median line height | width threshold | height threshold |")
        appendLine("|-------|-----------:|-------------------:|----------------:|-----------------:|")
        for (fraction in fractions) {
            appendLine(
                "| ${fraction.sceneId} | ${fraction.widthPx}x${fraction.heightPx} " +
                    "| ${cell(fraction.medianLineHeight)} | ${cell(fraction.widthFraction)} " +
                    "| ${cell(fraction.heightFraction)} |"
            )
        }
        appendLine()
        val spread = ThresholdFraction.heightFractionSpread(fractions)
        appendLine("**Height-fraction spread across measured scenes:** ${spread?.let(::format) ?: NO_SPREAD}")
        appendLine()
    }

    private fun cell(measured: Measured<Double>): String = when (measured) {
        is Measured.Value -> format(measured.value)
        is Measured.Unmeasured -> "-"
    }

    private fun StringBuilder.row(sceneId: String, axis: String, measured: Measured<*>) {
        val cells = when (measured) {
            is Measured.Value -> "yes | ${measured.value}"
            is Measured.Unmeasured -> "NO | -"
        }
        appendLine("| $sceneId | $axis | $cells |")
    }

    private fun format(value: Double?): String =
        value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "-"

    private fun reportRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Gradle runs unit tests with the module dir as working dir; walk up to the repo root.
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return File(dir, "temp/ocrbench")
    }

    private fun reportFile(): File =
        File(reportRoot(), "${LocalDate.now()}/overlay-rectangle-report.md")

    private companion object {
        const val LAST_REPORT = "last-report.txt"
        const val TRANSLATION_GROWTH = 1.6f
        const val PLATE_PADDING = 8f
        const val NO_RECOGNISER =
            "no recogniser ran - blocks came from the annotation, so recall is not measured here"

        /**
         * Printed instead of 0 when the measured scenes share a single resolution. A zero here would
         * read as "the fraction is stable, so relativity buys nothing" - the very conclusion the spread
         * exists to support or refuse, reached without any change of scale to observe (S2036).
         */
        const val NO_SPREAD =
            "not measured - every measured scene shares one resolution, so a spread would be zero " +
                "by construction rather than by finding"

        val CAVEAT = listOf(
            "## What this corpus does not cover",
            "",
            "- **Rectangles only.** Every number above is arithmetic on boxes. Nothing here reads a",
            "  single pixel, so nothing here can say how much of the source text a plate actually hides.",
            "- **Source-ink concealment is unmeasured, and owned by S1782.** It needs a rasterised",
            "  composition of plate over source, which this host cannot produce: Robolectric's legacy",
            "  graphics records draw calls instead of rasterising them, and its native graphics runtime",
            "  ships no Windows binary before Robolectric 4.16.1. An empty field here would read as",
            "  \"measured and fine\" - it is stated instead.",
            "- **Recall is unmeasured.** No recogniser is in this loop, so `found` reports Unmeasured on",
            "  every scene rather than the 100 % that feeding a scene its own annotation would produce.",
            "- **The translated extent is a stand-in.** Plate width is driven by a fixed multiple of the",
            "  source width and by the source's own height - chosen for determinism, not measured from",
            "  any translator's output.",
            "- **Real scenes enter through the manifest.** With none registered the report describes the",
            "  synthetic scenes only; that is visible in the scene count, never hidden by it.",
            "- **The height relations give a lower bound only.** They are measured on annotated truth,",
            "  which holds real text by construction. Artifact rejection fires on tokens carrying no",
            "  letter or digit, so nothing here says how low the multiplier could go before it stops",
            "  catching an artifact - that needs annotated artifacts, which this format does not carry.",
            "- **The threshold fraction means nothing while every scene shares one resolution.** The",
            "  spread is what decides whether an absolute pixel bound is the defect, and scenes of one",
            "  size produce a spread of zero by construction rather than by finding.",
        ).joinToString("\n")
    }
}
