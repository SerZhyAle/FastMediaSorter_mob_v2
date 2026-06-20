package com.sza.fastmediasorter.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.graphics.ColorUtils

/**
 * Token colors for a single reader-theme background. Bundles the eight highlight roles so the
 * highlighter can swap the whole palette to match the active background instead of shipping fixed
 * dark-theme hues that wash out on the light/sepia reader themes.
 */
data class SyntaxPalette(
    val keyword: Int,
    val string: Int,
    val comment: Int,
    val number: Int,
    val tag: Int,
    val attribute: Int,
    val boolean: Int,
    val nullLiteral: Int,
) {
    companion object {
        /** VS Code Dark+ hues - tuned for dark backgrounds. */
        val DARK = SyntaxPalette(
            keyword = 0xFF569CD6.toInt(),   // Blue
            string = 0xFFCE9178.toInt(),    // Orange
            comment = 0xFF6A9955.toInt(),   // Green
            number = 0xFFB5CEA8.toInt(),    // Light green
            tag = 0xFF569CD6.toInt(),       // Blue
            attribute = 0xFF9CDCFE.toInt(), // Light blue
            boolean = 0xFF569CD6.toInt(),   // Blue
            nullLiteral = 0xFF569CD6.toInt(),
        )

        /** VS Code Light+ inspired hues - saturated/dark enough to read on white and sepia. */
        val LIGHT = SyntaxPalette(
            keyword = 0xFF0000FF.toInt(),   // Blue
            string = 0xFFA31515.toInt(),    // Dark red
            comment = 0xFF008000.toInt(),   // Green
            number = 0xFF098658.toInt(),    // Teal green
            tag = 0xFF800000.toInt(),       // Maroon
            attribute = 0xFF1F6B82.toInt(), // Dark teal
            boolean = 0xFF0000FF.toInt(),   // Blue
            nullLiteral = 0xFF0000FF.toInt(),
        )

        /** Pick the palette whose token contrast suits [bgColor]'s luminance. */
        fun forBackground(bgColor: Int): SyntaxPalette =
            if (ColorUtils.calculateLuminance(bgColor) < 0.5) DARK else LIGHT
    }
}

/**
 * Lightweight syntax highlighter for code files.
 * Applies ForegroundColorSpan to keywords, strings, comments, numbers.
 * Supports: .kt, .java, .json, .xml, .py, .js, .ts, .html, .css
 *
 * Performance: Only highlights text shorter than [MAX_HIGHLIGHT_LENGTH].
 */
object SyntaxHighlighter {

