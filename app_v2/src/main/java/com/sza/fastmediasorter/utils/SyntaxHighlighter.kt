package com.sza.fastmediasorter.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * Lightweight syntax highlighter for code files.
 * Applies ForegroundColorSpan to keywords, strings, comments, numbers.
 * Supports: .kt, .java, .json, .xml, .py, .js, .ts, .html, .css
 *
 * Performance: Only highlights text shorter than [MAX_HIGHLIGHT_LENGTH].
 */
object SyntaxHighlighter {

    private const val MAX_HIGHLIGHT_LENGTH = 100_000 // 100KB chars threshold

    // Colors (dark theme friendly defaults - caller can customize via theme)
    private const val COLOR_KEYWORD = 0xFF569CD6.toInt()   // Blue
    private const val COLOR_STRING = 0xFFCE9178.toInt()    // Orange
    private const val COLOR_COMMENT = 0xFF6A9955.toInt()   // Green
    private const val COLOR_NUMBER = 0xFFB5CEA8.toInt()    // Light green
    private const val COLOR_TAG = 0xFF569CD6.toInt()       // Blue (XML/HTML tags)
    private const val COLOR_ATTRIBUTE = 0xFF9CDCFE.toInt() // Light blue (XML/HTML attr)
    private const val COLOR_BOOLEAN = 0xFF569CD6.toInt()   // Blue
    private const val COLOR_NULL = 0xFF569CD6.toInt()      // Blue

    // Kotlin/Java keywords
    private val KOTLIN_KEYWORDS = setOf(
        "abstract", "annotation", "as", "break", "by", "catch", "class", "companion",
        "const", "constructor", "continue", "crossinline", "data", "do", "else", "enum",
        "expect", "external", "false", "final", "finally", "for", "fun", "get", "if",
        "import", "in", "infix", "init", "inline", "inner", "interface", "internal",
        "is", "it", "lateinit", "noinline", "null", "object", "open", "operator", "out",
        "override", "package", "private", "protected", "public", "reified", "return",
        "sealed", "set", "super", "suspend", "this", "throw", "true", "try", "typealias",
        "val", "var", "vararg", "when", "where", "while", "yield"
    )

