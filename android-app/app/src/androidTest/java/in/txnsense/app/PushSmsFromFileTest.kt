package `in`.txnsense.app

import android.Manifest

import androidx.test.platform.app.InstrumentationRegistry

import `in`.txnsense.app.data.FileHistoricalSmsSource
import `in`.txnsense.app.data.TransactionRepository

import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import java.util.UUID
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class PushSmsFromFileTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun push_and_parse_sms_file() = runBlocking {
        val context = rule.activity
        // Grant READ_SMS permission
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)

        // Prepare test SMS export file in app's external files dir
        val file = File(context.getExternalFilesDir(null), "All Conversations 2026-09-15 15_04_50.txt")
        val smsText = """
Received from TEST on 2026-09-15 15:04
INR 42.00 debited via UPI account 1234

Received from TEST on 2026-09-15 15:05
INR 100.50 credited via UPI account 5678
""".trimIndent()
file.writeText(smsText)

        // Create a new isolated repository
        val repo = TransactionRepository(context, "smsfiletest_${UUID.randomUUID()}")
        try {
            val source = FileHistoricalSmsSource(file)
            val now = System.currentTimeMillis()
            val preview = repo.previewImport(source, now)
            assertTrue("No messages were parsed", preview.ready > 0)
            val commitResult = repo.commitImport(preview)
            assertEquals(preview.ready, commitResult.inserted)

            val snapshot = repo.snapshot(today = LocalDate.of(2026, 9, 1))
            assertFalse("Ledger should not be empty", snapshot.rows.isEmpty())

            val amounts = snapshot.rows.map { it.amountMinor }.toSet()
            assertTrue("4200 not found", amounts.contains(4200L))
            assertTrue("10050 not found", amounts.contains(10050L))
        } finally {
            repo.eraseAll()
            repo.close()
            file.delete()
        }
    }
}
