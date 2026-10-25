package `in`.financeministry.app

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OriginalSmsViewTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun grant() = InstrumentationRegistry.getInstrumentation().uiAutomation
        .grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
    private val body = "Sent Rs.42.50 from Example Bank A/c X0000 to Source Demo Cafe on 24-10-26. UPI Ref 000000000001."

    @Test fun exact_device_key_lookup_survives_edits_and_reopen_without_writing_to_ledger() = runBlocking {
        grant()
        val repo = TransactionRepository(context, "original_sms_${UUID.randomUUID()}")
        val time = System.currentTimeMillis() - 1000
        val sms = HistoricalSms("AD-EXBANK", time, time - 7000, body)
        val source = HistoricalSmsSource { _, emit -> emit(sms) }
        try {
            repo.commitImport(repo.previewImport(source))
            val row = repo.snapshot().rows.single()
            repo.save(ManualInput("45", Direction.Debit, time + 86400000, TransactionType.Other,
                channel = Channel.UPI, label = "User correction"), row.id)
            repo.close()
            val revision = repo.revision.value
            val before = repo.get(row.id)!!
            val result = repo.originalSms(row.id, source) as OriginalSmsResult.Found
            assertEquals(body, result.message.body)
            assertEquals("AD-EXBANK", result.message.sender)
            assertEquals(time, result.message.date)
            assertEquals(revision, repo.revision.value)
            val after = repo.get(row.id)!!
            assertEquals(before.amountMinor, after.amountMinor)
            assertEquals(before.updatedAt, after.updatedAt)
            assertEquals(before.effectiveTimestamp, after.effectiveTimestamp)
            assertEquals("User correction", after.counterpartyLabel)
            assertEquals(1, repo.snapshot().rows.size)
            assertFalse(result.toString().contains(body))
            assertSame(OriginalSmsResult.Unavailable, repo.originalSms(row.id, HistoricalSmsSource { _, _ -> }))
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun manual_and_erased_records_never_query_inbox() = runBlocking {
        grant()
        val repo = TransactionRepository(context, "original_manual_${UUID.randomUUID()}")
        val forbidden = HistoricalSmsSource { _, _ -> fail("No linked SMS") }
        try {
            val id = repo.save(ManualInput("10", Direction.Debit, System.currentTimeMillis(), TransactionType.Other))
            assertSame(OriginalSmsResult.Unavailable, repo.originalSms(id, forbidden))
            repo.eraseAll()
            assertSame(OriginalSmsResult.Unavailable, repo.originalSms(id, forbidden))
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun storage_failure_has_a_safe_unavailable_result_not_an_uncaught_exception() = runBlocking {
        var unavailable = false
        val failing = object : android.content.ContextWrapper(context) {
            override fun getDatabasePath(name: String): java.io.File {
                if (unavailable) throw IllegalStateException("Storage unavailable")
                return super.getDatabasePath(name)
            }
        }
        val repo = TransactionRepository(failing, "original_storage_${UUID.randomUUID()}")
        try {
            unavailable = true
            assertSame(OriginalSmsResult.Unavailable, repo.originalSms("missing"))
        } finally { unavailable = false; repo.eraseAll(); repo.close() }
    }

    @Test fun pending_provider_does_not_block_erasure_or_recreate_erased_keys() = runBlocking {
        grant()
        val namespace = "original_erase_${UUID.randomUUID()}"
        val repo = TransactionRepository(context, namespace)
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val time = System.currentTimeMillis() - 1000
        val sms = HistoricalSms("AD-EXBANK", time, time - 7000, body)
        try {
            repo.commitImport(repo.previewImport(HistoricalSmsSource { _, emit -> emit(sms) }))
            val row = repo.snapshot().rows.single()
            val lookup = async {
                repo.originalSms(row.id, HistoricalSmsSource { _, emit ->
                    entered.complete(Unit); resume.await(); emit(sms)
                })
            }
            try {
                entered.await()
                var erased = false
                try { withTimeout(3000) { repo.eraseAll(); erased = true } }
                catch (_: TimeoutCancellationException) { }
                resume.complete(Unit)
                val result = lookup.await()
                assertTrue("A stalled inbox query blocked erasure", erased)
                assertSame(OriginalSmsResult.Unavailable, result)
                assertFalse(context.getSharedPreferences("${namespace}_secrets", 0).getBoolean("hmac_created", false))
                assertTrue(repo.snapshot().rows.isEmpty())
            } finally { lookup.cancel() }
        } finally { resume.complete(Unit); repo.eraseAll(); repo.close() }
    }

    /** Opt in only on a disposable emulator with the fictional host-injected sample. */
    @Test fun real_android_inbox_provider_resolves_the_exact_original_message() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runOriginalSmsProvider") == "true")
        grant()
        val repo = TransactionRepository(context, "original_provider_${UUID.randomUUID()}")
        try {
            var fixture: HistoricalSms? = null
            AndroidHistoricalSmsSource(context).read(ImportWindow.lastThreeMonths()) {
                if (it.body.contains("ORIGINAL-SMS-VIEW-TEST")) fixture = it
            }
            val sms = requireNotNull(fixture) { "Inject the fictional source-view SMS first" }
            repo.commitImport(repo.previewImport(HistoricalSmsSource { _, emit -> emit(sms) }))
            val row = repo.snapshot().rows.single()
            val result = repo.originalSms(row.id) as OriginalSmsResult.Found
            assertEquals(sms.body, result.message.body)
            assertEquals(sms.sender, result.message.sender)
            assertEquals(sms.date, result.message.date)
        } finally { repo.eraseAll(); repo.close() }
    }
}
