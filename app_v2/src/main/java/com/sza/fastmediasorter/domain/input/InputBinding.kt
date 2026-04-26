package com.sza.fastmediasorter.domain.input

data class InputBinding(
    val commandId: String,
    val trigger: InputTrigger,
    val source: BindingSource
)

enum class BindingSource { DEFAULT, OVERRIDE }

enum class InputSurface { PLAYER, BROWSER, DIALOG, VR }
