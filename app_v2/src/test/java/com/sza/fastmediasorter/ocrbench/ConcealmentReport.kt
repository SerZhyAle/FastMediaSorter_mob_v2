package com.sza.fastmediasorter.ocrbench

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * S1782: runs the corpus through the rasterizer and the concealment metric, and writes the dated report
 * the acceptance bound is later derived from.
 *
 * **No bound lives here.** Strategic §5.1 pillar 4 and S1716 ADR-3 keep it outside the code that
 * produces the number, so that moving it later shows up as a difference between two dated reports
 * instead of dissolving into a diff. This file therefore states values and counts and compares nothing
 * to anything.
 *
 * The report lands under `temp/` per CLAUDE.md Rule 1.
 */
object ConcealmentReport {

    /**
     * The corpus over measured scenes, with the scenes that carry no number counted apart.
     *
     * [unmeasuredCount] is a field rather than something a reader derives by subtraction: strategic
     * §5.1 pillar 3 asks for exactly that, because a count you have to compute is a count nobody
     * computes.
     */
    data class Summary(
        val sceneCount: Int,
        val measuredCount: Int,
        val unmeasuredCount: Int,
        val worst: Double?,
        val median: Double?,
        val unmeasuredReasons: List<String>,
    )

    /**
     * Compose every corpus scene and score it.
     *
     * A composition [OverlayRasterizer] refused becomes an unmeasured scene carrying the refusal as its
     * reason - never a zero. That keeps pillar 2 intact: the guard exists so a raster that never
     * happened cannot enter the axis as perfect concealment, and turning it into a counted refusal is
     * the only outcome that preserves both the count and the cause.
     */
    fun run(context: Context): List<SceneConcealment> = SyntheticScene.all().map { scene ->
        val sceneId = scene.annotation.sceneId
        try {
            val composition = OverlayRasterizer.compose(scene, context)
            val control = OverlayRasterizer.compose(ConcealmentMetric.withoutSourceInk(scene), context)
            ConcealmentMetric.score(scene, composition, control)
        } catch (failure: OverlayRasterizer.RasterFailure) {
            ConcealmentMetric.unmeasured(sceneId, failure.message ?: RASTER_REFUSED)
        }
    }

    fun summarise(scenes: List<SceneConcealment>): Summary {
        val values = scenes.mapNotNull { (it.worstResidualInk as? Measured.Value)?.value }
        val reasons = scenes.mapNotNull { it.unmeasuredReason() }
        return Summary(
            sceneCount = scenes.size,
            measuredCount = values.size,
            unmeasuredCount = reasons.size,
            worst = values.maxOrNull(),
            median = medianOf(values),
            unmeasuredReasons = reasons.distinct(),
        )
    }

    fun render(scenes: List<SceneConcealment>, summary: Summary): String = buildString {
        val build = "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE})"
        appendLine("# OCR overlay concealment report")
        appendLine()
        appendLine("**Report id:** ${reportId()}")
        appendLine("**Date:** ${LocalDate.now()}")
        appendLine("**Build measured:** $build")
        appendLine("**Scenes:** ${summary.sceneCount} total")
        appendLine("**Scenes measured:** ${summary.measuredCount}")
        appendLine("**Scenes NOT measured:** ${summary.unmeasuredCount}")
        appendLine()
        appendLine("## Per scene")
        appendLine()
        appendLine("| scene | measured | worst residual ink | why not measured |")
        appendLine("|-------|:--------:|-------------------:|------------------|")
        for (scene in scenes) {
            appendLine(sceneRow(scene))
        }
        appendLine()
        appendLine("## Per area, on the scenes that were measured")
        appendLine()
        appendLine("| scene | area | text | measured | residual ink |")
        appendLine("|-------|-----:|------|:--------:|-------------:|")
        for (scene in scenes) {
            for (area in scene.areas) {
                appendLine(areaRow(scene.sceneId, area))
            }
        }
        appendLine()
        appendLine("## Over measured scenes only")
        appendLine()
        appendLine("- **worst:** ${format(summary.worst)}")
        appendLine("- **median:** ${format(summary.median)}")
        appendLine()
        appendLine("### Why a scene went unmeasured")
        appendLine()
        for (reason in summary.unmeasuredReasons) {
            appendLine("- $reason")
        }
        appendLine()
        append(CAVEAT)
        appendLine()
    }

    /** Writes the report and the pointer a script reads back, and returns the file it wrote. */
    fun write(text: String): File {
        val file = File(reportRoot(), "${LocalDate.now()}/$REPORT_NAME")
        file.parentFile?.mkdirs()
        file.writeText(text)
        File(reportRoot(), LAST_REPORT).writeText(file.absolutePath)
        return file
    }

    /** Stable across a day's runs, so a bounds file can name the report it rested on. */
    fun reportId(): String = "concealment-${LocalDate.now()}"

    private fun sceneRow(scene: SceneConcealment): String {
        val value = (scene.worstResidualInk as? Measured.Value)?.value
        val reason = scene.unmeasuredReason() ?: "-"
        return "| ${scene.sceneId} | ${yesNo(scene.isMeasured)} | ${format(value)} | $reason |"
    }

    private fun areaRow(sceneId: String, area: AreaConcealment): String {
        val value = (area.residualInk as? Measured.Value)?.value
        return "| $sceneId | ${area.areaIndex} | ${area.text} | ${yesNo(area.isMeasured)} " +
            "| ${format(value)} |"
    }

    private fun yesNo(measured: Boolean): String = if (measured) "yes" else "NO"

    private fun format(value: Double?): String =
        value?.let { String.format(Locale.ROOT, "%.4f", it) } ?: "-"

    private fun medianOf(values: List<Double>): Double? {
        if (values.isEmpty()) {
            return null
        }
        val sorted = values.sorted()
        val middle = sorted.size / HALF
        return if (sorted.size % HALF == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / HALF
        }
    }

    private fun reportRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Gradle runs unit tests with the module dir as working dir; walk up to the repo root.
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        return File(dir, "temp/ocrbench")
    }

    private const val REPORT_NAME = "overlay-concealment-report.md"
    private const val LAST_REPORT = "last-concealment-report.txt"
    private const val HALF = 2
    private const val RASTER_REFUSED = "the rasterizer refused this scene without stating a reason"

    private val CAVEAT = listOf(
        "## What this report does not cover",
        "",
        "- **One axis only.** Residual ink under the plate. The three rectangle axes - found, overlap,",
        "  spill - are S1716's and live in its own report; nothing here restates them.",
        "- **No acceptance bound.** The bound derived from this report lives in",
        "  `app_v2/src/test/resources/ocrbench/concealment-bounds.json` and names the report it came",
        "  from. A bound stated here could be moved to agree with the number beside it.",
        "- **Not what a user sees.** This is the shipped view rasterised by Robolectric's native",
        "  graphics on a desktop host, not a photograph of a phone. Strategic §2 names device capture",
        "  as a separate and more expensive axis, deliberately not substituted by this one.",
        "- **Residual is measured against a control, not against the input.** Each scene is composed",
        "  twice: once as it stands, and once with its source ink erased. What still differs between",
        "  the two is the source showing through, as a share of the contrast it started with. A plate",
        "  nothing reads through scores 0, no plate at all scores 1. No tolerance is applied anywhere.",
    ).joinToString("\n")
}