    private const val MAX_HIGHLIGHT_LENGTH = 100_000 // 100KB chars threshold

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
     * [palette] selects the token colors - pass [SyntaxPalette.forBackground] for the active
     * reader theme so hues stay legible on light/sepia backgrounds.
     * Returns SpannableString with color spans, or null if highlighting is not applicable.
     */
    fun highlight(
        text: String,
        extension: String,
        palette: SyntaxPalette = SyntaxPalette.DARK,
    ): SpannableString? {
        if (text.length > MAX_HIGHLIGHT_LENGTH) return null
        if (text.isEmpty()) return null

        return when (extension.lowercase()) {
            "kt", "kts", "java" -> highlightKotlin(text, palette)
            "json" -> highlightJson(text, palette)
            "xml", "html", "htm", "svg" -> highlightXml(text, palette)
            "py" -> highlightPython(text, palette)
            "js", "ts", "jsx", "tsx" -> highlightJavaScript(text, palette)
            "css", "scss", "less" -> highlightCss(text, palette)
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

    private fun highlightKotlin(text: String, palette: SyntaxPalette): SpannableString {
        val spannable = SpannableString(text)

        // Comments (line and block)
        highlightPattern(spannable, text, Regex("//[^\n]*"), palette.comment)
        highlightPattern(spannable, text, Regex("/\\*[\\s\\S]*?\\*/"), palette.comment)

        // Strings (double-quoted, including escaped quotes)
        highlightPattern(spannable, text, Regex("\"\"\"[\\s\\S]*?\"\"\""), palette.string) // Triple-quoted
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), palette.string)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), palette.string)

        // Keywords
        highlightKeywords(spannable, text, KOTLIN_KEYWORDS, palette.keyword)

        // Numbers
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*[fFdDlL]?\\b"), palette.number)

        return spannable
    }

    private fun highlightPython(text: String, palette: SyntaxPalette): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("#[^\n]*"), palette.comment)

        // Strings (triple-quoted first, then single/double)
        highlightPattern(spannable, text, Regex("\"\"\"[\\s\\S]*?\"\"\""), palette.string)
        highlightPattern(spannable, text, Regex("'''[\\s\\S]*?'''"), palette.string)
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), palette.string)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), palette.string)

        // Keywords
        highlightKeywords(spannable, text, PYTHON_KEYWORDS, palette.keyword)

        // Numbers
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*\\b"), palette.number)

        return spannable
    }

    private fun highlightJavaScript(text: String, palette: SyntaxPalette): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("//[^\n]*"), palette.comment)
        highlightPattern(spannable, text, Regex("/\\*[\\s\\S]*?\\*/"), palette.comment)

        // Template literals
        highlightPattern(spannable, text, Regex("`(?:[^`\\\\]|\\\\.)*`"), palette.string)
        // Strings
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), palette.string)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), palette.string)

        // Keywords
        highlightKeywords(spannable, text, JS_KEYWORDS, palette.keyword)

        // Numbers
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*\\b"), palette.number)

        return spannable
    }

    private fun highlightJson(text: String, palette: SyntaxPalette): SpannableString {
        val spannable = SpannableString(text)

        // Keys (string before colon)
        highlightPattern(spannable, text, Regex("\"[^\"]*\"\\s*:"), palette.attribute)

        // String values
        highlightPattern(spannable, text, Regex(":\\s*\"(?:[^\"\\\\]|\\\\.)*\""), palette.string)

        // Numbers
        highlightPattern(spannable, text, Regex(":\\s*-?\\d+[.\\d]*([eE][+-]?\\d+)?"), palette.number)

        // Booleans and null
        highlightPattern(spannable, text, Regex("\\b(true|false)\\b"), palette.boolean)
        highlightPattern(spannable, text, Regex("\\bnull\\b"), palette.nullLiteral)

        return spannable
    }

    private fun highlightXml(text: String, palette: SyntaxPalette): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("<!--[\\s\\S]*?-->"), palette.comment)

        // Tags (opening and closing)
        highlightPattern(spannable, text, Regex("</?[a-zA-Z][a-zA-Z0-9_.:-]*"), palette.tag)
        highlightPattern(spannable, text, Regex("/?>"), palette.tag)

        // Attribute names
        highlightPattern(spannable, text, Regex("\\b[a-zA-Z][a-zA-Z0-9_:-]*(?=\\s*=)"), palette.attribute)

        // Attribute values
        highlightPattern(spannable, text, Regex("\"[^\"]*\""), palette.string)

        return spannable
    }

    private fun highlightCss(text: String, palette: SyntaxPalette): SpannableString {
        val spannable = SpannableString(text)

        // Comments
        highlightPattern(spannable, text, Regex("/\\*[\\s\\S]*?\\*/"), palette.comment)

        // Strings
        highlightPattern(spannable, text, Regex("\"(?:[^\"\\\\]|\\\\.)*\""), palette.string)
        highlightPattern(spannable, text, Regex("'(?:[^'\\\\]|\\\\.)*'"), palette.string)

        // Numbers with units
        highlightPattern(spannable, text, Regex("\\b\\d+[.\\d]*(px|em|rem|%|vh|vw|pt|cm|mm)?\\b"), palette.number)

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
