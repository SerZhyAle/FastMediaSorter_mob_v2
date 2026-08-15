package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S1661: crosses the Wear data layer to the watch, which resolves the received text back to its own copy of
// this enum. Gson takes the written value from the constant itself, so the envelope around it pins nothing
// here - the names are pinned on the constants instead. The wire names deliberately repeat the constant
// names, which are also the literals the watch copy spells, so the two sides cannot drift apart.
enum class WearPlaybackCommand {
    @SerializedName("PLAY_PAUSE")
    PLAY_PAUSE,

    @SerializedName("NEXT")
    NEXT,

    @SerializedName("PREVIOUS")
    PREVIOUS,

    @SerializedName("STOP")
    STOP
}
