package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
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
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.ActivityCalculatorBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        render()
        loadPersistedHistory()
        loadPersistedMemory()
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
                entries += currentOperation
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
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val maxWidth = dpToPx(if (isLandscape) FUNCTION_DIALOG_MAX_WIDTH_LAND_DP else FUNCTION_DIALOG_MAX_WIDTH_PORT_DP)
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
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val maxHeight = dpToPx(if (isLandscape) FUNCTION_DIALOG_MAX_HEIGHT_LAND_DP else FUNCTION_DIALOG_MAX_HEIGHT_PORT_DP)
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
        const val MENU_COPY = 1
        const val MENU_PASTE = 2
        const val MENU_ROUND = 3
        const val MENU_SHARE_RESULT = 4
        const val MENU_SAVE_HISTORY = 5
        const val MENU_CLEAR_HISTORY = 6
        const val MENU_FUNCTION = 7
        const val FN_SIN = 100
        const val FN_COS = 101
        const val FN_TAN = 102
        const val FN_COT = 103
        const val FN_SQRT = 104
        const val FN_CBRT = 105
        const val FN_SQUARE = 106
        const val FN_POWER = 107
        const val FN_RECIPROCAL = 108
        const val FN_LOG10 = 109
        const val FN_LN = 110
        const val FN_FACTORIAL = 111
        const val FN_PI = 112
        const val FN_MOD = 113
        const val FN_INTEGER_DIVIDE = 114
        const val CLIP_LABEL = "calculator"
        const val HISTORY_FILE_NAME = "calculator_history.txt"
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
