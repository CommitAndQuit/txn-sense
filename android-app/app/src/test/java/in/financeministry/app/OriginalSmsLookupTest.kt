package `in`.financeministry.app

import `in`.financeministry.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Replacing exact fingerprint matching with amount/label matching must fail these tests. */
class OriginalSmsLookupTest {
    private val time = 1_790_000_000_000L
    private val body = "INR 42.50 debited via UPI to SAMPLE SHOP. Ref 000000000001"
    private fun hash(sender: String, date: Long, text: String) =
        MessageDigest.getInstance("SHA-256").digest("$sender\u0000$date\u0000$text".toByteArray())
    private fun row(timestamp: Long = time, fingerprintDate: Long = timestamp) = TransactionEntity(
        "sample", hash("AD-EXBANK", fingerprintDate, body), "SMS", timestamp, timestamp,
        4250, direction = "Debit", status = "Successful", channel = "UPI", transactionType = "Other",
        reviewState = "AutoRecorded", createdAt = time, updatedAt = time)
    private fun source(vararg messages: HistoricalSms) = HistoricalSmsSource { _, emit -> messages.forEach { emit(it) } }
    private suspend fun find(row: TransactionEntity = row(), source: HistoricalSmsSource, permitted: () -> Boolean = { true }) =
        OriginalSmsLookup(::hash).find(row, source, permitted)

    @Test fun exact_received_timestamp_match_returns_original_not_edited_fields() = runBlocking {
        val sms = HistoricalSms("AD-EXBANK", time, 0, body)
        val changed = row().copy(amountMinor = 9999, effectiveTimestamp = time + 86400000,
            counterpartyLabel = "Edited by user", isUserCorrected = true)
        val result = find(changed, source(sms)) as OriginalSmsResult.Found
        assertSame(sms, result.message)
        assertEquals(body, result.message.body)
    }

    @Test fun imported_sent_timestamp_and_live_delayed_delivery_match() = runBlocking {
        val imported = HistoricalSms("AD-EXBANK", time, time - 5000, body)
        assertTrue(find(row(fingerprintDate = time - 5000), source(imported)) is OriginalSmsResult.Found)
        val delayed = HistoricalSms("AD-EXBANK", time + 86400000, time, body)
        assertTrue(find(source = source(delayed)) is OriginalSmsResult.Found)
    }

    @Test fun wrong_sender_body_and_timestamp_never_match_even_with_same_amount() = runBlocking {
        val messages = source(HistoricalSms("OTHERBANK", time, 0, body),
            HistoricalSms("AD-EXBANK", time, 0, body.replace("SAMPLE SHOP", "OTHER SHOP")),
            HistoricalSms("AD-EXBANK", time + 1, 0, body))
        assertSame(OriginalSmsResult.Unavailable, find(source = messages))
    }

    @Test fun duplicate_exact_candidates_are_ambiguous_not_first_match_wins() = runBlocking {
        val sms = HistoricalSms("AD-EXBANK", time, 0, body)
        assertSame(OriginalSmsResult.Unavailable, find(source = source(sms, sms)))
    }

    @Test fun deleted_message_returns_unavailable() = runBlocking {
        assertSame(OriginalSmsResult.Unavailable, find(source = source()))
    }

    @Test fun query_is_limited_to_the_original_timestamp_not_neighboring_messages() = runBlocking {
        val result = find(source = HistoricalSmsSource { window, emit ->
            assertEquals(time, window.start)
            assertEquals(time, window.end)
            emit(HistoricalSms("AD-EXBANK", time, 0, body))
        })
        assertTrue(result is OriginalSmsResult.Found)
    }

    @Test fun manual_and_missing_fingerprint_do_not_read_inbox() = runBlocking {
        val forbidden = HistoricalSmsSource { _, _ -> fail("Inbox must not be queried") }
        assertSame(OriginalSmsResult.Unavailable, find(row().copy(sourceType = "Manual"), forbidden))
        assertSame(OriginalSmsResult.Unavailable, find(row().copy(sourceFingerprint = null), forbidden))
    }

    @Test fun permission_denied_does_not_read_inbox() = runBlocking {
        assertSame(OriginalSmsResult.PermissionRequired,
            find(source = HistoricalSmsSource { _, _ -> fail("No permission") }, permitted = { false }))
    }

    @Test fun revocation_after_read_discards_candidate() = runBlocking {
        var allowed = true
        val messages = HistoricalSmsSource { _, emit ->
            emit(HistoricalSms("AD-EXBANK", time, 0, body)); allowed = false
        }
        assertSame(OriginalSmsResult.PermissionRequired, find(source = messages, permitted = { allowed }))
    }

    @Test fun provider_failure_after_candidate_does_not_expose_partial_result() = runBlocking {
        val messages = HistoricalSmsSource { _, emit ->
            emit(HistoricalSms("AD-EXBANK", time, 0, body)); throw IllegalStateException("provider")
        }
        assertSame(OriginalSmsResult.Unavailable, find(source = messages))
    }

    @Test fun security_failure_is_permission_required_and_cancellation_is_not_swallowed() = runBlocking {
        assertSame(OriginalSmsResult.PermissionRequired,
            find(source = HistoricalSmsSource { _, _ -> throw SecurityException() }))
        try {
            find(source = HistoricalSmsSource { _, _ -> throw CancellationException() })
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun oversized_candidate_scan_fails_closed_even_after_exact_match() = runBlocking {
        val messages = HistoricalSmsSource { _, emit ->
            emit(HistoricalSms("AD-EXBANK", time, 0, body))
            repeat(200) { emit(HistoricalSms("OTHER", time, 0, "unrelated")) }
        }
        assertSame(OriginalSmsResult.Unavailable, find(source = messages))
    }

    @Test fun raw_message_is_redacted_in_debug_representations() = runBlocking {
        val result = find(source = source(HistoricalSms("AD-EXBANK", time, 0, body)))
        assertTrue(result is OriginalSmsResult.Found)
        assertFalse(result.toString().contains(body))
        assertFalse(result.toString().contains("AD-EXBANK"))
        assertFalse((result as OriginalSmsResult.Found).message.toString().contains(body))
    }
}
