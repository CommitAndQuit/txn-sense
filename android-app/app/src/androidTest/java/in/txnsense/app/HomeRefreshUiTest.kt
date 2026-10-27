package `in`.txnsense.app

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.txnsense.app.data.HistoricalSms
import `in`.txnsense.app.data.HistoricalSmsSource
import `in`.txnsense.app.data.ImportWindow
import `in`.txnsense.app.data.SMS_INBOX_REFRESH_DISCLOSURE_ACCEPTED
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.feature.SpendTrackerScreen
import `in`.txnsense.app.ui.TxnSenseTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth
import java.util.UUID

class HomeRefreshUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun pull_to_refresh_discloses_once_then_reports_the_summary() {
        val context = rule.activity
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        val repository = TransactionRepository(context, "refresh_ui_${UUID.randomUUID()}")
        val now = System.currentTimeMillis()
        var calls = 0
        var window: ImportWindow? = null
        val source = HistoricalSmsSource { requested, emit ->
            calls++
            window = requested
            if (calls == 1) {
                emit(HistoricalSms("TEST", now - 1000, 0, "INR 42 debited via UPI"))
                emit(HistoricalSms("TEST", now - 2000, 0, "INR 43 debited via UPI"))
            }
        }
        try {
            rule.activity.runOnUiThread {
                rule.activity.setContent {
                    TxnSenseTheme {
                        SpendTrackerScreen(
                            repository = repository, month = YearMonth.now().toString(), overview = null,
                            onMonthChange = {}, timeFilter = "This Month", onTimeFilter = {},
                            onOpenTransaction = {}, onOpenSettings = {}, historicalSmsSource = source)
                    }
                }
            }
            rule.onNodeWithTag("home_refresh").performTouchInput { swipeDown() }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Read existing SMS?").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("I understand — refresh").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText("Added 2 transactions").fetchSemanticsNodes().isNotEmpty() }
            assertNotNull(window)
            assertEquals(ImportWindow.lastTwoMonths(window!!.end), window)
            assertEquals(true, repository.preferences.getBoolean(SMS_INBOX_REFRESH_DISCLOSURE_ACCEPTED, false))

            rule.onNodeWithTag("home_refresh").performTouchInput { swipeDown() }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Up to date").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Read existing SMS?").assertDoesNotExist()
            assertEquals(2, calls)
            assertEquals(2, runBlocking { repository.latestImport()!!.inserted })
        } finally {
            runBlocking { repository.eraseAll(); repository.close() }
        }
    }
}
