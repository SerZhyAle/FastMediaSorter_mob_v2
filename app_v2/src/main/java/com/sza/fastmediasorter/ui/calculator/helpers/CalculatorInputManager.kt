package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.orientation.isWideLayout
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.ActivityCalculatorBinding
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

class CalculatorInputManager(
    private val binding: ActivityCalculatorBinding,
    private val context: Context,
) {
    private val engine = CalculatorEngine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasReturnableResult = false
    private val historyStore: CalculatorHistoryStore =
        FileCalculatorHistoryStore(File(context.applicationContext.filesDir, HISTORY_FILE_NAME))
    private var persistedHistorySize = 0
    private var historyLoaded = false
    private var initialInputText: String? = null
    private val memoryStore = CalculatorMemoryStore(context)
    private val prankManager = CalculatorAprilFoolsPrankManager(context)
    private var memoryRowExpanded = false

    /**
     * S1549: what a rotation must carry across a rebuilt view hierarchy - the engine's calculation plus the
     * two display-level facts that live here and nowhere else.
     */
    data class State(
        val engine: CalculatorEngine.State,
        val memoryRowExpanded: Boolean,
        val hasReturnableResult: Boolean,
    )

    fun snapshot(): State = State(
        engine = engine.snapshot(),
        memoryRowExpanded = memoryRowExpanded,
        hasReturnableResult = hasReturnableResult,
    )

    fun restore(state: State) {
        engine.restore(state.engine)
        memoryRowExpanded = state.memoryRowExpanded
        hasReturnableResult = state.hasReturnableResult
        render()
    }

    /** S1549: the calculation crosses a rotation-induced recreate through the instance state. */
    fun saveTo(outState: Bundle) {
        val state = snapshot()
        outState.putString(STATE_DISPLAY, state.engine.display)
        outState.putString(STATE_OPERATION_HISTORY, state.engine.operationHistory)
        outState.putStringArrayList(STATE_COMPLETED_HISTORY, ArrayList(state.engine.completedHistory))
        outState.putString(STATE_ACCUMULATOR, state.engine.accumulator)
        outState.putString(STATE_PENDING_OPERATOR, state.engine.pendingOperatorSymbol)
        outState.putString(STATE_REPEAT_OPERATOR, state.engine.repeatOperatorSymbol)
        outState.putString(STATE_REPEAT_OPERAND, state.engine.repeatOperand)
        outState.putBoolean(STATE_START_NEW_INPUT, state.engine.startNewInput)
        outState.putString(STATE_MEMORY, state.engine.memory)
        outState.putBoolean(STATE_MEMORY_ROW_EXPANDED, state.memoryRowExpanded)
        outState.putBoolean(STATE_HAS_RETURNABLE_RESULT, state.hasReturnableResult)
    }

    /** Returns true when a calculation was restored, so the caller knows not to re-apply its intent input. */
    fun restoreFrom(savedState: Bundle?): Boolean {
        val display = savedState?.getString(STATE_DISPLAY) ?: return false
        restore(
            State(
                engine = CalculatorEngine.State(
                    display = display,
                    operationHistory = savedState.getString(STATE_OPERATION_HISTORY).orEmpty(),
                    completedHistory = savedState.getStringArrayList(STATE_COMPLETED_HISTORY).orEmpty(),
                    accumulator = savedState.getString(STATE_ACCUMULATOR),
                    pendingOperatorSymbol = savedState.getString(STATE_PENDING_OPERATOR),
                    repeatOperatorSymbol = savedState.getString(STATE_REPEAT_OPERATOR),
                    repeatOperand = savedState.getString(STATE_REPEAT_OPERAND),
                    startNewInput = savedState.getBoolean(STATE_START_NEW_INPUT, true),
                    memory = savedState.getString(STATE_MEMORY) ?: "0",
                ),
                memoryRowExpanded = savedState.getBoolean(STATE_MEMORY_ROW_EXPANDED, false),
                hasReturnableResult = savedState.getBoolean(STATE_HAS_RETURNABLE_RESULT, false),
            )
        )
        return true
    }

    fun bind() {
        bindDigitButtons()
        binding.btnCalculatorDecimal.setOnClickListener { update { inputDecimal() } }
        binding.btnCalculatorPercent.setOnClickListener { update { inputPercent() } }
        binding.btnCalculatorToggleSign.setOnClickListener { update { toggleSign() } }
        binding.btnCalculatorClear.setOnClickListener { update { clear() } }
        binding.btnCalculatorClearEntry.setOnClickListener { update { clearEntry() } }
        binding.btnCalculatorBackspace.setOnClickListener { update { backspace() } }
        binding.btnCalculatorMenu.setOnClickListener { showCalculatorMenu() }
        binding.btnCalculatorAdd.setOnClickListener { update { inputOperator("+") } }
        binding.btnCalculatorSubtract.setOnClickListener { update { inputOperator("-") } }
        binding.btnCalculatorMultiply.setOnClickListener { update { inputOperator("×") } }
        binding.btnCalculatorDivide.setOnClickListener { update { inputOperator("÷") } }
        binding.btnCalculatorEquals.setOnClickListener { update { inputEquals() } }
        bindMemoryButtons()
        bindLongPressActions()
        render()
        loadPersistedHistory()
        loadPersistedMemory()
    }

    /**
     * S1719: the second action on a key. Every entry reaches the same handler its menu row uses, so a
     * key and its menu twin cannot become two implementations of one function.
     *
     * The keys are listed through the generated binding rather than resolved by id (S1693 forbids
     * `findViewById` here), and each one asks the table what it carries - so a key the table does not
     * name gains no listener at all and keeps exactly today's behaviour. The backspace key is in the
     * list and deliberately absent from the table.
     */
    private fun bindLongPressActions() {
        listOf(
            binding.btnCalculatorZero,
            binding.btnCalculatorOne,
            binding.btnCalculatorTwo,
            binding.btnCalculatorThree,
            binding.btnCalculatorFour,
            binding.btnCalculatorFive,
            binding.btnCalculatorSix,
            binding.btnCalculatorSeven,
            binding.btnCalculatorEight,
            binding.btnCalculatorNine,
            binding.btnCalculatorDecimal,
            binding.btnCalculatorToggleSign,
            binding.btnCalculatorAdd,
            binding.btnCalculatorSubtract,
            binding.btnCalculatorMultiply,
            binding.btnCalculatorDivide,
            binding.btnCalculatorPercent,
            binding.btnCalculatorBackspace,
        ).forEach { key ->
            val action = CalculatorLongPressMap.actionFor(key.id) ?: return@forEach
            key.setOnLongClickListener {
                perform(action)
                // Consumed: without this the key would also deliver its ordinary click, so a held
                // finger on 7 would type a 7 and then take the sine of it.
                true
            }
            showHintOn(key, action.hint)
        }
    }

    /**
     * S1719: writes the key's own symbol and, under it in small type, what a long press does.
     *
     * Rendered into the button's text rather than added as a second view in the layouts: a key is one
     * `MaterialButton` cell of a `GridLayout`, so a separate label would mean wrapping seventeen cells
     * in two layout files and re-deriving their column weights and touch targets. Writing the hint
     * here also means portrait and landscape cannot disagree - there is nothing to keep in step.
     */
    private fun showHintOn(key: MaterialButton, hint: CalculatorLongPressMap.Hint) {
        val hintText = when (hint) {
            is CalculatorLongPressMap.Hint.Notation -> hint.text
            is CalculatorLongPressMap.Hint.Word -> context.getString(hint.res)
        }
        val symbol = key.text?.toString().orEmpty().substringBefore('\n')
        val combined = SpannableString("$symbol\n$hintText")
        combined.setSpan(
            RelativeSizeSpan(HINT_TEXT_SCALE),
            symbol.length + 1,
            combined.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        // The symbol keeps its own line, so re-binding never stacks a second hint on an existing one.
        key.maxLines = HINT_KEY_MAX_LINES
        key.text = combined
    }

    /** S1719: one long-press action, expressed as the call the menu already makes. */
    private fun perform(action: CalculatorLongPressMap.Action) {
        Timber.d("S1719: long press action=${action::class.simpleName}")
        when (action) {
            is CalculatorLongPressMap.Action.Function -> handleMenuItem(action.itemId)
            is CalculatorLongPressMap.Action.MenuCommand -> handleMenuItem(action.itemId)
            // Three ordinary zero presses rather than a new engine call: the shortcut must mean
            // exactly what typing 0 three times means, including whatever the engine does about a
            // leading zero, and a second implementation could only disagree with it.
            CalculatorLongPressMap.Action.TripleZero -> update {
                inputDigit(0)
                inputDigit(0)
                inputDigit(0)
            }
        }
    }

    private fun bindMemoryButtons() {
        binding.btnCalculatorMemoryAdd.setOnClickListener { update { memoryAdd() }.also { persistMemory() } }
        binding.btnCalculatorMemorySubtract.setOnClickListener { update { memorySubtract() }.also { persistMemory() } }
        binding.btnCalculatorMemoryRecall.setOnClickListener { update { memoryRecall() } }
        binding.btnCalculatorMemoryClear.setOnClickListener { update { memoryClear() }.also { persistMemory() } }
        binding.btnCalculatorMemoryToggle.setOnClickListener { toggleMemoryRow() }
    }

    private fun loadPersistedMemory() {
        thread(name = "CalculatorMemoryLoad") {
            val stored = memoryStore.loadMemory()
            val expanded = memoryStore.loadRowExpanded()
            mainHandler.post {
                stored?.toBigDecimalOrNull()?.let { engine.restoreMemory(it) }
                memoryRowExpanded = expanded
                applyMemoryRowState()
                render()
            }
        }
    }

    private fun toggleMemoryRow() {
        memoryRowExpanded = !memoryRowExpanded
        applyMemoryRowState()
        val expanded = memoryRowExpanded
        thread(name = "CalculatorMemoryRowState") { memoryStore.saveRowExpanded(expanded) }
    }

    private fun applyMemoryRowState() {
        binding.calculatorMemoryRow.isVisible = memoryRowExpanded
    }

    private fun persistMemory() {
        val value = engine.memory.toPlainString()
        thread(name = "CalculatorMemorySave") { memoryStore.saveMemory(value) }
    }

    private fun loadPersistedHistory() {
        thread(name = "CalculatorHistoryLoad") {
            val entries = historyStore.load()
            mainHandler.post {
                engine.restoreHistory(entries)
                persistedHistorySize = entries.size
                historyLoaded = true
                applyPendingInitialInput()
                render()
            }
        }
    }

    // Called by the Activity right after bind(); the actual evaluation is deferred until persisted
    // history has loaded, so a selection result is appended after the restored entries and persisted.
    fun applyInitialInput(text: String?) {
        initialInputText = text
        if (historyLoaded) applyPendingInitialInput()
    }

    private fun applyPendingInitialInput() {
        val initialText = initialInputText?.trim().orEmpty()
        initialInputText = null
        if (initialText.isBlank() || !engine.canParseInput(initialText)) return
        update { inputNumber(initialText) }
    }

    fun currentResultOrNull(): String? =
        engine.display.takeIf { hasReturnableResult && engine.error == null && it.isNotBlank() }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val digit = digitFor(event.keyCode)
        if (digit != null) {
            update { inputDigit(digit) }
            return true
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_NUMPAD_DOT -> consume { inputDecimal() }
            KeyEvent.KEYCODE_PLUS,
            KeyEvent.KEYCODE_NUMPAD_ADD -> consume { inputOperator("+") }
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> consume { inputOperator("-") }
            KeyEvent.KEYCODE_STAR,
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> consume { inputOperator("×") }
            KeyEvent.KEYCODE_SLASH,
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> consume { inputOperator("÷") }
            KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> consume { inputEquals() }
            KeyEvent.KEYCODE_DEL -> consume { backspace() }
            KeyEvent.KEYCODE_ESCAPE -> consume { clear() }
            else -> false
        }
    }

    fun render() {
        binding.calculatorDisplay.text = when (engine.error) {
            CalculatorEngine.CalculatorError.DIVISION_BY_ZERO ->
                context.getString(R.string.calculator_error_division_by_zero)
            CalculatorEngine.CalculatorError.MATH_DOMAIN ->
                context.getString(R.string.calculator_error_math_domain)
            null -> engine.display
        }
        binding.calculatorHistory.text = buildVisibleHistory()
        binding.calculatorHistoryScroll.post {
            binding.calculatorHistoryScroll.fullScroll(View.FOCUS_DOWN)
        }
        renderMemoryIndicator()
    }

    private fun renderMemoryIndicator() {
        val hasMemory = engine.memory.compareTo(java.math.BigDecimal.ZERO) != 0
        binding.calculatorMemoryIndicator.isVisible = hasMemory
        if (hasMemory) {
            binding.calculatorMemoryIndicator.text =
                context.getString(R.string.calculator_memory_indicator, engine.memoryDisplay)
        }
    }

    private fun buildVisibleHistory(): String {
        val entries = engine.calculationHistory.toMutableList()
        val currentOperation = engine.operationHistory
        if (currentOperation.isNotBlank()) {
            val completedCurrentOperation = "$currentOperation${engine.display}"
            if (entries.lastOrNull() != completedCurrentOperation) {
                // S1241: while the right operand is being typed, show what "=" would give. The
                // engine returns null for an operation with no answer yet - typing `1000 ÷ 0` on the
                // way to `1000 ÷ 0.1` - so the line simply stays as it was rather than flashing an
                // error the user has not asked for.
                val preview = engine.previewResult()
                entries += if (preview != null) "$currentOperation = $preview" else currentOperation
            }
        }
        return entries.joinToString(separator = "\n")
    }

    private fun showCalculatorMenu() {
        PopupMenu(context, binding.btnCalculatorMenu).apply {
            menu.add(0, MENU_COPY, 0, R.string.copy)
            menu.add(0, MENU_PASTE, 1, R.string.calculator_action_paste)
            menu.add(0, MENU_ROUND, 2, R.string.calculator_action_round)
            menu.add(0, MENU_FUNCTION, 3, context.getString(R.string.calculator_action_function) + "  ▸")
            menu.add(0, MENU_SHARE_RESULT, 4, R.string.calculator_action_share_result)
            menu.add(0, MENU_SAVE_HISTORY, 5, R.string.calculator_action_save_history)
            menu.add(0, MENU_CLEAR_HISTORY, 6, R.string.calculator_action_clear_history)
            setOnMenuItemClickListener { item -> handleMenuItem(item.itemId) }
            show()
        }
    }

    private fun showFunctionChooser() {
        val margin = context.resources.getDimensionPixelSize(R.dimen.margin_tiny)
        val padding = context.resources.getDimensionPixelSize(R.dimen.margin_small)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(padding, padding, padding, padding)
        }
        val leftColumn = createFunctionColumn()
        val rightColumn = createFunctionColumn()
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                resolveFunctionDialogHeightPx(margin, padding),
            )
            addView(container)
        }
        lateinit var dialog: AlertDialog
        FUNCTION_MENU_ITEMS.forEachIndexed { index, item ->
            val targetColumn = if (index % 2 == 0) leftColumn else rightColumn
            targetColumn.addView(
                createFunctionButton(item, margin) { itemId ->
                    dialog.dismiss()
                    handleMenuItem(itemId)
                }
            )
        }
        container.addView(leftColumn)
        container.addView(rightColumn)

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.calculator_action_function)
            .setView(scrollView)
            .create()
        dialog.show()
        dialog.window?.setLayout(resolveFunctionDialogWidthPx(), WRAP_CONTENT)
        leftColumn.getChildAt(0)?.requestFocus()
        DialogAccessibilityHelper.applyInitialFocus(dialog)
    }

    private fun createFunctionColumn(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

    private fun createFunctionButton(
        item: FunctionMenuItem,
        margin: Int,
        onSelect: (Int) -> Unit,
    ): MaterialButton {
        val themedContext = ContextThemeWrapper(context, R.style.Widget_FastMediaSorter_Calculator_Button)
        val textSizePx = context.resources.getDimension(R.dimen.text_size_normal)
        val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.padding_normal)
        val verticalPadding = context.resources.getDimensionPixelSize(R.dimen.padding_small)
        return MaterialButton(
            themedContext,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = context.getString(item.labelRes)
            contentDescription = text
            isAllCaps = false
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minimumHeight = 0
            minHeight = 0
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            minLines = 2
            maxLines = 2
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setMargins(margin, margin, margin, margin)
            }
            setOnClickListener { onSelect(item.itemId) }
        }
    }

    private fun resolveFunctionDialogWidthPx(): Int {
        val isWide = context.resources.configuration.isWideLayout()
        val maxWidth = dpToPx(if (isWide) FUNCTION_DIALOG_MAX_WIDTH_LAND_DP else FUNCTION_DIALOG_MAX_WIDTH_PORT_DP)
        val horizontalMargin = dpToPx(FUNCTION_DIALOG_SIDE_MARGIN_DP)
        val availableWidth = context.resources.displayMetrics.widthPixels - (horizontalMargin * 2)
        return min(availableWidth, maxWidth)
    }

    private fun resolveFunctionDialogHeightPx(itemMargin: Int, containerPadding: Int): Int {
        val rows = ceil(FUNCTION_MENU_ITEMS.size / FUNCTION_DIALOG_COLUMN_COUNT.toDouble()).toInt()
        val contentHeight =
            (rows * dpToPx(FUNCTION_DIALOG_BUTTON_HEIGHT_DP)) +
                (rows * itemMargin * 2) +
                (containerPadding * 2)
        val isWide = context.resources.configuration.isWideLayout()
        val maxHeight = dpToPx(if (isWide) FUNCTION_DIALOG_MAX_HEIGHT_LAND_DP else FUNCTION_DIALOG_MAX_HEIGHT_PORT_DP)
        return min(contentHeight, maxHeight)
    }

    private fun dpToPx(valueDp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valueDp.toFloat(),
            context.resources.displayMetrics,
        ).roundToInt()

    private fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_COPY -> { copyDisplay(); true }
        MENU_PASTE -> { pasteNumber(); true }
        MENU_ROUND -> { update { roundDisplay() }; true }
        MENU_FUNCTION -> { showFunctionChooser(); true }
        MENU_SHARE_RESULT -> { shareResult(); true }
        MENU_SAVE_HISTORY -> { saveHistory(); true }
        MENU_CLEAR_HISTORY -> { clearHistory(); true }
        FN_SIN -> { update { sine() }; true }
        FN_COS -> { update { cosine() }; true }
        FN_TAN -> { update { tangent() }; true }
        FN_COT -> { update { cotangent() }; true }
        FN_SQRT -> { update { squareRoot() }; true }
        FN_CBRT -> { update { cubeRoot() }; true }
        FN_SQUARE -> { update { square() }; true }
        FN_POWER -> { update { inputOperator("^") }; true }
        FN_RECIPROCAL -> { update { reciprocal() }; true }
        FN_LOG10 -> { update { log10() }; true }
        FN_LN -> { update { naturalLog() }; true }
        FN_FACTORIAL -> { update { factorial() }; true }
        FN_PI -> { update { inputPi() }; true }
        FN_INTEGER_DIVIDE -> { update { inputOperator("DIV") }; true }
        FN_MOD -> { update { inputOperator("mod") }; true }
        else -> false
    }

    private fun copyDisplay() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, engine.display))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun pasteNumber() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        update { inputNumber(text) }
    }

    private fun shareResult() {
        val launched = SystemShareInvoker.invoke(
            context = context,
            payload = SharePayload.Text(engine.display),
            chooserTitle = context.getString(R.string.calculator_action_share_result),
        )
        if (!launched) {
            Toast.makeText(context, R.string.calculator_share_result_no_target, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveHistory() {
        val historyText = engine.calculationHistoryText
        if (historyText.isBlank()) {
            Toast.makeText(context, R.string.calculator_history_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val appContext = context.applicationContext
        thread(name = "CalculatorHistorySave") {
            val result = runCatching {
                CalculatorHistoryFileWriter.writeToDownloads(appContext, historyText)
            }
            mainHandler.post {
                result.onSuccess { fileName ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.calculator_history_saved_to_downloads, fileName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { error ->
                    Timber.e(error, "Calculator history save failed")
                    Toast.makeText(context, R.string.calculator_history_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearHistory() {
        engine.clearHistory()
        persistedHistorySize = 0
        thread(name = "CalculatorHistoryClear") { historyStore.clear() }
        render()
        Toast.makeText(context, R.string.calculator_history_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun bindDigitButtons() {
        binding.btnCalculatorZero.setOnClickListener { update { inputDigit(0) } }
        binding.btnCalculatorOne.setOnClickListener { update { inputDigit(1) } }
        binding.btnCalculatorTwo.setOnClickListener { update { inputDigit(2) } }
        binding.btnCalculatorThree.setOnClickListener { update { inputDigit(3) } }
        binding.btnCalculatorFour.setOnClickListener { update { inputDigit(4) } }
        binding.btnCalculatorFive.setOnClickListener { update { inputDigit(5) } }
        binding.btnCalculatorSix.setOnClickListener { update { inputDigit(6) } }
        binding.btnCalculatorSeven.setOnClickListener { update { inputDigit(7) } }
        binding.btnCalculatorEight.setOnClickListener { update { inputDigit(8) } }
        binding.btnCalculatorNine.setOnClickListener { update { inputDigit(9) } }
    }

    private fun digitFor(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
        else -> null
    }

    private fun consume(action: CalculatorEngine.() -> String): Boolean {
        update(action)
        return true
    }

    private fun update(action: CalculatorEngine.() -> String) {
        val previousError = engine.error
        engine.action()
        hasReturnableResult = engine.error == null
        persistNewHistoryEntries()
        render()
        if (
            previousError != CalculatorEngine.CalculatorError.DIVISION_BY_ZERO &&
            engine.error == CalculatorEngine.CalculatorError.DIVISION_BY_ZERO
        ) {
            prankManager.showDivisionByZeroPrank()
        }
    }

    private fun persistNewHistoryEntries() {
        val history = engine.calculationHistory
        if (history.size <= persistedHistorySize) {
            if (history.size < persistedHistorySize) persistedHistorySize = history.size
            return
        }
        val newEntries = history.subList(persistedHistorySize, history.size).toList()
        persistedHistorySize = history.size
        thread(name = "CalculatorHistoryAppend") {
            newEntries.forEach { historyStore.append(it) }
        }
    }

    private companion object {
        /** S1719: the hint reads as a caption under the key's symbol, not as a second symbol. */
        const val HINT_TEXT_SCALE = 0.42f

        /** Symbol line plus hint line - never more, so a re-bind cannot stack hints. */
        const val HINT_KEY_MAX_LINES = 2

        const val CLIP_LABEL = "calculator"
        const val HISTORY_FILE_NAME = "calculator_history.txt"

        // S1549: instance-state keys for the in-progress calculation.
        const val STATE_DISPLAY = "calc_display"
        const val STATE_OPERATION_HISTORY = "calc_operation_history"
        const val STATE_COMPLETED_HISTORY = "calc_completed_history"
        const val STATE_ACCUMULATOR = "calc_accumulator"
        const val STATE_PENDING_OPERATOR = "calc_pending_operator"
        const val STATE_REPEAT_OPERATOR = "calc_repeat_operator"
        const val STATE_REPEAT_OPERAND = "calc_repeat_operand"
        const val STATE_START_NEW_INPUT = "calc_start_new_input"
        const val STATE_MEMORY = "calc_memory"
        const val STATE_MEMORY_ROW_EXPANDED = "calc_memory_row_expanded"
        const val STATE_HAS_RETURNABLE_RESULT = "calc_has_returnable_result"
        const val FUNCTION_DIALOG_COLUMN_COUNT = 2
        const val FUNCTION_DIALOG_BUTTON_HEIGHT_DP = 44
        const val FUNCTION_DIALOG_SIDE_MARGIN_DP = 16
        const val FUNCTION_DIALOG_MAX_WIDTH_PORT_DP = 320
        const val FUNCTION_DIALOG_MAX_WIDTH_LAND_DP = 420
        const val FUNCTION_DIALOG_MAX_HEIGHT_PORT_DP = 320
        const val FUNCTION_DIALOG_MAX_HEIGHT_LAND_DP = 360
        val FUNCTION_MENU_ITEMS = listOf(
            FunctionMenuItem(FN_SIN, R.string.calculator_fn_sin),
            FunctionMenuItem(FN_COS, R.string.calculator_fn_cos),
            FunctionMenuItem(FN_TAN, R.string.calculator_fn_tan),
            FunctionMenuItem(FN_COT, R.string.calculator_fn_cot),
            FunctionMenuItem(FN_SQRT, R.string.calculator_fn_sqrt),
            FunctionMenuItem(FN_CBRT, R.string.calculator_fn_cbrt),
            FunctionMenuItem(FN_SQUARE, R.string.calculator_fn_square),
            FunctionMenuItem(FN_POWER, R.string.calculator_fn_power),
            FunctionMenuItem(FN_RECIPROCAL, R.string.calculator_fn_reciprocal),
            FunctionMenuItem(FN_LOG10, R.string.calculator_fn_log10),
            FunctionMenuItem(FN_LN, R.string.calculator_fn_ln),
            FunctionMenuItem(FN_FACTORIAL, R.string.calculator_fn_factorial),
            FunctionMenuItem(FN_INTEGER_DIVIDE, R.string.calculator_fn_div),
            FunctionMenuItem(FN_MOD, R.string.calculator_fn_mod),
            FunctionMenuItem(FN_PI, R.string.calculator_fn_pi),
        )
    }

    private data class FunctionMenuItem(val itemId: Int, val labelRes: Int)
}
