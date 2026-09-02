package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * S1716: the real half of the corpus - scenes the owner photographed or screenshotted, loaded from
 * the local cache that `scripts/ocrbench/fetch-real-scenes.ps1` fills.
 *
 * Media never live in the repository; the annotation always does. That split is the owner's ruling
 * of 2026-08-25 and it is not symmetry for its own sake: a picture can be fetched again, while a
 * hand-corrected annotation is the ticket's most expensive manual work, so it belongs under history
 * where a change to the ground truth shows up in a diff.
 *
 * Every failure here names the fetch script, because "no real scene was measured" must never read
 * as "the real scenes scored nothing" - strategic pillar 3 requires an absent dependency to stop the
 * run rather than shrink it silently.
 */
object RealSceneSource {

    const val FETCH_SCRIPT: String = "scripts/ocrbench/fetch-real-scenes.ps1"

    /** The manifest's own format version, independent of the annotation format's. */
    private const val MANIFEST_VERSION = 1

    private const val MANIFEST_RESOURCE = "ocrbench/real-scenes.json"
    private const val ANNOTATION_DIR = "ocrbench/annotations"
    private const val RECT_FIELDS = 4

    /** One real scene: the cached media file and the annotation committed for it. */
    data class RealScene(
        val sceneId: String,
        val media: File,
        val annotation: SceneAnnotation,
    )

    /** What the manifest promises, before anything is known about the cache. */
    data class ManifestEntry(
        val sceneId: String,
        val relativePath: String,
        val sha256: String,
    )

    /** Default cache root, matching the fetch script's own default. */
    fun defaultCacheRoot(): File = File("temp/ocrbench/cache")

    /**
     * Manifest entries as committed. An empty list is a legitimate state: it says the corpus asks
     * for no real scene yet, which is different from asking for one and not finding it.
     */
    fun manifest(): List<ManifestEntry> {
        val raw = readResource(MANIFEST_RESOURCE)
            ?: error("$MANIFEST_RESOURCE is missing from the test resources; it is committed, so restore it.")
        val doc = JSONObject(raw)
        require(doc.getInt("version") == MANIFEST_VERSION) {
            "real-scenes.json is version ${doc.getInt("version")}, this reader knows " +
                "$MANIFEST_VERSION - bump the reader before the manifest."
        }
        val scenes = doc.getJSONArray("scenes")
        return (0 until scenes.length()).map { index ->
            val entry = scenes.getJSONObject(index)
            ManifestEntry(
                sceneId = entry.getString("sceneId"),
                relativePath = entry.getString("relativePath"),
                sha256 = entry.getString("sha256"),
            )
        }
    }

    /**
     * Every real scene the manifest asks for, loaded from [cacheRoot].
     *
     * Throws when a scene is absent from the cache or carries no annotation, naming the script that
     * fixes it. Returning the ones that happened to be present would report a corpus that silently
     * shrank to whatever this machine had lying around.
     */
    fun all(cacheRoot: File = defaultCacheRoot()): List<RealScene> = manifest().map { entry ->
        val media = cacheRoot.listFiles { file -> file.nameWithoutExtension == entry.sceneId }
            ?.firstOrNull()
            ?: error(
                "real scene '${entry.sceneId}' is not in ${cacheRoot.path}; " +
                    "run $FETCH_SCRIPT to fetch it."
            )
        RealScene(entry.sceneId, media, annotationFor(entry.sceneId))
    }

    /**
     * The committed annotation for one scene. Absent annotation is an error rather than a draft:
     * strategic pillar 2 forbids a recogniser's own output from ever standing in for ground truth,
     * and an empty annotation would score every plate as perfect.
     */
    fun annotationFor(sceneId: String): SceneAnnotation {
        val raw = readResource("$ANNOTATION_DIR/$sceneId.json")
            ?: error(
                "real scene '$sceneId' has no annotation at $ANNOTATION_DIR/$sceneId.json; " +
                    "annotate it by hand - $FETCH_SCRIPT only fetches the picture."
            )
        return parseAnnotation(JSONObject(raw))
    }

    private fun parseAnnotation(doc: JSONObject): SceneAnnotation {
        val version = doc.getInt("version")
        require(version in SceneAnnotation.SUPPORTED_VERSIONS) {
            "annotation is version $version, this reader knows " +
                "${SceneAnnotation.SUPPORTED_VERSIONS.sorted().joinToString(", ")}."
        }
        val provenance = doc.getJSONObject("provenance")
        return SceneAnnotation(
            version = version,
            sceneId = doc.getString("sceneId"),
            widthPx = doc.getInt("widthPx"),
            heightPx = doc.getInt("heightPx"),
            textAreas = doc.getJSONArray("textAreas").mapObjects { area ->
                TextArea(area.getString("text"), rect(area.getJSONArray("box")), words(area))
            },
            paintableAreas = doc.getJSONArray("paintableAreas").mapObjects { area ->
                PaintableArea(rect(area.getJSONArray("box")))
            },
            readable = doc.getBoolean("readable"),
            provenance = Provenance(
                author = provenance.getString("author"),
                annotatedOn = provenance.getString("annotatedOn"),
                draft = provenance.getBoolean("draft"),
            ),
        )
    }

    /**
     * Word geometry of one text area, absent in every version-1 annotation.
     *
     * A missing key yields an empty list, which downstream means "not annotated at word level" rather
     * than "no word here" - see [TextArea.words].
     */
    private fun words(area: JSONObject): List<AnnotatedWord> {
        val words = area.optJSONArray("words") ?: return emptyList()
        return words.mapObjects { word ->
            AnnotatedWord(word.getString("text"), rect(word.getJSONArray("box")))
        }
    }

    private fun rect(values: JSONArray): Rect {
        require(values.length() == RECT_FIELDS) {
            "a box needs $RECT_FIELDS numbers [left, top, right, bottom], got ${values.length()}."
        }
        return Rect(values.getInt(0), values.getInt(1), values.getInt(2), values.getInt(3))
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> transform(getJSONObject(index)) }

    private fun readResource(path: String): String? =
        javaClass.classLoader?.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
}
