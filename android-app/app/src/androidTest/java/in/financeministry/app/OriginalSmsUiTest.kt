package `in`.financeministry.app

import android.Manifest
import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.OriginalSmsAction
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class OriginalSmsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val body = "INR 42.50 debited via UPI to SAMPLE SHOP. Ref 000000000001. PRIVATE SOURCE FIXTURE"

    private fun withSms(check: (TransactionRepository, TransactionEntity, HistoricalSmsSource) -> Unit) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_SMS)
        val repo = TransactionRepository(context, "original_ui_${UUID.randomUUID()}")
        try {
            val now = System.currentTimeMillis() - 1000
            val source = HistoricalSmsSource { _, emit -> emit(HistoricalSms("AD-EXBANK", now, now - 1000, body)) }
            val row = runBlocking {
                repo.commitImport(repo.previewImport(source)); repo.snapshot().rows.single()
            }
            check(repo, row, source)
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun viewing_is_opt_in_secure_transient_and_does_not_change_transaction() = withSms { repo, row, source ->
        var queries = 0
        val counted = HistoricalSmsSource { window, emit -> queries++; source.read(window, emit) }
        rule.setContent { MaterialTheme { OriginalSmsAction(repo, row, counted) } }
        rule.onNodeWithText(body).assertDoesNotExist()
        assertEquals(0, queries)
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("AD-EXBANK").assertIsDisplayed()
        rule.runOnIdle {
            // Read the real window flags without Espresso requiring focus on a dialog root.
            val dialogs = android.view.inspector.WindowInspector.getGlobalWindowViews().filter {
                it !== rule.activity.window.decorView && it.visibility == android.view.View.VISIBLE
            }
            assertTrue("Message dialog must block screenshots/recents", dialogs.any {
                val params = it.layoutParams as? WindowManager.LayoutParams
                params != null && params.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            })
        }
        rule.onNodeWithText("Close").performClick()
        rule.onNodeWithText(body).assertDoesNotExist()
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, queries) // Re-read, never a retained message cache.
        rule.onNodeWithText("Close").performClick()
        assertEquals(row.updatedAt, runBlocking { repo.get(row.id)!!.updatedAt })
        assertEquals(1, runBlocking { repo.snapshot().rows.size })
    }

    @Test fun backgrounding_discards_message_and_does_not_redisplay_it_on_resume() = withSms { repo, row, source ->
        rule.setContent { MaterialTheme { OriginalSmsAction(repo, row, source) } }
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText(body).assertDoesNotExist()
        rule.onNodeWithText("View original SMS").assertIsDisplayed()
    }

    @Test fun closing_a_pending_lookup_cancels_it_without_late_display() = withSms { repo, row, _ ->
        val entered = java.util.concurrent.atomic.AtomicBoolean(false)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val slow = HistoricalSmsSource { _, _ ->
            entered.set(true)
            try { awaitCancellation() } finally { cancelled.set(true) }
        }
        rule.setContent { MaterialTheme { OriginalSmsAction(repo, row, slow) } }
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { entered.get() }
        rule.onNodeWithText("Close").performClick()
        rule.waitUntil(5000) { cancelled.get() }
        rule.onNodeWithText(body).assertDoesNotExist()
        rule.onNodeWithText("Original SMS").assertDoesNotExist()
    }

    @Test fun switching_transactions_drops_the_previous_message() = withSms { repo, row, source ->
        val selected = androidx.compose.runtime.mutableStateOf(row)
        rule.setContent { MaterialTheme { OriginalSmsAction(repo, selected.value, source) } }
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { selected.value = row.copy(id = "another-record") }
        rule.onNodeWithText(body).assertDoesNotExist()
        rule.onNodeWithText("Original SMS").assertDoesNotExist()
    }

    @Test fun unavailable_and_erasure_drop_the_viewer_without_changing_totals() = withSms { repo, row, source ->
        var deleted = true
        val currentSource = HistoricalSmsSource { window, emit -> if (!deleted) source.read(window, emit) }
        rule.setContent { MaterialTheme { OriginalSmsAction(repo, row, currentSource) } }
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText("Original message unavailable").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(body).assertDoesNotExist()
        rule.onNodeWithText("Close").performClick()
        deleted = false
        rule.onNodeWithText("View original SMS").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithText(body).fetchSemanticsNodes().isNotEmpty() }
        runBlocking { repo.eraseAll() }
        rule.waitUntil(5000) { rule.onAllNodesWithText(body).fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { repo.snapshot().rows.isEmpty() })
    }

    @Test fun manual_transactions_offer_no_sms_action() {
        val repo = TransactionRepository(context, "original_manual_ui_${UUID.randomUUID()}")
        try {
            val row = runBlocking {
                val id = repo.save(ManualInput("10", Direction.Debit, System.currentTimeMillis(), TransactionType.Other))
                repo.get(id)!!
            }
            rule.setContent { MaterialTheme { OriginalSmsAction(repo, row) } }
            rule.onNodeWithText("View original SMS").assertDoesNotExist()
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }

    @Test fun actual_payment_details_offer_the_action_for_sms_records() = withSms { repo, row, _ ->
        repo.preferences.edit().putBoolean("onboarding_complete", true)
            .putInt("app_tour_seen_version", BuildConfig.VERSION_CODE).commit()
        rule.setContent { LedgerApp(repo, row.id to false, consumeRequest = {}) }
        rule.waitUntil(15000) { rule.onAllNodesWithText("View original SMS").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("View original SMS").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Close transaction details").assertIsDisplayed()
    }

    @Test fun denied_inbox_access_discloses_permission_without_reading_or_requesting_automatically() {
        val deniedContext = object : android.content.ContextWrapper(context) {
            override fun checkSelfPermission(permission: String): Int =
                if (permission == Manifest.permission.READ_SMS) android.content.pm.PackageManager.PERMISSION_DENIED
                else super.checkSelfPermission(permission)
        }
        val repo = TransactionRepository(deniedContext, "original_denied_ui_${UUID.randomUUID()}")
        try {
            val now = System.currentTimeMillis()
            val row = TransactionEntity("fixture", ByteArray(32), "SMS", now, now, 4250,
                direction = "Debit", status = "Successful", channel = "UPI", transactionType = "Other",
                reviewState = "AutoRecorded", createdAt = now, updatedAt = now)
            rule.setContent { MaterialTheme {
                OriginalSmsAction(repo, row, HistoricalSmsSource { _, _ -> fail("Permission not granted") })
            } }
            rule.onNodeWithText("Read original SMS?").assertDoesNotExist()
            rule.onNodeWithText("View original SMS").performClick()
            rule.onNodeWithText("Read original SMS?").assertIsDisplayed()
            rule.onNodeWithText("Allow inbox access").assertIsDisplayed()
            rule.onNodeWithText("Not now").performClick()
            rule.onNodeWithText("Read original SMS?").assertDoesNotExist()
            rule.onNodeWithText("View original SMS").assertIsDisplayed()
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }
}
