package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.utils.CharsetDetector
import com.sza.fastmediasorter.utils.SyntaxHighlighter
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val root: View,
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
    // S0704: unified-player spinner owner. Non-null only in the unified PlayerActivity; null in the
    // standalone activities, where the bar keeps its single reactive driver and these helpers fall
    // back to direct progressBar writes.
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator? = null,
) {

    companion object {
        // Font size limits (in sp)
        private const val MIN_FONT_SIZE_SP = 6f
        private const val MAX_FONT_SIZE_SP = 72f
        private const val DEFAULT_TEXT_FONT_SIZE_SP = 14f
        private const val DEFAULT_TRANSLATION_FONT_SIZE_SP = 14f

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
        fun launchEditorCalculator(initialInput: String)
        fun launchSelectionCalculator(initialInput: String)
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
    private val calculatorEnabledFlow = MutableStateFlow(false)
    // S0189 Phase 07: auto-fit font manager; created fresh on each enterEditMode
    private var autoFitFontManager: TextEditorAutoFitFontManager? = null
    private val safeViews = PlayerBindingSafeViews(root)

    // S1549: not a `by lazy` - it binds concrete editor-toolbar views, and a lazy would keep handing
    // out the discarded ones after a re-inflate, leaving Save/Cancel dead in the new layout.
    private var _actionPanelManager: com.sza.fastmediasorter.ui.editor.actions.EditorActionPanel? = null
    private val actionPanelManager: com.sza.fastmediasorter.ui.editor.actions.EditorActionPanel
        get() = _actionPanelManager ?: buildActionPanelManager().also { _actionPanelManager = it }

    private fun buildActionPanelManager(): com.sza.fastmediasorter.ui.editor.actions.EditorActionPanel {
        return com.sza.fastmediasorter.ui.editor.actions.EditorActionPanelBinder(
            buttons = com.sza.fastmediasorter.ui.editor.actions.EditorActionButtons(
                save = safeViews.btnEditorSave,
                saveClose = safeViews.btnEditorSaveClose,
                saveSend = safeViews.btnEditorSaveSend,
                sendKeep = safeViews.btnEditorSendKeep,
                more = safeViews.btnEditorMore,
                cancel = safeViews.btnEditorCancel,
            ),
            // S0189: dirty-state tint applies to the top editor toolbar (action buttons live there now).
            hostView = safeViews.editorToolbar,
            calculatorEnabled = calculatorEnabledFlow,
            isDirty = dirtyTracker.isDirty,
            coroutineScope = coroutineScope,
            cleanColor = com.sza.fastmediasorter.ui.editor.dirty.DirtyToolbarTinter.TRANSPARENT_PRESERVE_ORIGINAL,
            dirtyColor = android.graphics.Color.parseColor("#992C2C"),
        )
    }

    /**
     * S1549: re-point at a re-inflated hierarchy. The loaded text, the current page and the editor
     * buffer stay in this instance; every delegate reads through the shared [safeViews], so
     * re-pointing that one object covers them all. The caller re-renders the current page after.
     */
    fun rebindLayoutRoot(newRoot: View) {
        safeViews.rebindRoot(newRoot)
        _actionPanelManager = null
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
        safeViews = safeViews,
        getTextFontSizeSp = { textFontSizeSp },
        getTypeface = { currentTypeface },
        getTextGestureDetector = { textGestureDetector },
        resetTranslationState = { translationOverlayManager.resetState() },
        setTouchZonesEnabled = callback::setTouchZonesEnabled,
        loadingIndicatorCoordinator = loadingIndicatorCoordinator,
    )
    private val searchManager = TextViewerSearchManager(safeViews)

    fun setupControls() {
        // Setup gesture detectors for font size adjustment
        setupGestureDetectors()

        coroutineScope.launch {
            settingsRepository.getSettings()
                .map { it.enableCalculator }
                .distinctUntilChanged()
                .collect { enabled ->
                    calculatorEnabledFlow.value = enabled
                }
        }

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
                ttsManager?.stop()
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
        safeViews.btnCopyTextCmd.setOnClickListener {
            val text = safeViews.tvTextContent.text.toString()
            if (text.isNotEmpty()) {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
            }
        }

        safeViews.btnEditTextCmd.setOnClickListener {
            enterEditMode()
        }

        // S0189: editor action panel callbacks - extracted to TextEditorActionPanelCallbacks.
        actionPanelManager.setup(
            TextEditorActionPanelCallbacks(
                safeViews = safeViews,
                getSaveFlow = { saveFlow },
                getCurrentLocalFile = { currentLocalFile },
                getTextNoteStagingRegistry = { textNoteStagingRegistry },
                saveDialogDefaultName = ::saveDialogDefaultName,
                cacheNewlySavedNote = ::cacheNewlySavedNote,
                rebaselineDirtyTracker = dirtyTracker::rebaseline,
                isDirty = { dirtyTracker.isDirty.value },
                saveEditedText = ::saveEditedText,
                sendTo = ::openSendToMenuForText,
                openCalculator = callback::launchEditorCalculator,
                finishActivity = callback::finishActivity,
                exitEditMode = ::exitEditMode,
            ).build()
        )

        // Editor toolbar buttons - delegated
        findReplaceManager.setupEditorToolbar()

        safeViews.btnTranslateTextCmd.setOnClickListener {
            translationOverlayManager.toggleTranslation { originalTextForTranslation() }
        }
        safeViews.btnTranslateTextCmd.setOnLongClickListener {
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
                onSearchGoogle = { openGoogleSearch(context, it) },
                isCalculatorAvailable = { calculatorEnabledFlow.value },
                onOpenCalculator = callback::launchSelectionCalculator,
            )

        // Editor (EditText) selection: append "Calculator" to the platform selection menu.
        // Reuses the editor calculator round-trip so the result is inserted after the selection.
        safeViews.etTextContent.customSelectionActionModeCallback =
            EditorSelectionActionModeCallback(
                isCalculatorAvailable = { calculatorEnabledFlow.value },
                getSelectedText = {
                    val editable = safeViews.etTextContent.text
                    val start = safeViews.etTextContent.selectionStart.coerceAtLeast(0)
                    val end = safeViews.etTextContent.selectionEnd.coerceAtLeast(0)
                    editable?.substring(minOf(start, end), maxOf(start, end)) ?: ""
                },
                onOpenCalculator = callback::launchEditorCalculator,
            )
    }

    /** Setup gesture detectors for horizontal swipe to change font size + vertical swipe for page nav. Delegated to [TextViewerGestureDetectors]. */
    private fun setupGestureDetectors() {
        textGestureDetector = TextViewerGestureDetectors.buildTextDetector(
            context = context,
            root = root,
            safeViews = safeViews,
            getCurrentFile = { currentFile },
            getTextFilePager = { textFilePager },
            onIncreaseTextFontSize = ::increaseTextFontSize,
            onDecreaseTextFontSize = ::decreaseTextFontSize,
            onHideOcrText = ::hideOcrText,
            onNextPage = ::nextPage,
            onPreviousPage = ::previousPage,
            onExitFullscreenMode = callback::exitFullscreenMode,
        )
        translationGestureDetector = TextViewerGestureDetectors.buildTranslationDetector(
            context = context,
            root = root,
            onIncreaseTranslationFontSize = ::increaseTranslationFontSize,
            onDecreaseTranslationFontSize = ::decreaseTranslationFontSize,
            onToggleOverlaySize = translationOverlayManager::toggleOverlaySize,
        )
    }

    /** Apply font settings from session configuration. Called when user changes font settings in translation settings dialog. */
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

    }

    private fun increaseTextFontSize() {
        val baseSizeSp = if (safeViews.textEditContainer.isVisible) {
            autoFitFontManager?.currentFontSizeSp() ?: textFontSizeSp
        } else {
            textFontSizeSp
        }
        textFontSizeSp = FontResizeController.increase(baseSizeSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        applyTextFontSize()
        // S0189 Phase 07: manual swipe overrides auto-fit until next edit-mode open
        autoFitFontManager?.notifyManualOverride(textFontSizeSp)
        logFontResizeGesture(textFontSizeSp)
    }

    private fun decreaseTextFontSize() {
        val baseSizeSp = if (safeViews.textEditContainer.isVisible) {
            autoFitFontManager?.currentFontSizeSp() ?: textFontSizeSp
        } else {
            textFontSizeSp
        }
        textFontSizeSp = FontResizeController.decrease(baseSizeSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        applyTextFontSize()
        // S0189 Phase 07: manual swipe overrides auto-fit until next edit-mode open
        autoFitFontManager?.notifyManualOverride(textFontSizeSp)
        logFontResizeGesture(textFontSizeSp)
    }

    private fun applyTextFontSize() {
        safeViews.tvTextContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
        safeViews.etTextContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
    }

    private fun increaseTranslationFontSize() {
        translationFontSizeSp =
            FontResizeController.increase(translationFontSizeSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        applyTranslationFontSize()
        logFontResizeGesture(translationFontSizeSp)
    }

    private fun decreaseTranslationFontSize() {
        translationFontSizeSp =
            FontResizeController.decrease(translationFontSizeSp, MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        applyTranslationFontSize()
        logFontResizeGesture(translationFontSizeSp)
    }

    private fun applyTranslationFontSize() {
        safeViews.tvTranslatedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, translationFontSizeSp)
    }

    /** Apply translation font size (called from PlayerActivity for image translation). Uses the same font size setting as text translation to keep consistency. */
    fun applyTranslationFontSizeForImageTranslation() {
        applyTranslationFontSize()
    }

    // S0760: the per-step size Toast was removed (it arrived too late to help); the proportional
    // step is large enough to be self-evident. Gesture analytics are kept.
    private fun logFontResizeGesture(sizeSp: Float) {
        UserActionLogger.logGesture("FontSizeChange", "TextViewerManager", "size=${sizeSp.toInt()}sp")
    }

    private val viewerLoader by lazy {
        TextViewerLoader(
            context = context,
            safeViews = safeViews,
            coroutineScope = coroutineScope,
            settingsRepository = settingsRepository,
            networkFileManager = networkFileManager,
            textNoteStagingRegistry = textNoteStagingRegistry,
            applyTextFontSize = ::applyTextFontSize,
            closePager = ::closePager,
            showError = callback::showError,
            setCurrentLocalFile = { currentLocalFile = it },
            setOriginalTextWithoutNumbers = { originalTextWithoutNumbers = it },
            setMarkdownRendered = { markdownRendered = it },
            setSyntaxHighlightingEnabled = { syntaxHighlightingEnabled = it },
            setCurrentReaderTheme = { currentReaderTheme = it },
            setCurrentCharset = { currentCharset = it },
            setTextFilePager = { textFilePager = it },
            resolveTheme = ::resolveTheme,
            renderPageContent = ::renderPageContent,
            updatePageIndicator = ::updatePageIndicator,
            isAutoOpenEditMode = { autoOpenEditMode },
            clearAutoOpenEditMode = { autoOpenEditMode = false },
            enterEditMode = { autoOpen -> enterEditMode(autoOpen) },
            loadingIndicatorCoordinator = loadingIndicatorCoordinator,
            onTextCopyClicked = {
                val text = safeViews.tvTextContent.text.toString()
                if (text.isNotEmpty()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
                    Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    fun displayText(mediaFile: MediaFile, isWritable: Boolean) {
        // Stop in-flight read-aloud before swapping the source (TTS reads originalTextWithoutNumbers).
        ttsManager?.stop()
        currentFile = mediaFile
        viewerLoader.load(mediaFile, isWritable)
    }

    fun insertCalculatorResult(result: String) {
        if (result.isBlank() || !calculatorEnabledFlow.value) return
        val editable = safeViews.etTextContent.text
        val insertAt = safeViews.etTextContent.selectionEnd.coerceIn(0, editable.length)
        editable.insert(insertAt, result)
        safeViews.etTextContent.setSelection(insertAt + result.length)
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

    /** Reopen current file with a different encoding. */
    fun reopenWithEncoding(charset: Charset) {
        val file = currentLocalFile ?: return
        currentFile ?: return

        // Re-read replaces the buffer TTS is speaking; stop first.
        ttsManager?.stop()
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

    /** Release all resources. Call from Activity onDestroy. */
    fun release() {
        closePager()
        ttsManager?.release()
        ttsManager = null
        undoRedoManager?.detach()
        undoRedoManager = null
        autoSaveManager?.stopAutoSave()
        autoSaveManager = null
    }

    /** Close text viewer triggered by back button press. Performs complete cleanup for text file viewer (NOT for OCR results). This ensures single back-press exits, not double. Called from PlayerLifecycleManager.setupBackPressHandler() when back is pressed while text viewer is active. */
    fun closeTextViewerFromBackPress() {
        if (currentFile != null) {
            ttsManager?.stop()
            closePager()
            safeViews.textViewerContainer.isVisible = false
            safeViews.textScrollView.isVisible = false
            safeViews.textPageNavigation.isVisible = false
            safeViews.tvTextContent.text = ""
            currentFile = null
            // NOTE: Do NOT call exitFullscreenMode() here - it only adjusts UI state.
            // Back handler will call exitPlayerWithAudioCheck() to actually navigate back.
        }
    }

    // ===== H.2: Rich rendering & reader UI methods =====

    /** Toggle markdown rendering for .md files. Switches between raw text and Markwon-rendered view, then reloads current page. */
    fun toggleMarkdownRendering() {
        markdownRendered = !markdownRendered
        coroutineScope.launch(Dispatchers.IO) {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(markdownRendered = markdownRendered))
        }
        reloadCurrentPage()
    }

    /** Apply reader theme (background & text color) to the text viewer. Saves preference to settings. */
    fun applyReaderTheme(theme: TextReaderTheme) {
        currentReaderTheme = theme
        coroutineScope.launch(Dispatchers.IO) {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(textReaderTheme = theme.name))
        }
        applyThemeToViews()
    }

    /** Get current reader theme. */
    fun getCurrentTheme(): TextReaderTheme = currentReaderTheme

    /** Resolve reader theme by name. "SYSTEM" picks DARK or LIGHT based on the device dark-mode setting; any unrecognized name also falls back to the system default. */
    private fun resolveTheme(name: String): TextReaderTheme {
        if (name.equals("SYSTEM", ignoreCase = true)) {
            val isNight = (context.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return if (isNight) TextReaderTheme.DARK else TextReaderTheme.LIGHT
        }
        return TextReaderTheme.entries.find { it.name.equals(name, ignoreCase = true) }
            ?: resolveTheme("SYSTEM")
    }

    /** Toggle TTS read-aloud for current page text. */
    fun toggleReadAloud() {
        if (ttsManager == null) {
            ttsManager = TtsReadAloudManager(context) { state ->
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

    /** Render page content with Markwon, syntax highlighting, and theme applied. */
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
            val palette = com.sza.fastmediasorter.utils.SyntaxPalette.forBackground(currentReaderTheme.bgColor)
            val highlighted = SyntaxHighlighter.highlight(displayText, ext, palette)
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

    /**
     * S1549: re-render the held page after a layout re-inflate - the pager, page index and editor
     * buffer live in this instance, only the views it renders into were replaced.
     */
    fun rerenderAfterRebind() {
        if (textFilePager == null) return
        reloadCurrentPage()
        updatePageIndicator()
    }

    // ===== H.3: Editor enter/exit/save =====

    /** S0189: signal that edit mode should be activated automatically once text content loads. Called by [PlayerActivity] when launched with [PlayerActivity.EXTRA_TEXT_EDIT_MODE_ON_OPEN]. */
    fun setAutoOpenEditMode(enabled: Boolean) {
        autoOpenEditMode = enabled
    }

    /** Enter text edit mode. [autoOpen] distinguishes automatic (S0189 create flow) from manual entry. */
    private val editorModeController by lazy {
        TextEditorModeController(
            context = context,
            safeViews = safeViews,
            coroutineScope = coroutineScope,
            settingsRepository = settingsRepository,
            networkFileManager = networkFileManager,
            actionPanelManager = actionPanelManager,
            findReplaceManager = findReplaceManager,
            dirtyTracker = dirtyTracker,
            editContentFlow = editContentFlow,
            dirtyTextWatcher = dirtyTextWatcher,
            defaultTextFontSizeSp = DEFAULT_TEXT_FONT_SIZE_SP,
            getCurrentFile = { currentFile },
            getOriginalTextWithoutNumbers = { originalTextWithoutNumbers },
            setOriginalTextWithoutNumbers = { originalTextWithoutNumbers = it },
            getTextFilePager = { textFilePager },
            getAutoSaveManager = { autoSaveManager },
            setAutoSaveManager = { autoSaveManager = it },
            getAutoFitFontManager = { autoFitFontManager },
            setAutoFitFontManager = { autoFitFontManager = it },
            getUndoRedoManager = { undoRedoManager },
            setUndoRedoManager = { undoRedoManager = it },
            applyLineNumbers = ::applyLineNumbers,
            showError = callback::showError,
            loadingIndicatorCoordinator = loadingIndicatorCoordinator,
        )
    }

    internal fun enterEditMode(autoOpen: Boolean = false) = editorModeController.enterEditMode(autoOpen)

    /** S0189: after a successful Save of a new note, append the resulting file directly to [com.sza.fastmediasorter.core.cache.MediaFilesCacheManager] for its resource. Browse's `onResume` Reconciler (S0242 Phase 03) sees no pending journal entry for the new note, so the cache append is the canonical signal - the new entry appears in the Browse list on next resume without triggering a full network rescan. Skipped for non-staged edits (the file already exists in the resource list). */
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

    /** S0189: pre-fill name for the save-with-rename dialog. Network staging files are stored on disk as `<resourceId>_<intendedName>` to keep entries unique inside the shared `Downloads/FastMediaSorter/notes/` directory; without this lookup the resource-id prefix leaks into the SMB/FTP/SFTP/Cloud upload as the final filename. Falls back to the on-disk name for non-registered (already-saved or arbitrary) text files. */
    private fun saveDialogDefaultName(localFile: java.io.File): String {
        val stagedNote = textNoteStagingRegistry?.lookup(localFile)
        return stagedNote?.intendedName ?: localFile.name
    }

    private fun exitEditMode() = editorModeController.exitEditMode()

    private fun saveEditedText() = editorModeController.saveEditedText()

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

    /** Force enable translation and translate current text. Used when settings are changed via long-press dialog. */
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
        safeViews.btnTranslateTextCmd.alpha = if (enabled) 1.0f else 0.55f
    }

    fun updateTranslationButtonIcon(sourceLang: String, targetLang: String) {
        // Clear XML tint first - otherwise selector_player_button_tint (white)
        // colours the entire drawable and makes the badge text invisible.
        safeViews.btnTranslateTextCmd.imageTintList = null
        val drawable = LanguageBadgeDrawable(context, sourceLang, targetLang)
        safeViews.btnTranslateTextCmd.setImageDrawable(drawable)
        safeViews.btnTranslateTextCmd.alpha =
            if (translationOverlayManager.isEnabled) 1.0f else 0.55f
    }

    // ===== OCR / translated text display =====

    fun displayOcrText(text: String) {
        ttsManager?.stop()
        currentFile = null
        originalTextWithoutNumbers = text
        ocrDisplayManager.displayOcrText(text)
    }

    fun hideOcrText() {
        ttsManager?.stop()
        currentFile = null
        ocrDisplayManager.hideOcrText()
    }

    fun displayTranslatedText(text: String) {
        ttsManager?.stop()
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

    /**
     * S0459: open the unified «Send to..» menu for [text] (text/plain). Routed from the editor
     * toolbar's single outbound action; the menu's receiver registry self-gates (system Share,
     * Keep-text, Email, ..), so this surface no longer owns per-target wiring.
     */
    private fun openSendToMenuForText(text: String) {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: run {
            Timber.w("text editor host is not a FragmentActivity - cannot open send-to menu")
            return
        }
        val sendToMenuManager = dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, SendToMenuEntryPoint::class.java)
            .sendToMenuManager()
        val content = com.sza.fastmediasorter.core.share.ShareableContent(
            uris = emptyList(),
            mime = "text/plain",
            mediaType = com.sza.fastmediasorter.domain.model.MediaType.TEXT,
            text = text,
        )
        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            sendToMenuManager.show(activity, content, settings)
        }
    }

    /** Returns the text to translate: original page text without line numbers, or displayed text. */
    private fun originalTextForTranslation(): String =
        originalTextWithoutNumbers.ifBlank { safeViews.tvTextContent.text.toString() }

    /** S0459: app-scoped accessor for [SendToMenuManager] (Singleton) from the manually-built viewer. */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    internal interface SendToMenuEntryPoint {
        fun sendToMenuManager(): com.sza.fastmediasorter.ui.share.SendToMenuManager
    }
}
