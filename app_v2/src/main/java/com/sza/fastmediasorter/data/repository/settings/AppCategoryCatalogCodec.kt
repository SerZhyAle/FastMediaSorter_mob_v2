package com.sza.fastmediasorter.data.repository.settings

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.domain.model.launcher.AppCategory
import com.sza.fastmediasorter.domain.model.launcher.AppCategoryCatalog
import timber.log.Timber

/**
 * S4530: the catalog's stored JSON form.
 *
 * Decoding is tolerant because the value also arrives from restored backups, possibly written by another
 * build: anything unreadable degrades to an empty catalog rather than taking the launcher down, and ids
 * that name no category are dropped so a half-written value cannot hide an app from every group.
 */
object AppCategoryCatalogCodec {

    private const val FORMAT_VERSION = 1

    private val gson = Gson()

    fun encode(catalog: AppCategoryCatalog): String {
        val stored: StoredCatalog = StoredCatalog(
            version = FORMAT_VERSION,
            categories = catalog.categories.map { StoredCategory(it.id, it.name, it.systemCategory) },
            order = catalog.order,
            assignments = catalog.assignments.mapValues { it.value.toList() },
            seededPackages = catalog.seededPackages.toList(),
        )
        return gson.toJson(stored, StoredCatalog::class.java)
    }

    fun decode(json: String?): AppCategoryCatalog {
        if (json.isNullOrBlank()) return AppCategoryCatalog.EMPTY
        val stored = try {
            gson.fromJson(json, StoredCatalog::class.java)
        } catch (e: JsonParseException) {
            Timber.w(e, "App category catalog unreadable, starting empty")
            null
        }
        return stored?.let(::toCatalog) ?: AppCategoryCatalog.EMPTY
    }

    private fun toCatalog(stored: StoredCatalog): AppCategoryCatalog {
        // Gson fills absent fields with null even where Kotlin declares a non-null type.
        val categories = stored.categories.orEmpty()
            .filterNotNull()
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .map { AppCategory(id = checkNotNull(it.id), name = it.name, systemCategory = it.systemCategory) }
        val known = categories.mapTo(HashSet()) { it.id }
        val assignments = stored.assignments.orEmpty()
            .mapNotNull { (packageName, ids) ->
                val kept = ids.orEmpty().filterTo(LinkedHashSet()) { it in known }
                if (packageName.isNullOrBlank() || kept.isEmpty()) null else packageName to kept
            }
            .toMap()
        return AppCategoryCatalog(
            categories = categories,
            order = stored.order.orEmpty().filter { it in known }.distinct(),
            assignments = assignments,
            seededPackages = stored.seededPackages.orEmpty().filterNotNull().toSet(),
        )
    }

    // Wire names are pinned: the JSON outlives the build that wrote it, and R8 renames unpinned fields.
    private data class StoredCatalog(
        @SerializedName("version") val version: Int? = null,
        @SerializedName("categories") val categories: List<StoredCategory?>? = null,
        @SerializedName("order") val order: List<String>? = null,
        @SerializedName("assignments") val assignments: Map<String?, List<String>?>? = null,
        @SerializedName("seededPackages") val seededPackages: List<String?>? = null,
    )

    private data class StoredCategory(
        @SerializedName("id") val id: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("systemCategory") val systemCategory: Int? = null,
    )
}
