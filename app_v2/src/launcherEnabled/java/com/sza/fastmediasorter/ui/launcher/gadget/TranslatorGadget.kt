package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherTranslatorBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.TranslationSettingsDialog
import com.sza.fastmediasorter.ui.player.helpers.TextTranslationFacade
import com.sza.fastmediasorter.ui.player.helpers.TextTranslationFacadeFactory
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.util.showBoundToHost
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S1177 pillar B: offline translation on the desktop, in the cell rather than in another screen.
 *
 * The engine is not new and is not reached directly: the camera and the players already translate through
 * a contract with a No-Op implementation for the flavors without it, and this cell talks to that same
 * contract (strategic §5.3). Every flavor that has a desktop also has translation, so the cell needs no
 * runtime capability check and adds no gating axis.
 *
 * Seeds at 2x2 with a 2x1 floor - the owner's ruling of 2026-08-17: a translation almost never fits one
 * line, and the floor leaves the search-cell shape to whoever needs the space back.
 */
class TranslatorGadget @Inject constructor(
    private val facadeFactory: Lazy<TextTranslationFacadeFactory>,
    private val settingsRepository: Lazy<SettingsRepository>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_TRANSLATOR
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 2
    override val minSpanW: Int = 2
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_translator
    override val iconRes: Int = R.drawable.ic_translate
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        TranslatorGadgetView(container.context, facadeFactory, settingsRepository)
}

/**
 * What the cell shows under the result. One of these, never a bare blank.
 *
 * A separate type rather than a string decided inline, so [decideTranslatorState] can be asserted without
 * a view - and so a fifth state has to be added here, where every branch is visible at once.
 */
enum class TranslatorState {
    /** The user has not typed or pasted anything yet. */
    EMPTY_INPUT,

    /** The language pack is still downloading; translation resumes by itself once it is there. */
    MODEL_MISSING,

    /** The engine refused this pair - offline translation does not cover every combination. */
    PAIR_UNAVAILABLE,

    /** The engine failed for any other reason. */
    FAILED,

    /** A translation is on screen. */
    TRANSLATED,
}

/**
 * S1177: which state the cell is in, as a function of what it knows - no view, no engine, no context.
 *
 * Extracted because the states are the promise this cell makes to the user (strategic §2 goal 4), and a
 * promise that can only be checked by hand on a device is one that quietly stops holding.
 */
fun decideTranslatorState(
    input: String,
    translated: String?,
    modelMissing: Boolean,
    failed: Boolean,
): TranslatorState = when {
    input.isBlank() -> TranslatorState.EMPTY_INPUT
    modelMissing -> TranslatorState.MODEL_MISSING
    failed -> TranslatorState.FAILED
    translated == null -> TranslatorState.PAIR_UNAVAILABLE
    else -> TranslatorState.TRANSLATED
}

/**
 * The cell's view.
 *
 * Nothing typed or translated survives a rebuild of the desktop, and nothing is written to the cell's
 * stored param: text on a home screen is personal data visible to whoever picks the device up, which is
 * the same prohibition the search cell carries (strategic §5.2).
 */
