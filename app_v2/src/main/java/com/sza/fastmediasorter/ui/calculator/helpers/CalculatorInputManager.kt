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
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.databinding.ActivityCalculatorBinding
import timber.log.Timber
import kotlin.concurrent.thread

class CalculatorInputManager(
    private val binding: ActivityCalculatorBinding,
    private val context: Context,
) {
    private val engine = CalculatorEngine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasReturnableResult = false

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
        render()
    }

    fun applyInitialInput(text: String?) {
        val initialText = text?.trim().orEmpty()
        if (initialText.isBlank() || !engine.canParseInput(initialText)) return

        engine.inputNumber(initialText)
        hasReturnableResult = engine.error == null
        render()
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
            null -> engine.display
        }
        binding.calculatorHistory.text = buildVisibleHistory()
        binding.calculatorHistoryScroll.post {
            binding.calculatorHistoryScroll.fullScroll(View.FOCUS_DOWN)
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
            menu.add(0, MENU_SHARE_RESULT, 3, R.string.calculator_action_share_result)
            menu.add(0, MENU_SAVE_HISTORY, 4, R.string.calculator_action_save_history)
            menu.add(0, MENU_CLEAR_HISTORY, 5, R.string.calculator_action_clear_history)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_COPY -> {
                        copyDisplay()
                        true
                    }
                    MENU_PASTE -> {
                        pasteNumber()
                        true
                    }
                    MENU_ROUND -> {
                        update { roundDisplay() }
                        true
                    }
                    MENU_SHARE_RESULT -> {
                        shareResult()
                        true
                    }
                    MENU_SAVE_HISTORY -> {
                        saveHistory()
                        true
                    }
                    MENU_CLEAR_HISTORY -> {
                        clearHistory()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
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
        render()
    }

    private companion object {
        const val MENU_COPY = 1
        const val MENU_PASTE = 2
        const val MENU_ROUND = 3
        const val MENU_SHARE_RESULT = 4
        const val MENU_SAVE_HISTORY = 5
        const val MENU_CLEAR_HISTORY = 6
        const val CLIP_LABEL = "calculator"
    }
}
