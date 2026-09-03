package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2142: the whole «Send to..» receiver list the phone offers - watch-side mirror.
 *
 * Arrives whole every time, including empty, for [WearStreamPinsPayload]'s reason: a delta or a
 * skipped empty push would leave a withdrawn receiver stuck here with no way to take it back.
 *
 * Mirrors the phone module's declaration field for field and name for name - the two must not drift.
 */
data class WearSendToReceiversPayload(
    @SerializedName("receivers") val receivers: List<WearSendToReceiverEntry>
)

/**
 * One receiver, resolved by the phone into what this watch can show on its own.
 *
 * Branch К (`research/03`): the phone's `R` class does not exist here, so the label arrives as text
 * already resolved there and the icon as a stable name looked up in this module's own icon set -
 * never as a resource id, and never as image bytes.
 *
 * Every key is pinned with [SerializedName], and that is a condition of the channel working rather
 * than a style: the phone ships minified while this copy stays unobfuscated, so an unpinned phone
 * would write `{"a":..}` and every field here would read as null - in silence, with no error
 * anywhere (S1631). A debug build never reproduces it.
 *
 * Everything but [id] and [title] carries a default, so a phone predating a later field still
 * decodes here.
 */
data class WearSendToReceiverEntry(
    /** The same string persisted in the phone's settings, so a stored toggle needs no translation. */
    @SerializedName("id") val id: String,
    /** Already-resolved label: the installed app's own name where there is one, else the title. */
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String? = null,
    /** Stable icon name resolved in this module's own icon set; null = the generic glyph. */
    @SerializedName("iconName") val iconName: String? = null,
    /** Whether this watch serves the receiver itself, rather than handing the file to the phone. */
    @SerializedName("servedOnWatch") val servedOnWatch: Boolean = false,
    /**
     * Media-type names this receiver accepts; empty = any type. Travels with the record because the
     * type filter depends on the file open here, so it has to be applied here - the alternative is
     * the phone publishing one list per media type.
     */
    @SerializedName("applicableTypes") val applicableTypes: List<String> = emptyList(),
    @SerializedName("batchCapable") val batchCapable: Boolean = false,
    @SerializedName("textCapable") val textCapable: Boolean = false,
    @SerializedName("requiresLocalFile") val requiresLocalFile: Boolean = true
)
