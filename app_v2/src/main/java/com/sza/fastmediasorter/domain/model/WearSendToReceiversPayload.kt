package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2142: the whole «Send to..» receiver list the phone offers, published to the watch.
 *
 * Sent whole every time, including empty, for [WearStreamPinsPayload]'s reason: a delta or a skipped
 * empty push would leave a withdrawn receiver stuck on the watch with no way to take it back.
 *
 * The watch declaration must stay a mirror of this one, field for field and name for name.
 */
data class WearSendToReceiversPayload(
    @SerializedName("receivers") val receivers: List<WearSendToReceiverEntry>
)

/**
 * One receiver, resolved by the phone into what the watch can show on its own.
 *
 * Branch К (`research/03`): the phone's `R` class does not exist on the watch, so the label travels
 * as text already resolved here and the icon as a stable name the watch looks up in its own set -
 * never as a resource id, and never as image bytes.
 *
 * Every key is pinned with [SerializedName], and that is a condition of the channel working rather
 * than a style: the phone ships minified while the watch keeps its copy of this contract
 * unobfuscated, so an unpinned phone would write `{"a":..}` and the watch would read every field as
 * null - in silence, with no error anywhere (S1631). A debug build never reproduces it.
 *
 * S2885: a Kotlin default does NOT make a field survive an older phone - Gson fills by reflection and
 * runs no constructor, so an absent key leaves a reference field null whatever the default says, and
 * gives a primitive the JVM zero rather than the declared default. Only the declared nullability
 * protects a later reference field, which is why the collection here is nullable and read through
 * `.orEmpty()`.
 */
data class WearSendToReceiverEntry(
    /** The same string persisted in the phone's settings, so a stored toggle needs no translation. */
    @SerializedName("id") val id: String,
    /** Already-resolved label: the installed app's own name where there is one, else the title. */
    @SerializedName("title") val title: String,
    @SerializedName("subtitle") val subtitle: String? = null,
    /** Stable icon name resolved in the watch's own icon set; null = the watch's generic glyph. */
    @SerializedName("iconName") val iconName: String? = null,
    /** Whether the watch serves this receiver itself, rather than handing the file to the phone. */
    @SerializedName("servedOnWatch") val servedOnWatch: Boolean = false,
    /**
     * [MediaType] names this receiver accepts; empty = any type. Travels with the record because the
     * type filter depends on the file open on the watch, so it has to be applied there - the
     * alternative is the phone publishing one list per media type.
     */
    @SerializedName("applicableTypes") val applicableTypes: List<String>? = null,
    @SerializedName("batchCapable") val batchCapable: Boolean = false,
    @SerializedName("textCapable") val textCapable: Boolean = false,
    /**
     * S2887: nullable rather than `Boolean = true`, because a non-null primitive takes the JVM zero
     * from an absent key - `false`, the opposite of the intended default, and it arrives as a valid
     * value that nothing can tell apart from a deliberate one. Null means the sender said nothing;
     * every reader resolves it through `?: true`.
     */
    @SerializedName("requiresLocalFile") val requiresLocalFile: Boolean? = null
)