private class TranslatorGadgetView(
    context: Context,
    private val facadeFactory: Lazy<TextTranslationFacadeFactory>,
    private val settingsRepository: Lazy<SettingsRepository>,
) : LauncherGadgetView(context), TranslationManager.TranslationCallback {

    private val binding = GadgetLauncherTranslatorBinding.inflate(LayoutInflater.from(context), this)

    /**
     * Alive exactly while the view is attached and the launcher is STARTED - it is the base class's own
     * scope, kept rather than a second one, so a translation in flight dies with the cell that asked for
     * it and no lifecycle is invented here.
     */
    private var scope: CoroutineScope? = null

    private var facade: TextTranslationFacade? = null

    private var modelMissing = false
    private var failed = false

    /** Set when the user swaps: the direction the next translation runs in, overriding the detected one. */
    private var forcedSource: String? = null
    private var forcedTarget: String? = null

    init {
        binding.gadgetTranslatorInput.setOnEditorActionListener { _, actionId, _ ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE
            if (isDone) {
                translate(binding.gadgetTranslatorInput.text?.toString().orEmpty())
            }
            isDone
        }
        // The clipboard is read here and nowhere else - never on attach, never on a tick, never when the
        // cell becomes visible. Reading it silently is what the owner's decision of 2026-08-17 rules out
        // while still paying one tap for the common case (strategic §6 item 9).
        binding.gadgetTranslatorPaste.setOnClickListener {
            val pasted = clipboardText()
            binding.gadgetTranslatorInput.setText(pasted)
            translate(pasted)
        }
        binding.gadgetTranslatorSwap.setOnClickListener { swapDirection() }
        binding.gadgetTranslatorDirection.setOnClickListener { openLanguageSettings() }
        renderState(TranslatorState.EMPTY_INPUT)
    }

    override suspend fun CoroutineScope.onActive() {
        scope = this
        renderPairCaption()
        try {
            awaitCancellation()
        } finally {
            scope = null
            facade?.release()
            facade = null
        }
    }

    /** The caption is the settings entry, so it carries the saved pair before any input exists. */
    private suspend fun renderPairCaption() {
        val (source, target) = effectivePair()
        showPair(source, target)
    }

    /**
     * The pair the next translation runs in: the user's swap, else the program translation settings -
     * the single source the owner ruled by (2026-08-29). "auto" passes through verbatim because the
     * engine branch that detects is keyed on that literal, and the mapper would flatten it to "en".
     */
    private suspend fun effectivePair(): Pair<String, String> =
        forcedSource?.let { source -> source to (forcedTarget ?: FALLBACK_LANG) } ?: run {
            val settings = settingsRepository.get().getSettings().first()
            val rawSource = settings.translationSourceLanguage
            val source = if (rawSource == AUTO_LANG) {
                AUTO_LANG
            } else {
                TranslationManager.languageCodeToMLKit(rawSource)
            }
            source to TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
        }

    private fun showPair(source: String, target: String) {
        binding.gadgetTranslatorDirection.text = context.getString(
            R.string.launcher_translator_direction,
            source,
            target,
        )
    }

    /**
     * The caption opens the dialog where the pair lives and where the translation engine gets
     * enabled - the surface a pair-less or engine-less cell was missing. Applying settings there
     * drops the session swap, so what the user just saved is what runs next.
     */
    private fun openLanguageSettings() {
        Timber.d("S2237: language settings requested")
        val owner = findViewTreeLifecycleOwner() ?: return
        TranslationSettingsDialog.show(
            context = context,
            lifecycleOwner = owner,
            settingsRepository = settingsRepository.get(),
        ) {
            forcedSource = null
            forcedTarget = null
            scope?.launch { renderPairCaption() }
        }
    }

    private fun clipboardText(): String {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return ""
        val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        return item?.coerceToText(context)?.toString().orEmpty()
    }

    /**
     * Only ever called from a user action - never from a timer or a refresh tick, because the engine is
     * the expensive part of this cell and strategic §3.2 caps it at explicit use.
     */
    private fun translate(text: String) {
        if (text.isBlank()) {
            renderState(TranslatorState.EMPTY_INPUT)
            return
        }
        val activeScope = scope ?: return
        modelMissing = false
        failed = false
        activeScope.launch {
            val engine = facade ?: facadeFactory.get().create(this@TranslatorGadgetView).also { facade = it }
            val (source, target) = effectivePair()
            Timber.d("S2237: translate pair %s to %s", source, target)
            showPair(source, target)
            val translated = runCatching { engine.translate(text, source, target) }
                .onFailure {
                    failed = true
                    Timber.w("Translator cell: engine refused (%s)", it.javaClass.simpleName)
                }
                .getOrNull()
            if (translated != null) {
                binding.gadgetTranslatorResult.text = translated
            }
            val state = decideTranslatorState(text, translated, modelMissing, failed)
            renderState(state)
        }
    }

    /**
     * Swaps the pair for the session. The last result stays on screen: it is still a true translation
     * of what was typed, and blanking it would punish a mis-tap.
     */
    private fun swapDirection() {
        Timber.d("S2237: swap requested")
        val activeScope = scope ?: return
        activeScope.launch {
            val (source, target) = effectivePair()
            forcedSource = target
            forcedTarget = source
            showPair(target, source)
        }
    }

    /**
     * The last result always stays under whatever this writes - the degradation discipline the map cell
     * and the now-playing card already follow (strategic §5.1 pillar C): a live cell never becomes an
     * empty square because something failed.
     */
    private fun renderState(state: TranslatorState) {
        val message = when (state) {
            TranslatorState.EMPTY_INPUT -> R.string.launcher_translator_empty
            TranslatorState.MODEL_MISSING -> R.string.launcher_translator_model_missing
            TranslatorState.PAIR_UNAVAILABLE -> R.string.launcher_translator_unavailable
            TranslatorState.FAILED -> R.string.launcher_translator_unavailable
            TranslatorState.TRANSLATED -> R.string.launcher_translator_attribution
        }
        binding.gadgetTranslatorState.setText(message)
        binding.gadgetTranslatorState.isVisible = true
    }

    /** Engine failure, reported through the contract's own callback rather than a channel of our own. */
    override fun showError(message: String) {
        failed = true
        renderState(TranslatorState.FAILED)
    }

    /**
     * The missing language pack: the state goes in the cell, and the download is asked for first.
     *
     * The phase-04 audit rejected the shorter version of this - render the state and confirm on the
     * user's behalf. The backend downloads with `DownloadConditions.Builder().build()`, whose own comment
     * records that the Wi-Fi restriction is deliberately absent, and the pack is about 30 MB, so one tap
     * on a home-screen cell would have pulled 30 MB over whatever connection was at hand. Every other
     * consumer of this callback - the camera screen, the document viewer, the player - asks first, and a
     * cell must not be the one surface that does not. Cancelling leaves the state on screen and downloads
     * nothing; the next translation asks again.
     */
    override fun showModelDownloadPrompt(
        languageName: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) {
        modelMissing = true
        renderState(TranslatorState.MODEL_MISSING)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.download_translation_model_title)
            .setMessage(context.getString(R.string.download_translation_model_message, languageName))
            .setPositiveButton(R.string.download) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .showBoundToHost(context)
    }

    private companion object {
        /** The settings' auto-detect value: passed through to the engine and shown as-is. */
        const val AUTO_LANG = "auto"

        /** The engine's own default axis - every pair it supports translates through English. */
        const val FALLBACK_LANG = "en"
    }
}
