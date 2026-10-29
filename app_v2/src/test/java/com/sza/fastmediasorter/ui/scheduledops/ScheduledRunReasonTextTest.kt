package com.sza.fastmediasorter.ui.scheduledops

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScheduledRunReasonTextTest {

    @Test
    fun `every reason code has its own sentence`() {
        val sentences = ScheduledRunReason.entries.map { ScheduledRunReasonText.of(it, ScheduledRunOutcome.SKIPPED) }

        assertEquals(ScheduledRunReason.entries.size, sentences.toSet().size)
    }

    @Test
    fun `a run without a code gets the neutral sentence of its outcome`() {
        assertEquals(
            R.string.scheduled_op_reason_skipped_other,
            ScheduledRunReasonText.of(null, ScheduledRunOutcome.SKIPPED)
        )
        assertEquals(
            R.string.scheduled_op_reason_error_other,
            ScheduledRunReasonText.of(null, ScheduledRunOutcome.ERROR)
        )
    }

    @Test
    fun `a known code wins over the outcome`() {
        assertNotEquals(
            ScheduledRunReasonText.of(null, ScheduledRunOutcome.ERROR),
            ScheduledRunReasonText.of(ScheduledRunReason.PIN_REFUSED, ScheduledRunOutcome.ERROR)
        )
    }
}
