package com.sza.fastmediasorter.ui.common.help

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Form of the help a surface offers (contract `CONTEXT-HELP` rule 4). */
enum class HelpForm {
    PAGE,
    LAYER,
    BOTH,
    INHERIT,
    NONE,
    ;

    companion object {
        fun parse(value: String?): HelpForm? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** A sub-page of a surface, such as one Settings tab. */
data class HelpSubPage(
    val id: String,
    val name: String,
    val help: String?,
    val anchor: String?,
)

/** One row of the help map: one surface and what its help is. */
data class HelpSurface(
    val id: String,
    val name: String,
    val form: HelpForm,
    val help: String?,
    val anchor: String?,
    val parent: String?,
    val reason: String?,
    val pages: List<HelpSubPage>,
)

/**
 * The shipped help map (`assets/help/help-map.json`, contract `CONTEXT-HELP` rule 17). The same file is
 * read by `scripts/quality/assert-help-map.ps1`, so the app and the gate never disagree.
 */
data class HelpMap(
    val schemaVersion: Int,
    val pageTemplate: String,
    val fallbackLocale: String,
    val surfaces: List<HelpSurface>,
) {
    private val byId: Map<String, HelpSurface> = surfaces.associateBy { it.id }

    fun surface(id: String): HelpSurface? = byId[id]

    companion object {
        const val DEFAULT_TEMPLATE = "{locale}/{help}.html"
        const val DEFAULT_FALLBACK_LOCALE = "en"

        /**
         * Parses the map. Unknown members are ignored and a row with an unknown form is skipped, so a map
         * written against a later schema stays readable (contract section 3).
         */
        fun parse(json: String): HelpMap {
            val root = JsonParser.parseString(json).asJsonObject
            val surfaces = root.array("surfaces").mapNotNull { element ->
                (element as? JsonObject)?.let(::parseSurface)
            }
            return HelpMap(
                schemaVersion = root.string("schemaVersion")?.toIntOrNull() ?: 1,
                pageTemplate = root.string("pageTemplate") ?: DEFAULT_TEMPLATE,
                fallbackLocale = root.string("fallbackLocale") ?: DEFAULT_FALLBACK_LOCALE,
                surfaces = surfaces,
            )
        }

        private fun parseSurface(obj: JsonObject): HelpSurface? {
            val id = obj.string("id")
            val form = HelpForm.parse(obj.string("form"))
            if (id.isNullOrBlank() || form == null) return null
            val pages = obj.array("pages").mapNotNull { element ->
                val page = element as? JsonObject
                val pageId = page?.string("id")
                if (page == null || pageId.isNullOrBlank()) {
                    null
                } else {
                    HelpSubPage(pageId, page.string("name").orEmpty(), page.string("help"), page.string("anchor"))
                }
            }
            return HelpSurface(
                id = id,
                name = obj.string("name").orEmpty(),
                form = form,
                help = obj.string("help"),
                anchor = obj.string("anchor"),
                parent = obj.string("parent"),
                reason = obj.string("reason"),
                pages = pages,
            )
        }

        private fun JsonObject.string(name: String): String? {
            val value = get(name)
            return if (value == null || value.isJsonNull || !value.isJsonPrimitive) null else value.asString
        }

        private fun JsonObject.array(name: String): JsonArray {
            val value = get(name)
            return if (value != null && value.isJsonArray) value.asJsonArray else JsonArray()
        }
    }
}
