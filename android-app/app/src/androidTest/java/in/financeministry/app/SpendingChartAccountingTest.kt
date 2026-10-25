package `in`.financeministry.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class SpendingChartAccountingTest {
    @Test fun home_chart_uses_whole_month_not_page_or_filters_and_matches_personal_total() = runBlocking {
        val repo = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "chart_${UUID.randomUUID()}")
        val month = LocalDate.of(2026, 10, 1)
        val timestamp = month.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun row(id: String) = TransactionEntity(id, sourceType="Manual", sourceTimestamp=timestamp,
            effectiveTimestamp=timestamp, amountMinor=10000, direction="Debit", status="Successful",
            channel="CashManual", transactionType="Other", reviewState="Confirmed", category="Food", createdAt=1, updatedAt=1)
        try {
            repo.withLedger { db ->
                (1..101).forEach { db.transactions().insert(row("personal$it")) }
                listOf(row("group").copy(category="Bills", ownership="Group", personalShareMinor=2500, repaymentExpected=true),
                    row("review").copy(reviewState="NeedsReview"), row("bill").copy(transactionType="CardRepayment"),
                    row("transfer").copy(ownership="SelfTransfer"), row("refund").copy(direction="Credit", transactionType="Refund"))
                    .forEach { db.transactions().insert(it) }
            }
            val snapshot = repo.snapshot(today=month, filter="Credit")
            assertEquals(1, snapshot.rows.size)
            assertEquals(BigInteger.valueOf(1012500), snapshot.personalSpend)
            assertEquals(snapshot.personalSpend, snapshot.spendingCategories.fold(BigInteger.ZERO) { sum, category -> sum + category.amount })
            assertEquals(BigInteger.valueOf(2500), snapshot.spendingCategories.single { it.category=="Bills" }.amount)
            assertEquals(BigInteger.valueOf(1010000), snapshot.spendingCategories.single { it.category=="Food" }.amount)
        } finally { repo.eraseAll(); repo.close() }
    }
}