    // Python keywords
    private val PYTHON_KEYWORDS = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
        "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
        "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass",
        "raise", "return", "True", "try", "while", "with", "yield", "self"
    )

    // JavaScript/TypeScript keywords
    private val JS_KEYWORDS = setOf(
        "abstract", "arguments", "async", "await", "boolean", "break", "byte", "case",
        "catch", "class", "const", "continue", "debugger", "default", "delete", "do",
        "else", "enum", "export", "extends", "false", "final", "finally", "for",
        "function", "if", "implements", "import", "in", "instanceof", "interface",
        "let", "new", "null", "of", "package", "private", "protected", "public",
        "return", "static", "super", "switch", "this", "throw", "true", "try",
        "typeof", "undefined", "var", "void", "while", "with", "yield"
    )

    /**
     * Highlight syntax in the given text based on file extension.
     * Returns SpannableString with color spans, or null if highlighting is not applicable.
     */
    fun highlight(text: String, extension: String): SpannableString? {
        if (text.length > MAX_HIGHLIGHT_LENGTH) return null
        if (text.isEmpty()) return null

        return when (extension.lowercase()) {
            "kt", "kts", "java" -> highlightKotlin(text)
            "json" -> highlightJson(text)
            "xml", "html", "htm", "svg" -> highlightXml(text)
            "py" -> highlightPython(text)
            "js", "ts", "jsx", "tsx" -> highlightJavaScript(text)
            "css", "scss", "less" -> highlightCss(text)
            else -> null
        }
    }

    /**
     * Check if a file extension supports syntax highlighting.
     */
    fun isSupported(extension: String): Boolean {
        return extension.lowercase() in setOf(
            "kt", "kts", "java", "json", "xml", "html", "htm", "svg",
            "py", "js", "ts", "jsx", "tsx", "css", "scss", "less"
        )
    }

    private fun highlightKotlin(text: String): SpannableString {
        val spannable = SpannableString(text)

        // Comments (line and block)
        highlightPattern(spannable, text, Regex("//[^\n]*"), COLOR_COMMENT)
        highlightPattern(spannable, text, Regex("/\\*[\\s\\S]*?\\*/"), COLOR_COMMENT)

        // Strings (double-quoted, including escaped quotes)
        highlightPattern(spannable, text, Regex("\"\"\"[\\s\\S]*?\"\"\""), COLOR_STRING) // Triple-quoted
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), COLOR_STRING)

        // Keywords
        highlightKeywords(spannable, text, KOTLIN_KEYWORDS, COLOR_KEYWORD)

        // Numbers
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*[fFdDlL]?\\b"), COLOR_NUMBER)

        return spannable
    }

    private fun highlightPython(text: String): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("#[^\n]*"), COLOR_COMMENT)

        // Strings (triple-quoted first, then single/double)
        highlightPattern(spannable, text, Regex("\"\"\"[\\s\\S]*?\"\"\""), COLOR_STRING)
        highlightPattern(spannable, text, Regex("'''[\\s\\S]*?'''"), COLOR_STRING)
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), COLOR_STRING)

        // Keywords
        highlightKeywords(spannable, text, PYTHON_KEYWORDS, COLOR_KEYWORD)

        // Numbers
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*\\b"), COLOR_NUMBER)

        return spannable
    }

    private fun highlightJavaScript(text: String): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("//[^\n]*"), COLOR_COMMENT)
        highlightPattern(spannable, text, Regex("/\\*[\\s\\S]*?\\*/"), COLOR_COMMENT)

        // Template literals
        highlightPattern(spannable, text, Regex("`(?:[^`\\\\]|\\\\.)*`"), COLOR_STRING)
        // Strings
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), COLOR_STRING)

        // Keywords
        highlightKeywords(spannable, text, JS_KEYWORDS, COLOR_KEYWORD)

        // Numbers
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*\\b"), COLOR_NUMBER)

        return spannable
    }

    private fun highlightJson(text: String): SpannableString {
        val spannable = SpannableString(text)

        // Keys (string before colon)
        highlightPattern(spannable, text, Regex("\"[^\"]*\"\\s*:"), COLOR_ATTRIBUTE)

        // String values
        highlightPattern(spannable, text, Regex(":\\s*\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING)

        // Numbers
        highlightPattern(spannable, text, Regex(":\\s*-?\\d+[.\\d]*([eE][+-]?\\d+)?"), COLOR_NUMBER)

        // Booleans and null
        highlightPattern(spannable, text, Regex("\\b(true|false)\\b"), COLOR_BOOLEAN)
        highlightPattern(spannable, text, Regex("\\bnull\\b"), COLOR_NULL)

        return spannable
    }

    private fun highlightXml(text: String): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("<!--[\\s\\S]*?-->"), COLOR_COMMENT)

        // Tags (opening and closing)
        highlightPattern(spannable, text, Regex("</?[a-zA-Z][a-zA-Z0-9_.:-]*"), COLOR_TAG)
        highlightPattern(spannable, text, Regex("/?>"), COLOR_TAG)

        // Attribute names
        highlightPattern(spannable, text, Regex("\\b[a-zA-Z][a-zA-Z0-9_:-]*(?=\\s*=)"), COLOR_ATTRIBUTE)

        // Attribute values
        highlightPattern(spannable, text, Regex("\"[^\"]*\""), COLOR_STRING)

        return spannable
    }

    private fun highlightCss(text: String): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("/\\*[\\s\\S]*?\\*/"), COLOR_COMMENT)

        // Strings
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), COLOR_STRING)

        // Numbers with units
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*(px|em|rem|%|vh|vw|pt|cm|mm)?\\b"), COLOR_NUMBER)

        return spannable
    }

    private fun highlightPattern(spannable: SpannableString, text: String, pattern: Regex, color: Int) {
        pattern.findAll(text).forEach { match ->
            spannable.setSpan(
                ForegroundColorSpan(color),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun highlightKeywords(spannable: SpannableString, text: String, keywords: Set<String>, color: Int) {
        val wordPattern = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
        wordPattern.findAll(text).forEach { match ->
            if (match.value in keywords) {
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
