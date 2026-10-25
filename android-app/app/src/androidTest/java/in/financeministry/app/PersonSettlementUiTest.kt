package `in`.financeministry.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.feature.PeopleContent
import `in`.financeministry.app.ui.FinanceLedgerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

class PersonSettlementUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun person_cash_settlement_requires_preview_and_supports_undo() {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "settlement_ui_${UUID.randomUUID()}")
        try {
            runBlocking {
                repeat(2) { index -> r.save(ManualInput("300", Direction.Debit, System.currentTimeMillis() - index*1000,
                    TransactionType.Other, label = "Meal $index", ownership = SpendingOwnership.ForOther, groupLabel = "Alice", personalShare = "0")) }
            }
            rule.setContent { FinanceLedgerTheme(false) { PeopleContent(r, LocalDate.now(), {}, {}) } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Alice").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Alice").performClick()
            rule.onNodeWithText("Settle multiple expenses").performClick()
            rule.onNodeWithText("Received cash").performClick()
            rule.onNode(editableLedgerField("Amount received (INR)")).performTextReplacement("450")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithText("Preview allocation").performClick()
            rule.onNodeWithText("Confirm settlement").assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(instrumentation.targetContext.getExternalFilesDir("settlement-review"), "preview.png")
                    .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            assertEquals(60000L, runBlocking { r.snapshot().outstandingRepayments.toLong() })
            rule.onNodeWithText("Confirm settlement").performClick()
            rule.waitUntil(15000) { runBlocking { r.snapshot().outstandingRepayments.toLong() == 15000L } }
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Undo settlement"))
            rule.onNodeWithText("Undo settlement").performClick()
            rule.waitUntil(15000) { runBlocking { r.snapshot().outstandingRepayments.toLong() == 60000L } }
            assertEquals(45000L, runBlocking { r.snapshot().credit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }

    @Test fun linked_preview_can_be_changed_then_saved_without_adding_money_in() {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "settlement_link_ui_${UUID.randomUUID()}")
        try {
            runBlocking {
                repeat(2) { index -> r.save(ManualInput("300", Direction.Debit, System.currentTimeMillis() - index*1000,
                    TransactionType.Other, label = "Trip $index", ownership = SpendingOwnership.ForOther, groupLabel = "Alice")) }
                r.save(ManualInput("500", Direction.Credit, System.currentTimeMillis(), TransactionType.Other, label = "Alice receipt"))
            }
            rule.setContent { FinanceLedgerTheme(true) { PeopleContent(r, LocalDate.now(), {}, {}) } }
            rule.waitUntil(15000) { rule.onAllNodesWithText("Alice").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Alice").performClick()
            rule.onNodeWithText("Settle multiple expenses").performClick()
            rule.onNodeWithText("Choose recorded receipt").performClick()
            rule.onNodeWithText("Alice receipt").performClick()
            rule.onNodeWithText("Preview allocation").performClick()
            rule.onNodeWithText("Change allocation").performScrollTo().performClick()
            assertEquals(60000L, runBlocking { r.snapshot().outstandingRepayments.toLong() })
            rule.onNode(editableLedgerField("Amount received (INR)")).performTextReplacement(".80")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            rule.onNodeWithText("Preview allocation").performClick()
            rule.onNodeWithText("Confirm settlement").performClick()
            rule.waitUntil(15000) { runBlocking { r.snapshot().outstandingRepayments.toLong() == 59920L } }
            assertEquals(50000L, runBlocking { r.snapshot().credit.toLong() })
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
