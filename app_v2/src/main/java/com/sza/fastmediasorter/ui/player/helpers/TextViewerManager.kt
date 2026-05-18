package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.utils.CharsetDetector
import com.sza.fastmediasorter.utils.SyntaxHighlighter
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import com.sza.fastmediasorter.utils.UserActionLogger
import java.nio.charset.Charset
import kotlin.math.abs

/**
 * Manages text viewing/editing in PlayerActivity:
 * - Shows text viewer UI
 * - Loads text files (local/network/cloud via NetworkFileManager)
 * - Supports copy-to-clipboard and in-place edit/save (when resource is writable)
 * - Supports translation of text content via TranslationManager
 * - Supports dynamic font size adjustment via horizontal swipe gestures
 *
 * Find & Replace / editor toolbar: delegated to [TextEditorFindReplaceManager].
 * Translation overlay: delegated to [TextTranslationOverlayManager].
 */
@android.annotation.SuppressLint("SetTextI18n")
class TextViewerManager(
    private val context: Context,
    private val binding: ActivityPlayerUnifiedBinding,
    private val networkFileManager: NetworkFileManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: TextViewerCallback,
    private val translationManager: TranslationManager,
    // S0189 Phase 06: orchestrates save-with-rename dialog; null = fallback to legacy saveEditedText()
    private val saveFlow: TextEditorSaveFlow? = null,
    // S0189: registry of deferred new-note intents. Non-null in panel PlayerActivity,
    // null in StandalonePlayerActivity (which never creates notes).
    private val textNoteStagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry? = null,
) {

    companion object {
        // Font size limits (in sp)
        private const val MIN_FONT_SIZE_SP = 6f
        private const val MAX_FONT_SIZE_SP = 72f
        private const val DEFAULT_TEXT_FONT_SIZE_SP = 14f
        private const val DEFAULT_TRANSLATION_FONT_SIZE_SP = 14f
        private const val FONT_SIZE_STEP_SP = 2f

        // Swipe threshold as percentage of screen dimension
        private const val SWIPE_THRESHOLD_PERCENT = 0.05f // 5% of screen width/height
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    interface TextViewerCallback {
        fun showError(message: String)
        fun showTranslationSettingsDialog()
        fun exitFullscreenMode()
        fun setTouchZonesEnabled(enabled: Boolean)
        fun showEncodingDialog()
        // S0189: invoked by Save & Close to return the user to Browse with the new file.
        fun finishActivity()
    }

    private var currentFile: MediaFile? = null
    private var currentLocalFile: java.io.File? = null

    // Paged reader
    private var textFilePager: TextFilePager? = null
    private var currentCharset: Charset = Charsets.UTF_8

    // Markwon renderer (lazy init)
    private val markwon: Markwon by lazy { Markwon.create(context) }
    private var markdownRendered = true
    private var syntaxHighlightingEnabled = true

    // Reader theme - defaults to DARK on night-mode devices, LIGHT otherwise
    private var currentReaderTheme: TextReaderTheme = resolveTheme("SYSTEM")

    // TTS
    private var ttsManager: TtsReadAloudManager? = null

    // Editor: undo/redo + auto-save
    private var undoRedoManager: TextUndoRedoManager? = null
    private var autoSaveManager: TextEditorAutoSaveManager? = null

    // Dynamic font sizes (session-scoped, persist until user exits player)
    private var textFontSizeSp: Float = DEFAULT_TEXT_FONT_SIZE_SP
    private var translationFontSizeSp: Float = DEFAULT_TRANSLATION_FONT_SIZE_SP

    // Current font family (loaded from settings, applied to all text views)
    private var currentTypeface: android.graphics.Typeface = android.graphics.Typeface.SANS_SERIF

    // Store original text without line numbers for editing/translation
    private var originalTextWithoutNumbers: String = ""

    // Gesture detectors for font size adjustment
    private lateinit var textGestureDetector: GestureDetector
    private lateinit var translationGestureDetector: GestureDetector

    // S0189: when true, enterEditMode() is called automatically after text content loads
    private var autoOpenEditMode = false

    // S0189 Phase 09: action panel + dirty-state tracker built on the shared modules.
    // [editContentFlow] mirrors the EditText content; [dirtyTextWatcher] feeds it on every change.
    private val editContentFlow = kotlinx.coroutines.flow.MutableStateFlow("")
    private val dirtyTracker = com.sza.fastmediasorter.ui.editor.dirty.EditorDirtyStateTracker<String>(
        contentFlow = editContentFlow,
        initialBaseline = "",
    ).also { it.start(coroutineScope) }
    private val dirtyTextWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) {
            editContentFlow.value = s?.toString().orEmpty()
        }
    }
    private val keepChecker = com.sza.fastmediasorter.util.GoogleKeepAvailabilityChecker(context)
    // S0189 Phase 07: auto-fit font manager; created fresh on each enterEditMode
    private var autoFitFontManager: TextEditorAutoFitFontManager? = null
    private val safeViews = PlayerBindingSafeViews(binding)
    private val actionPanelManager: com.sza.fastmediasorter.ui.editor.actions.EditorActionPanel by lazy {
        com.sza.fastmediasorter.ui.editor.actions.EditorActionPanelBinder(
            buttons = com.sza.fastmediasorter.ui.editor.actions.EditorActionButtons(
                save = safeViews.btnEditorSave,
                saveClose = safeViews.btnEditorSaveClose,
                saveSend = safeViews.btnEditorSaveSend,
                sendKeep = safeViews.btnEditorSendKeep,
                cancel = safeViews.btnEditorCancel,
            ),
            // S0189: dirty-state tint applies to the top editor toolbar (action buttons live there now).
            hostView = safeViews.editorToolbar,
            keepAvailable = keepChecker.isKeepAvailable(),
            isDirty = dirtyTracker.isDirty,
            coroutineScope = coroutineScope,
            cleanColor = com.sza.fastmediasorter.ui.editor.dirty.DirtyToolbarTinter.TRANSPARENT_PRESERVE_ORIGINAL,
            dirtyColor = android.graphics.Color.parseColor("#992C2C"),
        )
    }

    // Delegated helpers
    private val findReplaceManager = TextEditorFindReplaceManager(
        context = context,
        safeViews = safeViews,
        undoRedoProvider = { undoRedoManager }
    )

    private val translationOverlayManager = TextTranslationOverlayManager(
        context = context,
        safeViews = safeViews,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        translationManager = translationManager,
        getTranslationFontSizeSp = { translationFontSizeSp },
        applyTranslationFontSize = ::applyTranslationFontSize,
        callback = object : TextTranslationOverlayManager.TranslationCallback {
            override fun showError(message: String) = callback.showError(message)
            override fun showTranslationSettingsDialog() =
                callback.showTranslationSettingsDialog()
            override fun onTranslationToggled(enabled: Boolean) =
                updateTranslateButtonTint(enabled)
        }
    )
    private val ocrDisplayManager = TextOcrDisplayManager(
        binding = binding,
        safeViews = safeViews,
        getTextFontSizeSp = { textFontSizeSp },
        getTypeface = { currentTypeface },
        getTextGestureDetector = { textGestureDetector },
        resetTranslationState = { translationOverlayManager.resetState() },
        setTouchZonesEnabled = callback::setTouchZonesEnabled,
    )
    private val searchManager = TextViewerSearchManager(safeViews)

    fun setupControls() {
        // Setup gesture detectors for font size adjustment
        setupGestureDetectors()

        // Page navigation buttons
        safeViews.btnTextPagePrev.setOnClickListener {
            UserActionLogger.logButtonClick("TextPagePrev", "TextViewerManager")
            previousPage()
        }
        safeViews.btnTextPageNext.setOnClickListener {
            UserActionLogger.logButtonClick("TextPageNext", "TextViewerManager")
            nextPage()
        }
        // Long-press encoding indicator to re-open with different encoding
        safeViews.tvTextEncodingIndicator.setOnClickListener {
            callback.showEncodingDialog()
        }

        // Close button for text viewer (OCR result or text file)
        safeViews.btnCloseTextViewer.setOnClickListener {
            UserActionLogger.logButtonClick("CloseTextViewer", "TextViewerManager")
            if (currentFile == null) {
                // OCR result - just hide and restore image/video view
                hideOcrText()
            } else {
                // Text file - hide and exit fullscreen
                closePager()
                safeViews.textViewerContainer.isVisible = false
                safeViews.textScrollView.isVisible = false
                safeViews.textPageNavigation.isVisible = false
                safeViews.tvTextContent.text = ""
                currentFile = null
                callback.exitFullscreenMode()
            }
        }

        // Close button for translation overlay
        safeViews.btnCloseTranslation.setOnClickListener {
            UserActionLogger.logButtonClick("CloseTranslation", "TextViewerManager")
            translationOverlayManager.hideOverlay()
        }

        // Click on background to close translation overlay
        safeViews.translationOverlayBackground.setOnClickListener {
            Timber.d("BUTTON: translationOverlayBackground clicked - hiding translation overlay")
            translationOverlayManager.hideOverlay()
        }

        // Setup translation overlay click to expand/collapse + swipe for font size
        safeViews.translationOverlay.setOnClickListener {
            translationOverlayManager.toggleOverlaySize()
        }

        // Setup translation overlay touch listener for horizontal swipe gestures
        safeViews.translationScrollView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            val handled = translationGestureDetector.onTouchEvent(event)
            // Let ScrollView handle vertical scrolling if gesture wasn't a horizontal swipe
            if (!handled) {
                v.onTouchEvent(event)
            }
            true
        }

        // Setup text viewer touch listener for horizontal swipe gestures
        safeViews.textScrollView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            textGestureDetector.onTouchEvent(event)
            false // Let ScrollView handle scrolling
        }

        // Edit mode uses a different surface than the read-only viewer, so gestures must be
        // attached to the EditText itself or horizontal swipes never reach textGestureDetector.
        safeViews.etTextContent.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            textGestureDetector.onTouchEvent(event)
            false // Keep native EditText selection/scroll behaviour intact.
        }

        // Text action buttons (now in top command panel)
        binding.btnCopyTextCmd.setOnClickListener {
            val text = safeViews.tvTextContent.text.toString()
            if (text.isNotEmpty()) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEditTextCmd.setOnClickListener {
            enterEditMode()
        }

        // S0189: 5-action panel - replaces the former 2-button row
        actionPanelManager.setup(com.sza.fastmediasorter.ui.editor.actions.EditorActionCallbacks(
            onSave = {
                val flow = saveFlow
                val localFile = currentLocalFile
                val capturedContent = safeViews.etTextContent.text.toString()
                if (flow != null && localFile != null) {
                    flow.commit(
                        currentLocalFile = localFile,
                        currentName = saveDialogDefaultName(localFile),
                        currentContent = capturedContent,
                        afterSave = { outcome ->
                            cacheNewlySavedNote(outcome, capturedContent)
                            // S0189: reset dirty-state - Save & Close on a clean buffer must skip
                            // the redundant re-save (which would orphan a file in the staging dir).
                            dirtyTracker.rebaseline(capturedContent)
                        }
                    )
                } else {
                    saveEditedText()
                    dirtyTracker.rebaseline(capturedContent)
                }
            },
            onSaveAndClose = saveAndClose@{
                val flow = saveFlow
                val localFile = currentLocalFile
                val capturedContent = safeViews.etTextContent.text.toString()
                // S0189: if the buffer is already clean (user did Save then Save & Close), skip the
                // duplicate save and just return to Browse. Avoids orphan staging files and the
                // "blank viewer after save" state when the activity stayed open.
                if (!dirtyTracker.isDirty.value) {
                    callback.finishActivity()
                    return@saveAndClose
                }
                if (flow != null && localFile != null) {
                    flow.commit(
                        currentLocalFile = localFile,
                        currentName = saveDialogDefaultName(localFile),
                        currentContent = capturedContent,
                        afterSave = { outcome ->
                            cacheNewlySavedNote(outcome, capturedContent)
                            dirtyTracker.rebaseline(capturedContent)
                            callback.finishActivity()
                        }
                    )
                } else {
                    saveEditedText()
                    dirtyTracker.rebaseline(capturedContent)
                    callback.finishActivity()
                }
            },
            onSaveAndSend = {
                val flow = saveFlow
                val localFile = currentLocalFile
                val capturedContent = safeViews.etTextContent.text.toString()
                if (flow != null && localFile != null) {
                    flow.commit(
                        currentLocalFile = localFile,
                        currentName = saveDialogDefaultName(localFile),
                        currentContent = capturedContent,
                        afterSave = { outcome ->
                            cacheNewlySavedNote(outcome, capturedContent)
                            val shareFile = java.io.File(outcome.finalPath)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                shareFile
                            )
                            com.sza.fastmediasorter.core.share.SystemShareInvoker.invoke(
                                context = context,
                                payload = com.sza.fastmediasorter.core.share.SharePayload.Text(
                                    content = capturedContent,
                                    streamUri = uri,
                                    grantReadPermission = true,
                                ),
                                chooserTitle = context.getString(com.sza.fastmediasorter.R.string.share),
                            )
                        }
                    )
                } else {
                    saveEditedText()
                    com.sza.fastmediasorter.core.share.SystemShareInvoker.invoke(
                        context = context,
                        payload = com.sza.fastmediasorter.core.share.SharePayload.Text(content = capturedContent),
                        chooserTitle = context.getString(com.sza.fastmediasorter.R.string.share),
                    )
                }
            },
            onSendToKeep = sendKeep@{
                val currentText = safeViews.etTextContent.text.toString()
                val keepPackage = keepChecker.resolveTargetPackage() ?: run {
                    android.widget.Toast.makeText(context, com.sza.fastmediasorter.R.string.text_editor_keep_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                    return@sendKeep
                }
                val sent = com.sza.fastmediasorter.core.share.SystemShareInvoker.invoke(
                    context = context,
                    payload = com.sza.fastmediasorter.core.share.SharePayload.Text(content = currentText),
                    preferredPackage = keepPackage,
                )
                if (!sent) {
                    android.widget.Toast.makeText(context, com.sza.fastmediasorter.R.string.text_editor_keep_unavailable, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onCancel = {
                // S0189: if this was a deferred new-note that auto-save already flushed to disk,
                // delete the file and drop the registry entry so Cancel leaves no trace.
                val localFile = currentLocalFile
                if (localFile != null) {
                    val stagedNote = textNoteStagingRegistry?.lookup(localFile)
                    if (stagedNote != null) {
                        if (localFile.exists()) {
                            localFile.delete()
                        }
                        textNoteStagingRegistry.unregister(localFile)
                    }
                }
                exitEditMode()
            },
        ))

        // Editor toolbar buttons - delegated
        findReplaceManager.setupEditorToolbar()

        binding.btnTranslateTextCmd.setOnClickListener {
            translationOverlayManager.toggleTranslation { originalTextForTranslation() }
        }
        binding.btnTranslateTextCmd.setOnLongClickListener {
            callback.showTranslationSettingsDialog()
            true
        }

        // Click outside OCR text to dismiss (tap on container background, not on text itself)
        safeViews.textViewerContainer.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                // Only dismiss if showing OCR text (currentFile is null for OCR)
                if (currentFile == null && safeViews.textViewerContainer.isVisible) {
                    val textViewLocation = IntArray(2)
                    safeViews.tvTextContent.getLocationOnScreen(textViewLocation)
                    val textViewRect = android.graphics.Rect(
                        textViewLocation[0],
                        textViewLocation[1],
                        textViewLocation[0] + safeViews.tvTextContent.width,
                        textViewLocation[1] + safeViews.tvTextContent.height
                    )

                    val containerLocation = IntArray(2)
                    safeViews.textViewerContainer.getLocationOnScreen(containerLocation)
                    val touchX = containerLocation[0] + event.x.toInt()
                    val touchY = containerLocation[1] + event.y.toInt()

                    if (!textViewRect.contains(touchX, touchY)) {
                        hideOcrText()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        // Extend the native selection ActionMode with "Translate" and "Search in Google"
        safeViews.tvTextContent.customSelectionActionModeCallback =
            DocumentSelectionActionModeCallback(
                showTranslate = BuildConfig.ENABLE_TRANSLATION,
                getSelectedText = {
                    val start = safeViews.tvTextContent.selectionStart.coerceAtLeast(0)
                    val end = safeViews.tvTextContent.selectionEnd.coerceAtLeast(0)
                    safeViews.tvTextContent.text
                        ?.substring(minOf(start, end), maxOf(start, end)) ?: ""
                },
                onTranslate = { translationOverlayManager.translateSelectedText(it) },
                onSearchGoogle = { openGoogleSearch(context, it) }
            )
    }

    /**
     * Setup gesture detectors for horizontal swipe to change font size
     */
    private fun setupGestureDetectors() {
        // Gesture detector for text content (tvTextContent)
        textGestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val screenWidth = binding.root.width
                    val screenHeight = binding.root.height
                    val swipeThreshold =
                        (minOf(screenWidth, screenHeight) * SWIPE_THRESHOLD_PERCENT)
                            .toInt().coerceAtLeast(50)

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    // Horizontal swipe → font size adjustment
                    if (abs(diffX) > abs(diffY) &&
                        abs(diffX) > swipeThreshold &&
                        abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        if (diffX > 0) increaseTextFontSize() else decreaseTextFontSize()
                        return true
                    }

                    // In edit mode, vertical gestures must remain plain text scrolling.
                    if (safeViews.textEditContainer.isVisible) {
                        return false
                    }

                    // Vertical swipe → page navigation or fullscreen exit
                    if (abs(diffY) > abs(diffX) &&
                        abs(diffY) > swipeThreshold &&
                        abs(velocityY) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        val scrollView = safeViews.textScrollView
                        val isAtTop = !scrollView.canScrollVertically(-1)
                        val isAtBottom = !scrollView.canScrollVertically(1)

                        // For OCR text (currentFile is null) close only at scroll edges
                        if (currentFile == null && safeViews.textViewerContainer.isVisible) {
                            if (diffY < 0 && isAtBottom) {
                                Timber.d("OCR text: Swipe up at bottom - closing OCR viewer")
                                hideOcrText()
                                return true
                            } else if (diffY > 0 && isAtTop) {
                                Timber.d("OCR text: Swipe down at top - closing OCR viewer")
                                hideOcrText()
                                return true
                            }
                            return false
                        }

                        val pager = textFilePager
                        if (diffY < 0 && isAtBottom) {
                            if (pager != null && pager.hasNextPage()) {
                                Timber.d("Text: Swipe up at bottom - next page")
                                nextPage()
                                return true
                            }
                            Timber.d("Text: Swipe up at bottom - exit fullscreen")
                            callback.exitFullscreenMode()
                            return true
                        } else if (diffY > 0 && isAtTop) {
                            if (pager != null && pager.hasPreviousPage()) {
                                Timber.d("Text: Swipe down at top - previous page")
                                previousPage()
                                return true
                            }
                            Timber.d("Text: Swipe down at top - exit fullscreen")
                            callback.exitFullscreenMode()
                            return true
                        }
                    }

                    return false
                }
            })

        // Gesture detector for translation overlay (tvTranslatedText)
        translationGestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val screenWidth = binding.root.width
                    val screenHeight = binding.root.height
                    val swipeThreshold =
                        (minOf(screenWidth, screenHeight) * SWIPE_THRESHOLD_PERCENT)
                            .toInt().coerceAtLeast(50)

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    // Only handle horizontal swipes (ignore vertical scrolling)
                    if (abs(diffX) > abs(diffY) &&
                        abs(diffX) > swipeThreshold &&
                        abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        if (diffX > 0) increaseTranslationFontSize()
                        else decreaseTranslationFontSize()
                        return true
                    }
                    return false
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    translationOverlayManager.toggleOverlaySize()
                    return true
                }
            })
    }

    /**
     * Apply font settings from session configuration.
     * Called when user changes font settings in translation settings dialog.
     */
    fun applyFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
        val baseTextSize = DEFAULT_TEXT_FONT_SIZE_SP
        val baseTranslationSize = DEFAULT_TRANSLATION_FONT_SIZE_SP

        if (settings.fontSize != com.sza.fastmediasorter.domain.models.TranslationFontSize.AUTO) {
            textFontSizeSp = (baseTextSize * settings.fontSize.multiplier)
                .coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            translationFontSizeSp = (baseTranslationSize * settings.fontSize.multiplier)
                .coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            applyTextFontSize()
            applyTranslationFontSize()
        } else {
            // AUTO mode - reset to defaults
            textFontSizeSp = DEFAULT_TEXT_FONT_SIZE_SP
            translationFontSizeSp = DEFAULT_TRANSLATION_FONT_SIZE_SP
            applyTextFontSize()
            applyTranslationFontSize()
        }

        // Apply font family and save to class variable for later use (e.g., displayOcrText)
        currentTypeface = when (settings.fontFamily) {
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.SERIF ->
                android.graphics.Typeface.SERIF
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.MONOSPACE ->
                android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.SANS_SERIF
        }
        safeViews.tvTextContent.typeface = currentTypeface
        safeViews.tvTranslatedText.typeface = currentTypeface

        Timber.d(
            "Applied font settings: size=${settings.fontSize.name} " +
                    "(${textFontSizeSp}sp), family=${settings.fontFamily.name}"
        )
    }

    private fun increaseTextFontSize() {
        val baseSizeSp = if (safeViews.textEditContainer.isVisible) {
            autoFitFontManager?.currentFontSizeSp() ?: textFontSizeSp
        } else {
            textFontSizeSp
        }
        textFontSizeSp = (baseSizeSp + FONT_SIZE_STEP_SP).coerceAtMost(MAX_FONT_SIZE_SP)
        applyTextFontSize()
        // S0189 Phase 07: manual swipe overrides auto-fit until next edit-mode open
        autoFitFontManager?.notifyManualOverride(textFontSizeSp)
        Timber.d("Text font size increased to ${textFontSizeSp}sp")
        showFontSizeToast(textFontSizeSp)
    }

    private fun decreaseTextFontSize() {
        val baseSizeSp = if (safeViews.textEditContainer.isVisible) {
            autoFitFontManager?.currentFontSizeSp() ?: textFontSizeSp
        } else {
            textFontSizeSp
        }
        textFontSizeSp = (baseSizeSp - FONT_SIZE_STEP_SP).coerceAtLeast(MIN_FONT_SIZE_SP)
        applyTextFontSize()
        // S0189 Phase 07: manual swipe overrides auto-fit until next edit-mode open
        autoFitFontManager?.notifyManualOverride(textFontSizeSp)
        Timber.d("Text font size decreased to ${textFontSizeSp}sp")
        showFontSizeToast(textFontSizeSp)
    }

    private fun applyTextFontSize() {
        safeViews.tvTextContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
        safeViews.etTextContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
    }

    private fun increaseTranslationFontSize() {
        translationFontSizeSp =
            (translationFontSizeSp + FONT_SIZE_STEP_SP).coerceAtMost(MAX_FONT_SIZE_SP)
        applyTranslationFontSize()
        Timber.d("Translation font size increased to ${translationFontSizeSp}sp")
        showFontSizeToast(translationFontSizeSp)
    }

    private fun decreaseTranslationFontSize() {
        translationFontSizeSp =
            (translationFontSizeSp - FONT_SIZE_STEP_SP).coerceAtLeast(MIN_FONT_SIZE_SP)
        applyTranslationFontSize()
        Timber.d("Translation font size decreased to ${translationFontSizeSp}sp")
        showFontSizeToast(translationFontSizeSp)
    }

    private fun applyTranslationFontSize() {
        safeViews.tvTranslatedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, translationFontSizeSp)
    }

    /**
     * Apply translation font size (called from PlayerActivity for image translation).
     * Uses the same font size setting as text translation to keep consistency.
     */
    fun applyTranslationFontSizeForImageTranslation() {
        applyTranslationFontSize()
    }

    private fun showFontSizeToast(sizeSp: Float) {
        UserActionLogger.logGesture("FontSizeChange", "TextViewerManager", "size=${sizeSp.toInt()}sp")
        Toast.makeText(context, "${sizeSp.toInt()}sp", Toast.LENGTH_SHORT).show()
    }

    fun displayText(mediaFile: MediaFile, isWritable: Boolean) {
        currentFile = mediaFile

        // Close previous pager
        closePager()

        binding.imageView.isVisible = false
        binding.photoView.isVisible = false
        binding.playerView.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.btnTranslateImage.isVisible = false

        // Hide PDF action buttons (they are for PDF files only)
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false

        // Hide EPUB action buttons (they are for EPUB files only)
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false

        // Hide EPUB WebView and controls (they are for EPUB files only)
        binding.epubWebView.isVisible = false
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false

        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        safeViews.tvTextContent.text = ""
        binding.progressBar.isVisible = true

        // Show text action buttons in command panel
        binding.btnCopyTextCmd.isVisible = true
        // Restore text-copy handler (may have been overridden by PdfViewerManager)
        binding.btnCopyTextCmd.setOnClickListener {
            val text = safeViews.tvTextContent.text.toString()
            if (text.isNotEmpty()) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnSearchTextCmd.isVisible = true

        // Apply saved font size (persists during session)
        applyTextFontSize()

        binding.btnEditTextCmd.isVisible = isWritable

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()

            withContext(Dispatchers.Main) {
                // Show translate button only if translation is enabled in settings AND supported by flavor
                binding.btnTranslateTextCmd.isVisible =
                    BuildConfig.ENABLE_TRANSLATION && settings.enableTranslation
            }
            try {
                // S0189: a new note may be registered as deferred - the file is created on first
                // Save, not when the editor opens. Skip the not-found error in that case and
                // render an empty buffer; auto-open edit mode is the next step.
                val deferredStaged = textNoteStagingRegistry?.lookup(java.io.File(mediaFile.path))
                val file = if (deferredStaged != null) {
                    deferredStaged.localFile
                } else {
                    try {
                        networkFileManager.prepareFileForRead(mediaFile)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.isVisible = false
                            callback.showError(context.getString(R.string.text_file_load_failed))
                        }
                        return@launch
                    }
                }

                if (!file.exists() && deferredStaged == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        callback.showError(context.getString(R.string.text_file_not_found))
                    }
                    return@launch
                }

                if (!file.exists()) {
                    // Deferred new note - render an empty buffer without the pager (no bytes to page).
                    currentLocalFile = file
                    originalTextWithoutNumbers = ""
                    val settings = settingsRepository.getSettings().first()
                    markdownRendered = settings.markdownRendered
                    syntaxHighlightingEnabled = settings.syntaxHighlighting
                    currentReaderTheme = resolveTheme(settings.textReaderTheme)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        renderPageContent("", settings.showTextLineNumbers, 1)
                        safeViews.textPageNavigation.isVisible = false
                        safeViews.tvTextEncodingIndicator.text = Charsets.UTF_8.name()
                        if (autoOpenEditMode) {
                            autoOpenEditMode = false
                            enterEditMode(autoOpen = true)
                        }
                    }
                    return@launch
                }

                // Check file size against maximum (100MB)
                if (file.length() > TextFilePager.MAX_FILE_SIZE) {
                    val fileSizeMb = "%.1f MB".format(file.length().toDouble() / (1024 * 1024))
                    val maxSizeMb =
                        "%.0f MB".format(TextFilePager.MAX_FILE_SIZE.toDouble() / (1024 * 1024))
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        safeViews.tvTextContent.text =
                            context.getString(R.string.text_file_too_large, fileSizeMb, maxSizeMb)
                        safeViews.textPageNavigation.isVisible = false
                    }
                    return@launch
                }

                // Detect charset
                currentCharset = CharsetDetector.detect(file)
                currentLocalFile = file

                // Create pager and open file
                val pager = TextFilePager(file, currentCharset)
                pager.open()
                textFilePager = pager

                // Read first page
                val pageText = pager.readPage(0)
                originalTextWithoutNumbers = pageText

                // Load rendering settings
                markdownRendered = settings.markdownRendered
                syntaxHighlightingEnabled = settings.syntaxHighlighting
                currentReaderTheme = resolveTheme(settings.textReaderTheme)

                val startLine = pager.getStartLineNumber(0)

                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false

                    // Render with Markwon/syntax/theme support
                    renderPageContent(pageText, settings.showTextLineNumbers, startLine)

                    // Show/hide page navigation
                    val multiPage = !pager.isSinglePage()
                    safeViews.textPageNavigation.isVisible = multiPage
                    if (multiPage) {
                        updatePageIndicator()
                        // Add bottom padding so page bar doesn't cover content
                        safeViews.textScrollView.setPadding(
                            safeViews.textScrollView.paddingLeft,
                            safeViews.textScrollView.paddingTop,
                            safeViews.textScrollView.paddingRight,
                            (48 * context.resources.displayMetrics.density).toInt()
                        )
                        // Disable edit for multi-page files
                        binding.btnEditTextCmd.isVisible = false
                    } else {
                        safeViews.textScrollView.setPadding(
                            safeViews.textScrollView.paddingLeft,
                            safeViews.textScrollView.paddingTop,
                            safeViews.textScrollView.paddingRight,
                            0
                        )
                    }

                    // Show encoding indicator
                    safeViews.tvTextEncodingIndicator.text = currentCharset.name()

                    Timber.d(
                        "TextViewerManager: Displaying page 0, " +
                                "${pageText.length} chars, charset=$currentCharset"
                    )

                    // S0189: auto-open edit mode when launched from the "create text note" flow
                    if (autoOpenEditMode) {
                        autoOpenEditMode = false
                        enterEditMode(autoOpen = true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading text file")
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    callback.showError(context.getString(R.string.text_file_display_error))
                }
            }
        }
    }

    /**
     * Apply line numbers to text content.
     * @param text Raw text content
     * @param showLineNumbers Whether to add line numbers
     * @param startLineNumber Starting line number (1-based)
     */
    private fun applyLineNumbers(
        text: String,
        showLineNumbers: Boolean,
        startLineNumber: Int
    ): String {
        if (!showLineNumbers || text.isEmpty()) return text

        val lines = text.lines()
        val maxLineNum = startLineNumber + lines.size - 1
        val numWidth = maxLineNum.toString().length

        return lines.mapIndexed { index, line ->
            val lineNum = (startLineNumber + index).toString().padStart(numWidth, ' ')
            "$lineNum │ $line"
        }.joinToString("\n")
    }

    fun nextPage() {
        val pager = textFilePager ?: return
        if (!pager.hasNextPage()) return

        // Stop TTS when changing pages
        ttsManager?.stop()

        coroutineScope.launch(Dispatchers.IO) {
            val pageText = pager.readPage(pager.currentPage + 1)
            originalTextWithoutNumbers = pageText

            val settings = settingsRepository.getSettings().first()
            val startLine = pager.getStartLineNumber(pager.currentPage)

            withContext(Dispatchers.Main) {
                renderPageContent(pageText, settings.showTextLineNumbers, startLine)
                safeViews.textScrollView.scrollTo(0, 0)
                updatePageIndicator()
            }
        }
    }

    fun previousPage() {
        val pager = textFilePager ?: return
        if (!pager.hasPreviousPage()) return

        // Stop TTS when changing pages
        ttsManager?.stop()

        coroutineScope.launch(Dispatchers.IO) {
            val pageText = pager.readPage(pager.currentPage - 1)
            originalTextWithoutNumbers = pageText

            val settings = settingsRepository.getSettings().first()
            val startLine = pager.getStartLineNumber(pager.currentPage)

            withContext(Dispatchers.Main) {
                renderPageContent(pageText, settings.showTextLineNumbers, startLine)
                safeViews.textScrollView.scrollTo(0, 0)
                updatePageIndicator()
            }
        }
    }

    /**
     * Reopen current file with a different encoding.
     */
    fun reopenWithEncoding(charset: Charset) {
        val file = currentLocalFile ?: return
        currentFile ?: return

        closePager()
        currentCharset = charset

        try {
            val pager = TextFilePager(file, charset)
            pager.open()
            textFilePager = pager
        } catch (e: Exception) {
            Timber.e(e, "TextViewerManager: Failed to reopen with charset=$charset")
            callback.showError(context.getString(R.string.text_file_reopen_failed))
            return
        }

        val activePager = textFilePager ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val pageText = activePager.readPage(0)
            originalTextWithoutNumbers = pageText

            val settings = settingsRepository.getSettings().first()
            val startLine = activePager.getStartLineNumber(0)

            withContext(Dispatchers.Main) {
                renderPageContent(pageText, settings.showTextLineNumbers, startLine)
                safeViews.textScrollView.scrollTo(0, 0)
                safeViews.tvTextEncodingIndicator.text = charset.name()
                updatePageIndicator()
                Timber.d("TextViewerManager: Re-opened with charset=$charset")
            }
        }
    }

    /** Get the list of supported charsets for the encoding picker. */
    fun getSupportedCharsets(): List<Pair<String, Charset>> = CharsetDetector.SUPPORTED_CHARSETS

    /** Get current charset name for display. */
    fun getCurrentCharsetName(): String = currentCharset.name()

    private fun updatePageIndicator() {
        val pager = textFilePager ?: return
        val current = pager.currentPage + 1
        val total = pager.getEstimatedPageCount()

        val indicatorText = if (pager.isFullyIndexed()) {
            context.getString(R.string.text_page_indicator, current, total)
        } else {
            context.getString(R.string.text_page_indicator_estimated, current, total)
        }
        safeViews.tvTextPageIndicator.text = indicatorText
        safeViews.btnTextPagePrev.isEnabled = pager.hasPreviousPage()
        safeViews.btnTextPageNext.isEnabled = pager.hasNextPage()
        safeViews.btnTextPagePrev.alpha = if (pager.hasPreviousPage()) 1f else 0.3f
        safeViews.btnTextPageNext.alpha = if (pager.hasNextPage()) 1f else 0.3f
    }

    private fun closePager() {
        textFilePager?.close()
        textFilePager = null
        currentLocalFile = null
    }

    /**
     * Release all resources. Call from Activity onDestroy.
     */
    fun release() {
        closePager()
        ttsManager?.release()
        ttsManager = null
        undoRedoManager?.detach()
        undoRedoManager = null
        autoSaveManager?.stopAutoSave()
        autoSaveManager = null
    }

    /**
     * Close text viewer triggered by back button press.
     * Performs complete cleanup for text file viewer (NOT for OCR results).
     * This ensures single back-press exits, not double.
     * Called from PlayerLifecycleManager.setupBackPressHandler() when back is pressed while text viewer is active.
     */
    fun closeTextViewerFromBackPress() {
        if (currentFile != null) {
            closePager()
            safeViews.textViewerContainer.isVisible = false
            safeViews.textScrollView.isVisible = false
            safeViews.textPageNavigation.isVisible = false
            safeViews.tvTextContent.text = ""
            currentFile = null
            // NOTE: Do NOT call exitFullscreenMode() here - it only adjusts UI state.
            // Back handler will call exitPlayerWithAudioCheck() to actually navigate back.
            Timber.d("TextViewerManager: Text file closed via back button press")
        }
    }

    // ===== H.2: Rich rendering & reader UI methods =====

    /**
     * Toggle markdown rendering for .md files.
     * Switches between raw text and Markwon-rendered view, then reloads current page.
     */
    fun toggleMarkdownRendering() {
        markdownRendered = !markdownRendered
        coroutineScope.launch(Dispatchers.IO) {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(markdownRendered = markdownRendered))
        }
        reloadCurrentPage()
        Timber.d("TextViewerManager: Markdown rendering toggled to $markdownRendered")
    }

    /**
     * Apply reader theme (background & text color) to the text viewer.
     * Saves preference to settings.
     */
    fun applyReaderTheme(theme: TextReaderTheme) {
        currentReaderTheme = theme
        coroutineScope.launch(Dispatchers.IO) {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(textReaderTheme = theme.name))
        }
        applyThemeToViews()
        Timber.d("TextViewerManager: Reader theme changed to ${theme.name}")
    }

    /** Get current reader theme. */
    fun getCurrentTheme(): TextReaderTheme = currentReaderTheme

    /**
     * Resolve reader theme by name. "SYSTEM" picks DARK or LIGHT based on the device dark-mode
     * setting; any unrecognized name also falls back to the system default.
     */
    private fun resolveTheme(name: String): TextReaderTheme {
        if (name.equals("SYSTEM", ignoreCase = true)) {
            val isNight = (context.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return if (isNight) TextReaderTheme.DARK else TextReaderTheme.LIGHT
        }
        return TextReaderTheme.entries.find { it.name.equals(name, ignoreCase = true) }
            ?: resolveTheme("SYSTEM")
    }

    /**
     * Toggle TTS read-aloud for current page text.
     */
    fun toggleReadAloud() {
        if (ttsManager == null) {
            ttsManager = TtsReadAloudManager(context) { state ->
                Timber.d("TextViewerManager: TTS state changed to $state")
            }
        }
        ttsManager?.toggle(originalTextWithoutNumbers)
    }

    private fun isMarkdownFile(): Boolean {
        val ext = currentFile?.path?.substringAfterLast('.', "")?.lowercase() ?: ""
        return ext == "md" || ext == "markdown" || ext == "mdown"
    }

    private fun getFileExtension(): String =
        currentFile?.path?.substringAfterLast('.', "")?.lowercase() ?: ""

    private fun applyThemeToViews() {
        safeViews.tvTextContent.setBackgroundColor(currentReaderTheme.bgColor)
        safeViews.tvTextContent.setTextColor(currentReaderTheme.textColor)
        safeViews.textScrollView.setBackgroundColor(currentReaderTheme.bgColor)
    }

    /**
     * Render page content with Markwon, syntax highlighting, and theme applied.
     */
    private fun renderPageContent(
        pageText: String,
        showLineNumbers: Boolean,
        startLineNumber: Int
    ) {
        if (pageText.isEmpty()) {
            // S0189: leave the viewer blank for empty files (new notes start blank). The
            // previous "File is empty" placeholder leaked into the editor as initial text.
            safeViews.tvTextContent.text = ""
            return
        }

        val ext = getFileExtension()

        // 1. Markdown rendering (raw text, no line numbers)
        if (isMarkdownFile() && markdownRendered) {
            markwon.setMarkdown(safeViews.tvTextContent, pageText)
            applyThemeToViews()
            return
        }

        // 2. Syntax highlighting for code files
        if (syntaxHighlightingEnabled && SyntaxHighlighter.isSupported(ext)) {
            val displayText = applyLineNumbers(pageText, showLineNumbers, startLineNumber)
            val highlighted = SyntaxHighlighter.highlight(displayText, ext)
            if (highlighted != null) {
                safeViews.tvTextContent.text = highlighted
                applyThemeToViews()
                return
            }
        }

        // 3. Plain text with line numbers
        val displayText = applyLineNumbers(pageText, showLineNumbers, startLineNumber)
        safeViews.tvTextContent.text = displayText
        applyThemeToViews()
    }

    private fun reloadCurrentPage() {
        val pager = textFilePager ?: return
        coroutineScope.launch(Dispatchers.IO) {
            val pageText = pager.readPage(pager.currentPage)
            originalTextWithoutNumbers = pageText
            val settings = settingsRepository.getSettings().first()
            val startLine = pager.getStartLineNumber(pager.currentPage)
            withContext(Dispatchers.Main) {
                renderPageContent(pageText, settings.showTextLineNumbers, startLine)
            }
        }
    }

    // ===== H.3: Editor enter/exit/save =====

    /**
     * S0189: signal that edit mode should be activated automatically once text content loads.
     * Called by [PlayerActivity] when launched with [PlayerActivity.EXTRA_TEXT_EDIT_MODE_ON_OPEN].
     */
    fun setAutoOpenEditMode(enabled: Boolean) {
        autoOpenEditMode = enabled
    }

    /** Enter text edit mode. [autoOpen] distinguishes automatic (S0189 create flow) from manual entry. */
    internal fun enterEditMode(autoOpen: Boolean = false) {
        val pager = textFilePager
        if (pager != null && !pager.isSinglePage()) {
            Toast.makeText(context, R.string.text_editing_large_file, Toast.LENGTH_SHORT).show()
            return
        }

        var textToEdit = originalTextWithoutNumbers.ifBlank {
            safeViews.tvTextContent.text.toString()
        }

        // Check for auto-save draft
        val filePath = currentFile?.path ?: ""
        if (autoSaveManager == null) {
            val tempDir = java.io.File(context.filesDir.parentFile, "temp")
            autoSaveManager = TextEditorAutoSaveManager(tempDir, coroutineScope)
        }
        val draft = autoSaveManager?.restoreDraft(filePath)
        if (draft != null && draft != textToEdit) {
            textToEdit = draft
            Toast.makeText(context, R.string.draft_restored, Toast.LENGTH_LONG).show()
        }

        safeViews.etTextContent.setText(textToEdit)

        safeViews.textScrollView.isVisible = false
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        safeViews.textEditContainer.isVisible = true
        safeViews.etTextContent.requestFocus()

        // S0189 Phase 09: bind dirty-state tracker to the EditText and reset panel tint.
        // The text watcher pumps content into [editContentFlow]; the tracker compares against
        // the baseline we just set via [rebaseline].
        safeViews.etTextContent.removeTextChangedListener(dirtyTextWatcher)
        editContentFlow.value = textToEdit
        dirtyTracker.rebaseline(textToEdit)
        safeViews.etTextContent.addTextChangedListener(dirtyTextWatcher)
        actionPanelManager.onEnterEditMode()

        // S0189 Phase 07: auto-fit font - uses max of persistent setting; locks on manual swipe
        val maxFontSp = DEFAULT_TEXT_FONT_SIZE_SP *
            com.sza.fastmediasorter.domain.models.TranslationFontSize.HUGE.multiplier
        autoFitFontManager?.detach()
        autoFitFontManager = TextEditorAutoFitFontManager(
            editText = safeViews.etTextContent,
            scrollView = safeViews.textEditScrollView,
            maxSizeSp = maxFontSp
        ).also {
            it.attach()
            it.reset()
        }

        // Detach previous undo/redo manager if exists (M-10 fix)
        undoRedoManager?.detach()

        undoRedoManager = TextUndoRedoManager(safeViews.etTextContent) { canUndo, canRedo ->
            safeViews.btnUndo.alpha = if (canUndo) 1f else 0.3f
            safeViews.btnUndo.isEnabled = canUndo
            safeViews.btnRedo.alpha = if (canRedo) 1f else 0.3f
            safeViews.btnRedo.isEnabled = canRedo
        }
        undoRedoManager?.attach()

        autoSaveManager?.startAutoSave(safeViews.etTextContent, filePath)

        // Cursor position tracking - delegated
        findReplaceManager.setupCursorPositionTracking()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(safeViews.etTextContent, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * S0189: after a successful Save of a new note, append the resulting file directly to
     * [com.sza.fastmediasorter.core.cache.MediaFilesCacheManager] for its resource. Browse's
     * `onResume` Reconciler (S0242 Phase 03) sees no pending journal entry for the new
     * note, so the cache append is the canonical signal - the new entry appears in the
     * Browse list on next resume without triggering a full network rescan.
     *
     * Skipped for non-staged edits (the file already exists in the resource list).
     */
    private fun cacheNewlySavedNote(outcome: com.sza.fastmediasorter.domain.usecase.SaveTextNoteUseCase.SaveOutcome, content: String) {
        val resourceId = currentFile?.resourceId ?: return
        val previousLocalFile = currentLocalFile ?: return
        // Only act for a deferred-staged note. For arbitrary text-file edits the cache list
        // already contains this file - adding again would create a duplicate.
        textNoteStagingRegistry?.lookup(previousLocalFile) ?: return
        val newFile = com.sza.fastmediasorter.domain.model.MediaFile(
            name = outcome.finalName,
            path = outcome.finalPath,
            type = com.sza.fastmediasorter.domain.model.MediaType.TEXT,
            size = content.toByteArray(Charsets.UTF_8).size.toLong(),
            createdDate = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            resourceId = resourceId,
        )
        com.sza.fastmediasorter.core.cache.MediaFilesCacheManager.addFile(resourceId, newFile)
    }

    /**
     * S0189: pre-fill name for the save-with-rename dialog.
     *
     * Network staging files are stored on disk as `<resourceId>_<intendedName>` to keep entries
     * unique inside the shared `Downloads/FastMediaSorter/notes/` directory; without this lookup
     * the resource-id prefix leaks into the SMB/FTP/SFTP/Cloud upload as the final filename.
     * Falls back to the on-disk name for non-registered (already-saved or arbitrary) text files.
     */
    private fun saveDialogDefaultName(localFile: java.io.File): String {
        val stagedNote = textNoteStagingRegistry?.lookup(localFile)
        return stagedNote?.intendedName ?: localFile.name
    }

    private fun exitEditMode() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(safeViews.etTextContent.windowToken, 0)

        undoRedoManager?.detach()
        undoRedoManager = null
        autoSaveManager?.stopAutoSave()

        // S0189 Phase 09: detach the text watcher (so subsequent EditText changes don't dirty
        // the tracker once the editor is closed) and reset the panel tint.
        safeViews.etTextContent.removeTextChangedListener(dirtyTextWatcher)
        actionPanelManager.onExitEditMode()

        // S0189 Phase 07: detach auto-fit font manager
        autoFitFontManager?.detach()
        autoFitFontManager = null

        // Close find panel if open - delegated
        findReplaceManager.closeFindPanel()

        safeViews.textEditContainer.isVisible = false
        safeViews.textScrollView.isVisible = true
        binding.btnCopyTextCmd.isVisible = true
        binding.btnEditTextCmd.isVisible = true
        binding.btnSearchTextCmd.isVisible = true
    }

    private fun saveEditedText() {
        val newText = safeViews.etTextContent.text.toString()
        val fileToSave = currentFile

        if (fileToSave == null) {
            callback.showError(context.getString(R.string.text_file_not_found))
            return
        }

        binding.progressBar.isVisible = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val localFile = networkFileManager.prepareFileForWrite(fileToSave)
                if (localFile == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                    }
                    return@launch
                }

                localFile.writeText(newText)
                MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "text-edit")

                val isNetworkFile = !fileToSave.path.startsWith("/")
                if (isNetworkFile) {
                    val uploadSuccess =
                        networkFileManager.uploadEditedFile(fileToSave, localFile)
                    if (!uploadSuccess) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.isVisible = false
                            callback.showError(
                                context.getString(R.string.text_file_upload_failed_after_local_save)
                            )
                        }
                        return@launch
                    }
                    networkFileManager.clearEditingCache()
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    originalTextWithoutNumbers = newText
                }

                // Read settings on IO, not Main (C-3 fix)
                val settings = settingsRepository.getSettings().first()

                withContext(Dispatchers.Main) {
                    val displayText = applyLineNumbers(newText, settings.showTextLineNumbers, 1)
                    safeViews.tvTextContent.text = displayText

                    autoSaveManager?.stopAutoSave(deleteDraft = true)
                    exitEditMode()
                    Toast.makeText(context, R.string.toast_text_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving text file")
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    callback.showError(context.getString(R.string.text_file_save_failed))
                }
            }
        }
    }

    // ===== Scroll helpers =====

    fun scrollDown() {
        val scrollView = safeViews.textScrollView
        scrollView.smoothScrollBy(0, scrollView.height)
    }

    fun scrollUp() {
        val scrollView = safeViews.textScrollView
        scrollView.smoothScrollBy(0, -scrollView.height)
    }

    fun handleMouseWheelScroll(verticalScroll: Float) {
        // Negative because scroll down is negative in MotionEvent
        // Multiply by 100 for reasonable scroll speed
        safeViews.textScrollView.smoothScrollBy(0, (verticalScroll * -100).toInt())
    }

    // ===== Translation public API - delegated =====

    /**
     * Force enable translation and translate current text.
     * Used when settings are changed via long-press dialog.
     */
    fun forceTranslate() {
        translationOverlayManager.forceTranslate { originalTextForTranslation() }
    }

    fun updateCloseButtonVisibility(showCommandPanel: Boolean) {
        // Show close button only in fullscreen mode (when command panel is hidden)
        safeViews.btnCloseTextViewer.isVisible = !showCommandPanel
    }

    private fun updateTranslateButtonTint(enabled: Boolean) {
        // Use alpha instead of imageTintList: tinting with a solid colour destroys
        // the LanguageBadgeDrawable text, making the badge appear as a solid block.
        binding.btnTranslateTextCmd.alpha = if (enabled) 1.0f else 0.55f
    }

    fun updateTranslationButtonIcon(sourceLang: String, targetLang: String) {
        // Clear XML tint first - otherwise selector_player_button_tint (white)
        // colours the entire drawable and makes the badge text invisible.
        binding.btnTranslateTextCmd.imageTintList = null
        val drawable = LanguageBadgeDrawable(context, sourceLang, targetLang)
        binding.btnTranslateTextCmd.setImageDrawable(drawable)
        binding.btnTranslateTextCmd.alpha =
            if (translationOverlayManager.isEnabled) 1.0f else 0.55f
    }

    // ===== OCR / translated text display =====

    fun displayOcrText(text: String) {
        currentFile = null
        ocrDisplayManager.displayOcrText(text)
    }

    fun hideOcrText() {
        currentFile = null
        ocrDisplayManager.hideOcrText()
    }

    fun displayTranslatedText(text: String) {
        currentFile = null
        ocrDisplayManager.displayTranslatedText(text)
    }

    fun searchText(query: String): Int = searchManager.searchText(query)

    fun highlightSearchMatch(query: String, matchIndex: Int) =
        searchManager.highlightSearchMatch(query, matchIndex)

    fun clearSearch() = searchManager.clearSearch()

    fun scrollToTop() = searchManager.scrollToTop()

    fun scrollToBottom() = searchManager.scrollToBottom()

    // ===== Private helpers =====

    /** Returns the text to translate: original page text without line numbers, or displayed text. */
    private fun originalTextForTranslation(): String =
        originalTextWithoutNumbers.ifBlank { safeViews.tvTextContent.text.toString() }
}
