package com.sza.fastmediasorter.core.util

import android.content.Context
import com.sza.fastmediasorter.R
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * S2351 - unit tests for [formatFileSize].
 *
 * The unit label became a localized resource, so `Context.getString` is stubbed to echo the resource id
 * back together with the substituted number. That pins the two things the move to resources promised not
 * to change: which bucket a byte count lands in, and the rendered number itself.
 */
class FileSizeTest {

    private lateinit var context: Context
    private val originalLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        // The grouping separator and the decimal mark are locale-dependent; pin one so the asserts
        // describe the formatter rather than the machine running them.
        Locale.setDefault(Locale.US)
        context = mockk(relaxed = true)
        every { context.getString(any<Int>(), *anyVararg()) } answers {
            val resId = firstArg<Int>()
            val formatArgs = invocation.args.drop(1)
                .flatMap { arg -> if (arg is Array<*>) arg.toList() else listOf(arg) }
            "$resId|${formatArgs.joinToString(",")}"
        }
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `zero bytes render through the byte unit`() {
        assertEquals("${R.string.unit_size_bytes}|0", formatFileSize(context, 0L))
    }

    @Test
    fun `small sizes keep every digit and group thousands with spaces`() {
        assertEquals("${R.string.unit_size_bytes}|1 234", formatFileSize(context, 1234L))
    }

    @Test
    fun `the last byte-rendered size stays on the byte unit`() {
        assertEquals("${R.string.unit_size_bytes}|10 239", formatFileSize(context, 10239L))
    }

    @Test
    fun `the exact-bytes ceiling is the first kilobyte-rendered size`() {
        assertEquals("${R.string.unit_size_kb}|10.00", formatFileSize(context, 10240L))
    }

    @Test
    fun `sizes below a megabyte render on the kilobyte unit`() {
        assertEquals("${R.string.unit_size_kb}|512.00", formatFileSize(context, 512L * 1024))
    }

    @Test
    fun `the last kilobyte-rendered size stays on the kilobyte unit`() {
        assertEquals("${R.string.unit_size_kb}|1023.00", formatFileSize(context, 1023L * 1024))
    }

    @Test
    fun `a megabyte is the first megabyte-rendered size`() {
        assertEquals("${R.string.unit_size_mb}|1.00", formatFileSize(context, 1024L * 1024))
    }

    @Test
    fun `sizes below a gigabyte render on the megabyte unit`() {
        assertEquals("${R.string.unit_size_mb}|5.00", formatFileSize(context, 5L * 1024 * 1024))
    }

    @Test
    fun `a gigabyte is the first gigabyte-rendered size`() {
        assertEquals("${R.string.unit_size_gb}|1.00", formatFileSize(context, 1024L * 1024 * 1024))
    }

    @Test
    fun `large sizes keep two decimal places`() {
        // 2.50 GB exactly, so the assert fails on a precision change rather than on a rounding tie.
        assertEquals("${R.string.unit_size_gb}|2.50", formatFileSize(context, 2560L * 1024 * 1024))
    }
}
