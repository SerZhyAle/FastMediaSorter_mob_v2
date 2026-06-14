package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.delivery.DeliverableSourceDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the effective [DeliverableSourceDescriptor] for a [DeliverableSet] by overlaying the
 * remote manifest's source URLs on top of the compiled bundled descriptor (S0386 strategic §6.1 B2).
 *
 * The manifest - a single **stationary** file on our mirror (fixed tag, not keyed by app version) -
 * supplies **only** the source URLs per file, so mirror endpoints can be moved without an app release.
 * The authoritative `sha256`/`minSize` integrity anchors always come from the compiled bundled
 * descriptor and are never read from the manifest, so a tampered manifest cannot weaken native-`.so`
 * verification - and a stale URL just fails the app-pinned hash and falls back. If the manifest is
 * unreachable or malformed, the bundled descriptor is used verbatim - the shipping version's URLs are
 * baked in, so the first run works offline of the manifest.
 *
 * A fixed URL (not `delivery-v<versionCode>/`) is intentional: per-app-version URL isolation is
 * unnecessary because hashes are app-pinned and verification fails safe, and a stationary address is
 * the whole point - one file we can edit in place to move endpoints. If version-specific URLs are ever
 * needed, key them inside the JSON by `versionCode`, not in the path.
 */
@Singleton
class DeliveryManifestDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val bundledDescriptors: Map<DeliverableSet, @JvmSuppressWildcards DeliverableSourceDescriptor>
) {

    /** The compiled fallback descriptor for [set], or null if this flavor does not ship the set. */
    fun bundledDescriptor(set: DeliverableSet): DeliverableSourceDescriptor? = bundledDescriptors[set]

    /**
     * Bundled descriptor for [set] with manifest-supplied URLs applied per file. Returns null when
     * this flavor declares no descriptor for the set. Integrity anchors stay as bundled.
     */
    suspend fun resolve(set: DeliverableSet): DeliverableSourceDescriptor? {
        val bundled = bundledDescriptors[set] ?: return null
        val urlOverrides = fetchUrlOverrides() ?: return bundled
        val setUrls = urlOverrides[set.name] ?: return bundled
        return bundled.copy(
            files = bundled.files.map { file ->
                val urls = setUrls[file.fileName]
                if (urls.isNullOrEmpty()) file else file.copy(sources = urls)
            }
        )
    }

    /**
     * Fetch and parse the version-keyed manifest into `setName -> fileName -> ordered URL list`.
     * Returns null on any unreachable/malformed manifest - an expected, non-error fallback to bundled.
     */
    private suspend fun fetchUrlOverrides(): Map<String, Map<String, List<String>>>? =
        withContext(Dispatchers.IO) {
            val url = "$MANIFEST_BASE/$MANIFEST_TAG/$MANIFEST_FILE"
            try {
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.i("Delivery manifest unavailable (HTTP %d) - using bundled URLs", response.code)
                        return@withContext null
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) null else parse(body)
                }
            } catch (e: IOException) {
                Timber.i(e, "Delivery manifest unreachable - using bundled URLs")
                null
            }
        }

    private fun parse(json: String): Map<String, Map<String, List<String>>>? = try {
        val root = JSONObject(json).optJSONObject(KEY_SETS) ?: return null
        buildMap {
            for (setName in root.keys()) {
                val filesObj = root.optJSONObject(setName) ?: continue
                val files = buildMap<String, List<String>> {
                    for (fileName in filesObj.keys()) {
                        val arr = filesObj.optJSONArray(fileName) ?: continue
                        put(fileName, (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) })
                    }
                }
                put(setName, files)
            }
        }
    } catch (e: org.json.JSONException) {
        Timber.w(e, "Delivery manifest malformed - using bundled URLs")
        null
    }

    private companion object {
        const val MANIFEST_BASE = "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download"
        // Stationary tag, co-located with the payloads - not keyed by app versionCode (see class doc).
        const val MANIFEST_TAG = "delivery-so-v1"
        const val MANIFEST_FILE = "delivery-manifest.json"
        const val KEY_SETS = "sets"
    }
}
