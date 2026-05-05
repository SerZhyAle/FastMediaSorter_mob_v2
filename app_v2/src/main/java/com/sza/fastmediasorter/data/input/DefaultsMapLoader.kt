package com.sza.fastmediasorter.data.input

import android.content.Context
import android.view.KeyEvent
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.domain.input.BindingSource
import com.sza.fastmediasorter.domain.input.InputBinding
import com.sza.fastmediasorter.domain.input.InputTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DefaultsMapLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun loadDefaults(): List<InputBinding> {
        val json = context.assets.open("input/default_bindings.json").bufferedReader().use { it.readText() }
        val root = Gson().fromJson(json, BindingsRoot::class.java)
        val result = mutableListOf<InputBinding>()
        for (entry in root.bindings) {
            val allTriggers = entry.triggers.keyboard + entry.triggers.gamepad +
                entry.triggers.mouse + entry.triggers.vr
            for (triggerStr in allTriggers) {
                result.add(
                    InputBinding(
                        commandId = entry.commandId,
                        trigger = InputTrigger.deserialize(triggerStr),
                        source = BindingSource.DEFAULT
                    )
                )
            }
        }
        return result
    }

    fun loadChromeOsDefaults(): List<InputBinding> = listOf(
        InputBinding("playback.pause_play",    InputTrigger.Key(KeyEvent.KEYCODE_SPACE),      BindingSource.DEFAULT),
        InputBinding("navigation.previous_file", InputTrigger.Key(KeyEvent.KEYCODE_DPAD_LEFT),  BindingSource.DEFAULT),
        InputBinding("navigation.next_file",   InputTrigger.Key(KeyEvent.KEYCODE_DPAD_RIGHT), BindingSource.DEFAULT),
        InputBinding("audio.volume_up",        InputTrigger.Key(KeyEvent.KEYCODE_DPAD_UP),    BindingSource.DEFAULT),
        InputBinding("audio.volume_down",      InputTrigger.Key(KeyEvent.KEYCODE_DPAD_DOWN),  BindingSource.DEFAULT),
        InputBinding("sorting.delete",         InputTrigger.Key(KeyEvent.KEYCODE_DEL),        BindingSource.DEFAULT),
        InputBinding("playback.pause_play",    InputTrigger.Key(KeyEvent.KEYCODE_ENTER),      BindingSource.DEFAULT),
        InputBinding("sorting.favourite",      InputTrigger.Key(KeyEvent.KEYCODE_F),          BindingSource.DEFAULT),
    )

    private data class BindingsRoot(
        @SerializedName("bindings") val bindings: List<BindingEntry> = emptyList()
    )

    private data class BindingEntry(
        @SerializedName("command_id") val commandId: String = "",
        @SerializedName("triggers") val triggers: TriggerMap = TriggerMap()
    )

    private data class TriggerMap(
        @SerializedName("keyboard") val keyboard: List<String> = emptyList(),
        @SerializedName("gamepad") val gamepad: List<String> = emptyList(),
        @SerializedName("mouse") val mouse: List<String> = emptyList(),
        @SerializedName("vr") val vr: List<String> = emptyList()
    )
}
