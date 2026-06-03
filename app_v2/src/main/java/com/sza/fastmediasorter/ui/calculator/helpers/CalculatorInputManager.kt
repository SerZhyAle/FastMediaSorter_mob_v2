package com.sza.fastmediasorter.ui.calculator.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.ActivityCalculatorBinding
import timber.log.Timber
import java.io.File
import kotlin.concurrent.thread

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
        Timber.d("S0331: calculator memory load on open")
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
        Timber.d("S0331: calculator memory row toggle")
        memoryRowExpanded = !memoryRowExpanded
        applyMemoryRowState()
        val expanded = memoryRowExpanded
        thread(name = "CalculatorMemoryRowState") { memoryStore.saveRowExpanded(expanded) }
    }

    private fun applyMemoryRowState() {
        binding.calculatorMemoryRow.isVisible = memoryRowExpanded
    }

    private fun persistMemory() {
        Timber.d("S0331: calculator memory persist")
        val value = engine.memory.toPlainString()
        thread(name = "CalculatorMemorySave") { memoryStore.saveMemory(value) }
    }

    private fun loadPersistedHistory() {
        Timber.d("S0329: calculator history load on open")
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
        Timber.d("S0329: calculator evaluate selected/pasted text")
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
            menu.addSubMenu(0, MENU_FUNCTION, 3, R.string.calculator_action_function).apply {
                add(0, FN_SIN, 0, R.string.calculator_fn_sin)
                add(0, FN_COS, 1, R.string.calculator_fn_cos)
                add(0, FN_TAN, 2, R.string.calculator_fn_tan)
                add(0, FN_COT, 3, R.string.calculator_fn_cot)
                add(0, FN_SQRT, 4, R.string.calculator_fn_sqrt)
                add(0, FN_CBRT, 5, R.string.calculator_fn_cbrt)
                add(0, FN_SQUARE, 6, R.string.calculator_fn_square)
                add(0, FN_POWER, 7, R.string.calculator_fn_power)
                add(0, FN_RECIPROCAL, 8, R.string.calculator_fn_reciprocal)
                add(0, FN_LOG10, 9, R.string.calculator_fn_log10)
                add(0, FN_LN, 10, R.string.calculator_fn_ln)
                add(0, FN_FACTORIAL, 11, R.string.calculator_fn_factorial)
                add(0, FN_PI, 12, R.string.calculator_fn_pi)
                add(0, FN_MOD, 13, R.string.calculator_fn_mod)
            }
            menu.add(0, MENU_SHARE_RESULT, 4, R.string.calculator_action_share_result)
            menu.add(0, MENU_SAVE_HISTORY, 5, R.string.calculator_action_save_history)
            menu.add(0, MENU_CLEAR_HISTORY, 6, R.string.calculator_action_clear_history)
            setOnMenuItemClickListener { item -> handleMenuItem(item.itemId) }
            show()
        }
    }

    private fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_COPY -> { copyDisplay(); true }
        MENU_PASTE -> { pasteNumber(); true }
        MENU_ROUND -> { update { roundDisplay() }; true }
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
        Timber.d("S0329: calculator clear persistent history")
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
        engine.action()
        hasReturnableResult = engine.error == null
        persistNewHistoryEntries()
        render()
    }

    private fun persistNewHistoryEntries() {
        val history = engine.calculationHistory
        if (history.size <= persistedHistorySize) {
            if (history.size < persistedHistorySize) persistedHistorySize = history.size
            return
        }
        val newEntries = history.subList(persistedHistorySize, history.size).toList()
        persistedHistorySize = history.size
        Timber.d("S0329: calculator persist history entry")
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
        const val CLIP_LABEL = "calculator"
        const val HISTORY_FILE_NAME = "calculator_history.txt"
    }
}
