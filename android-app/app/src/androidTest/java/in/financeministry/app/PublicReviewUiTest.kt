package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Pending records never enter totals; dismissal retains evidence and can be reversed. */
class PublicReviewUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun seed(status: String = "Successful", channel: String = "Card", type: String = "MerchantPayment"): Pair<TransactionRepository, TransactionEntity> {
        val r=TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "public_review_${UUID.randomUUID()}")
        val now=System.currentTimeMillis()
        val row=TransactionEntity("uncertain", sourceType="SMS", sourceTimestamp=now, effectiveTimestamp=now,
            amountMinor=47000, direction="Debit", status=status, channel=channel, transactionType=type,
            counterpartyLabel="Card payment", reviewState="NeedsReview", createdAt=now, updatedAt=now)
        runBlocking {r.withLedger {it.transactions().insert(row)}}
        r.preferences.edit().putBoolean("onboarding_complete",true).putInt("app_tour_seen_version",BuildConfig.VERSION_CODE).commit()
        rule.runOnUiThread {rule.activity.enableEdgeToEdge()}
        rule.setContent {FinanceMinistryTheme {LedgerApp(r,null,consumeRequest={})}}
        rule.waitUntil(15000) {rule.onAllNodesWithContentDescription("Open review tab").fetchSemanticsNodes().isNotEmpty()}
        rule.onNodeWithContentDescription("Open review tab").performClick()
        return r to row
    }
    @Test fun pending_status_can_be_corrected_without_opening_advanced_details() {
        val (r, row) = seed(status = "Pending")
        try {
            rule.onNodeWithText("Check & confirm").performScrollTo().performClick()
            rule.onNodeWithText("Check payment status").assertIsDisplayed()
            rule.onNodeWithText("Pending").performClick()
            rule.onNodeWithText("Successful").performClick()
            rule.onNodeWithText("Confirm payment").performClick()
            rule.waitUntil(15000) { runBlocking { r.get(row.id)?.reviewState == "Confirmed" } }
            assertEquals("Successful", runBlocking { r.get(row.id)!!.status })
            assertEquals(47000L, runBlocking { r.snapshot().debit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun uncertain_method_and_type_are_correctable_before_optional_details() {
        val (r, row) = seed(channel = "Unknown", type = "Unknown")
        try {
            rule.onNodeWithText("Check & confirm").performScrollTo().performClick()
            rule.onNodeWithText("Check payment method").assertIsDisplayed()
            rule.onAllNodesWithText("Not identified")[0].performClick()
            rule.onNodeWithText("UPI").performClick()
            rule.onNodeWithText("Check payment kind").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Not identified").performClick()
            rule.onNodeWithText("Purchase").performClick()
            rule.onNodeWithText("Confirm payment").performClick()
            rule.waitUntil(15000) { runBlocking { r.get(row.id)?.reviewState == "Confirmed" } }
            val saved = runBlocking { r.get(row.id)!! }
            assertEquals("UPI", saved.channel)
            assertEquals("MerchantPayment", saved.transactionType)
            assertEquals(47000L, runBlocking { r.snapshot().debit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun missing_payment_from_review_opens_manual_entry_without_confirming_existing_record() {
        val (r, row) = seed()
        try {
            rule.onNodeWithText("Missing a payment?").performClick()
            rule.onNodeWithText("Add a payment manually").performClick()
            rule.onNode(hasText("Add payment") and hasClickAction()).assertExists()
            assertEquals("NeedsReview", runBlocking { r.get(row.id)!!.reviewState })
            assertEquals(1, runBlocking { r.snapshot().rows.size })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun activity_recovery_routes_to_diagnostics_and_opt_in_import_without_mutation() {
        val (r, row) = seed()
        try {
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.onNodeWithText("Missing a payment?").performClick()
            rule.onNodeWithText("Check SMS capture").performScrollTo().performClick()
            rule.onNodeWithText("Permission and capture status on this device.").assertExists()
            rule.onNodeWithContentDescription("Close settings").performClick()
            rule.onNodeWithText("Missing a payment?").performClick()
            rule.onNodeWithText("Import past messages").performScrollTo().performClick()
            rule.onNodeWithText("Past transactions").assertExists()
            rule.onNodeWithText("Import last 3 months").assertExists()
            assertEquals("NeedsReview", runBlocking { r.get(row.id)!!.reviewState })
            assertEquals(1, runBlocking { r.snapshot().rows.size })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun actionable_review_confirms_one_payment_and_updates_totals_once() {
        val (r,row)=seed()
        try {
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            rule.onNodeWithText("Details need checking").assertIsDisplayed()
            rule.onNodeWithText("₹470.00").assertIsDisplayed()
            capture("review-pending-light.png")
            rule.onNodeWithText("Check & confirm").performClick()
            rule.onNodeWithText("Check this payment").assertIsDisplayed()
            rule.onNodeWithText("Confirm payment").performClick()
            rule.waitUntil(15000) {runBlocking {r.get(row.id)?.reviewState=="Confirmed"}}
            assertEquals(47000L,runBlocking {r.snapshot().debit.toLong()})
            assertEquals(47000L,runBlocking {r.snapshot().personalSpend.toLong()})
            assertEquals(0,runBlocking {r.reviewCount()})
            assertEquals(1,runBlocking {r.snapshot().rows.size})
        } finally {runBlocking {r.eraseAll();r.close()}}
    }
    @Test fun not_a_transaction_preserves_record_and_can_return_to_review() {
        val (r,row)=seed()
        try {
            rule.onNodeWithText("Not a transaction").performClick()
            rule.onNodeWithText("Exclude this alert?").assertIsDisplayed()
            rule.onNodeWithText("Keep in review").performClick()
            assertEquals("NeedsReview",runBlocking {r.get(row.id)!!.reviewState})
            rule.onNodeWithText("Not a transaction").performClick()
            rule.onNodeWithText("Exclude alert").performClick()
            rule.waitUntil(15000) {runBlocking {r.get(row.id)?.reviewState=="Dismissed"}}
            assertEquals(0,runBlocking {r.reviewCount()})
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            assertEquals(0L,runBlocking {r.snapshot().resultDebit.toLong()})
            assertEquals(0L,runBlocking {r.snapshot().personalSpend.toLong()})
            assertEquals(1,runBlocking {r.snapshot().rows.size})
            rule.onNodeWithContentDescription("Open activity").performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Card payment").fetchSemanticsNodes().isNotEmpty()}
            rule.onNodeWithText("Card payment").performTouchInput {longClick()}
            rule.onNodeWithText("Edit / confirm").assertDoesNotExist()
            rule.onNodeWithText("Category & purpose").assertDoesNotExist()
            rule.onNodeWithText("Mark as card bill payment").assertDoesNotExist()
            rule.onNodeWithText("Restore to review").performScrollTo().performClick()
            rule.waitUntil(15000) {runBlocking {r.get(row.id)?.reviewState=="NeedsReview"}}
            assertEquals(1,runBlocking {r.reviewCount()})
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            assertEquals("Successful",runBlocking {r.get(row.id)!!.status})
        } finally {runBlocking {r.eraseAll();r.close()}}
    }
    @Test fun old_editor_cannot_confirm_after_an_alert_is_excluded_then_restored() {
        val (r,row)=seed()
        try {
            rule.onNodeWithText("Check & confirm").performClick()
            runBlocking {
                r.dismissReview(row.id,row.updatedAt)
                val excluded=r.get(row.id)!!
                r.restoreDismissedReview(row.id,excluded.updatedAt)
            }
            rule.onNodeWithText("Confirm payment").performClick()
            rule.waitUntil(15000) {
                runBlocking {r.get(row.id)?.reviewState=="Confirmed"} ||
                    rule.onAllNodesWithText("This record changed. Reopen it before saving.").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("NeedsReview",runBlocking {r.get(row.id)!!.reviewState})
            assertEquals(0L,runBlocking {r.snapshot().debit.toLong()})
            rule.onNodeWithText("This record changed. Reopen it before saving.").assertIsDisplayed()
        } finally {runBlocking {r.eraseAll();r.close()}}
    }
    private fun capture(name:String) {
        rule.waitForIdle();rule.mainClock.advanceTimeBy(500);rule.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation()
        val variant=InstrumentationRegistry.getArguments().getString("visualVariant")
            ?.takeIf {it in setOf("light","dark","compact-light","compact-dark","large-text-light","large-text-dark")} ?: "light"
        val f=File(i.targetContext.getExternalFilesDir("public-ui"),name.replace("-light.png","-$variant.png"))
        f.outputStream().use {check(i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
    }
}
