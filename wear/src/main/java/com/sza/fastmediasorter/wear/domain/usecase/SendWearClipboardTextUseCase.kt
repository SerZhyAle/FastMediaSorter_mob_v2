package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.repository.WearClipboardTextOutcome
import com.sza.fastmediasorter.wear.domain.repository.WearClipboardTextSender
import javax.inject.Inject

/**
 * S3109: sends the text the screen is already showing to the paired phone.
 *
 * Takes the text rather than reading the clipboard itself: the screen holds what the wearer has just
 * seen in the preview, and a second read would send something else than what was on screen.
 */
class SendWearClipboardTextUseCase @Inject constructor(
    private val sender: WearClipboardTextSender
) {

    suspend operator fun invoke(text: String): WearClipboardTextOutcome = sender.send(text)
}
