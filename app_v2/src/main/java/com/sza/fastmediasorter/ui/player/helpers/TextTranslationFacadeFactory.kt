package com.sza.fastmediasorter.ui.player.helpers

interface TextTranslationFacadeFactory {
    fun create(callback: TranslationManager.TranslationCallback): TextTranslationFacade
}
