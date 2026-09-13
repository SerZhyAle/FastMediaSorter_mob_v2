package com.sza.fastmediasorter.broadcast

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.sza.fastmediasorter.domain.model.WearCameraLensDto
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLensEnumerationManager
import com.sza.fastmediasorter.ui.cameracapture.model.CameraLensEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2551: the lens set this phone is willing to open for a watch, in the wire's own terms.
 *
 * Headless on purpose. The watch asks over the Data Layer, where there is no Activity and no
 * `LifecycleOwner` to bind against, so this walks the platform's cameras and binds nothing - reading
 * characteristics needs no capture session, and starting one here would open the camera minutes
 * before the owner's consent decides whether it may be opened at all.
 *
 * The whole enumerated set crosses, including physical sub-lenses, because strategic §5.3 makes the
 * selectable set the phone's to author: a watch that hardcoded a pair of lenses would offer the same
 * two on every phone regardless of what the phone actually has.
 *
 * Below API 28 the platform admits to logical cameras only, so the set is simply shorter there.
 *
 * Sits beside the rest of the broadcast layer rather than under `domain/usecase/`, because the lens
 * enumeration it maps is a UI-package type and a `domain` file importing one inverts the layer arrow
 * (S2751). Same reason the consent policy of Phase 04 lives here.
 */
class ListBroadcastCameraLensesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val enumeration = CameraLensEnumerationManager()

    suspend operator fun invoke(): BroadcastCameraLenses = withContext(Dispatchers.IO) {
        val provider = cameraProvider()
        if (provider == null) BroadcastCameraLenses.EMPTY else toWire(enumeration.expand(provider))
    }

    /**
     * S3038: the lenses the phone's own broadcast screens offer. Filtered through
     * [CameraLensEnumerationManager.select] so two sub-lenses with the same magnification do not appear as
     * two identical choices; the watch keeps the unfiltered set above.
     */
    suspend fun listOptions(): BroadcastLensChoice = withContext(Dispatchers.IO) {
        val entries = cameraProvider()?.let { enumeration.select(enumeration.expand(it)) }.orEmpty()
        if (entries.isEmpty()) {
            BroadcastLensChoice.EMPTY
        } else {
            BroadcastLensChoice(
                options = entries.map { entry ->
                    BroadcastLensOption(
                        id = entry.id,
                        facing = facingOf(entry.lensFacing),
                        zoomMultiplier = entry.equivalentMultiplier,
                    )
                },
                initialLensId = entries[enumeration.initialLensIndex(entries)].id,
            )
        }
    }

    private fun cameraProvider(): ProcessCameraProvider? =
        runCatching { ProcessCameraProvider.getInstance(context).get() }
            .getOrElse { error ->
                // A phone whose camera stack refuses to initialise still owes the watch an answer, so
                // the empty set travels and the watch says "no lenses" rather than waiting.
                Timber.w(error, "Broadcast lens list: the camera provider never arrived")
                null
            }

    /**
     * [CameraLensEntry.id] crosses unchanged: it is already this project's stable name for a lens, and
     * it is the only token the watch sends back on a switch, so a second identifier here would be a
     * second answer to which camera the owner picked.
     */
    internal fun toWire(entries: List<CameraLensEntry>): BroadcastCameraLenses {
        if (entries.isEmpty()) {
            return BroadcastCameraLenses.EMPTY
        }
        val lenses = entries.map { entry ->
            WearCameraLensDto(
                id = entry.id,
                labelKey = labelKeyOf(entry.lensFacing),
                facing = facingOf(entry.lensFacing)
            )
        }
        return BroadcastCameraLenses(
            lenses = lenses,
            activeLensId = entries[enumeration.initialLensIndex(entries)].id
        )
    }

    private fun labelKeyOf(lensFacing: Int): String = when (lensFacing) {
        CameraSelector.LENS_FACING_BACK -> LENS_KEY_BACK
        CameraSelector.LENS_FACING_FRONT -> LENS_KEY_FRONT
        else -> LENS_KEY_EXTERNAL
    }

    private fun facingOf(lensFacing: Int): String = when (lensFacing) {
        CameraSelector.LENS_FACING_BACK -> FACING_BACK
        CameraSelector.LENS_FACING_FRONT -> FACING_FRONT
        else -> FACING_EXTERNAL
    }

    companion object {

        /**
         * The two keys the watch resolves to its own localized label, spelled exactly as
         * `PhoneCameraScreen.LENS_KEY_BACK` / `LENS_KEY_FRONT` spell them. The wire is the entire
         * contract between two separately compiled modules, so a character of drift here shows the
         * raw [WearCameraLensDto.facing] word on the watch instead of a translated label.
         */
        internal const val LENS_KEY_BACK = "lens_back"
        internal const val LENS_KEY_FRONT = "lens_front"

        /** No watch label exists for this one, so the watch falls back to the facing word. */
        internal const val LENS_KEY_EXTERNAL = "lens_external"

        internal const val FACING_BACK = "back"
        internal const val FACING_FRONT = "front"
        internal const val FACING_EXTERNAL = "external"
    }
}

/**
 * What the phone offers and which of it is live.
 *
 * The two travel together because [WearCameraLensDto.id] is the only name the active lens has, so an
 * active id separated from the list it indexes could name a lens the watch was never sent.
 */
data class BroadcastCameraLenses(
    val lenses: List<WearCameraLensDto>,
    val activeLensId: String?
) {

    companion object {

        /** No camera answered. Distinct from a refusal, which the ack carries in its own field. */
        val EMPTY = BroadcastCameraLenses(emptyList(), null)
    }
}
