package com.sza.fastmediasorter.ui.exchange.helpers

import com.sza.fastmediasorter.domain.model.ExchangeAdminReplyCode
import com.sza.fastmediasorter.domain.model.ExchangeCheckStep
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExchangeMessageTextTest {

    @Test
    fun `every row and every step has words`() {
        ExchangeMessage.entries.forEach { assertNotEquals(it.name, 0, ExchangeMessageText.what(it)) }
        ExchangeCheckStep.entries.forEach { assertNotEquals(it.name, 0, ExchangeMessageText.advice(it)) }
    }

    @Test
    fun `no two rows and no two steps share a string`() {
        val rows = ExchangeMessage.entries.map { ExchangeMessageText.what(it) }
        val steps = ExchangeCheckStep.entries.map { ExchangeMessageText.advice(it) }
        assertEquals(rows.size, rows.toSet().size)
        assertEquals(steps.size, steps.toSet().size)
    }

    @Test
    fun `every administration reply code has its own words`() {
        val words = ExchangeAdminReplyCode.entries.map { ExchangeMessageText.adminReply(it) }
        words.forEach { assertNotEquals(0, it) }
        assertEquals(words.size, words.toSet().size)
    }

    @Test
    fun `admin-disabled carries its own advice and no step`() {
        assertNotEquals(0, ExchangeMessageText.what(ExchangeMessage.ADMIN_DISABLED))
        assertNull(ExchangeMessage.ADMIN_DISABLED.step)
        assertEquals(ExchangeMessage.ADMIN_DISABLED, ExchangeMessage.ofWire("admin-disabled"))
    }

    @Test
    fun `a watch answer maps to the row of its own failure`() {
        assertEquals(ExchangeMessage.UNREACHABLE_SERVER, ExchangeMessageText.ofHandoverReason("unreachable"))
        assertEquals(ExchangeMessage.UNANSWERED, ExchangeMessageText.ofHandoverReason("unanswered"))
        assertEquals(ExchangeMessage.TLS_FAILED, ExchangeMessageText.ofHandoverReason("tls-failed"))
        assertEquals(ExchangeMessage.CERTIFICATE_CHANGED, ExchangeMessageText.ofHandoverReason("certificate_changed"))
        assertEquals(
            ExchangeMessage.CERTIFICATE_CHANGED,
            ExchangeMessageText.ofHandoverReason("certificate_untrusted"),
        )
    }

    @Test
    fun `a server refusal passes through and an unknown answer is the generic refusal`() {
        assertEquals(ExchangeMessage.BAD_CREDENTIALS, ExchangeMessageText.ofHandoverReason("bad-credentials"))
        assertEquals(ExchangeMessage.TOKEN_NOT_STORED, ExchangeMessageText.ofHandoverReason("token-not-stored"))
        assertEquals(ExchangeMessage.REFUSED, ExchangeMessageText.ofHandoverReason("malformed"))
    }
}
