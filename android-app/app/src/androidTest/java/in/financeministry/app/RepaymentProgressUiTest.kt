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
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class RepaymentProgressUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun monthly_expense_progress_does_not_subtract_receipts_for_older_expenses() {
        val r = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "progress_${UUID.randomUUID()}")
        val month = LocalDate.now().withDayOfMonth(1)
        fun time(day: LocalDate) = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            runBlocking {
                val old = r.save(ManualInput("600", Direction.Debit, time(month.minusDays(1)), TransactionType.Other,
                    ownership = SpendingOwnership.ForOther, groupLabel = "Alice", personalShare = "0"))
                val current = r.save(ManualInput("300", Direction.Debit, time(month), TransactionType.Other,
                    ownership = SpendingOwnership.ForOther, groupLabel = "Alice", personalShare = "0"))
                r.recordRepayment(old, "200", time(month), "Alice")
                r.recordRepayment(current, "50", time(month), "Alice")
            }
            rule.setContent { FinanceLedgerTheme(false) { PeopleContent(r, month, {}, {}) } }
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("All dates: expected ₹900.00, received ₹250.00, remaining ₹650.00").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Month breakdown").performScrollTo().performClick()
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Selected month's expenses: expected ₹300.00, received ₹50.00, remaining ₹250.00"))
            rule.onNodeWithContentDescription("Selected month's expenses: expected ₹300.00, received ₹50.00, remaining ₹250.00").assertExists()
        } finally { runBlocking { r.eraseAll(); r.close() } }
    }
}
