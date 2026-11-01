package `in`.txnsense.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Per-method spend/income aggregation: refunds net against spend and never count as income. */
class SpendAggregationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private fun repository() = TransactionRepository(context, "spend_agg_${UUID.randomUUID()}")
    private fun spend(amount: String, channel: Channel, type: TransactionType = TransactionType.MerchantPayment) =
        ManualInput(amount, Direction.Debit, System.currentTimeMillis(), type, channel = channel)
    private fun credit(amount: String, channel: Channel, type: TransactionType) =
        ManualInput(amount, Direction.Credit, System.currentTimeMillis(), type, channel = channel)

    @Test fun spend_nets_refunds_and_income_excludes_them() = runBlocking {
        val repo = repository()
        try {
            repo.save(spend("1000.00", Channel.CreditCard))
            repo.save(spend("400.00", Channel.CreditCard))
            repo.save(credit("150.00", Channel.CreditCard, TransactionType.Refund))   // nets against CC spend
            repo.save(spend("250.00", Channel.UPI))
            repo.save(credit("5000.00", Channel.BankTransfer, TransactionType.SalaryIncome)) // real income
            repo.save(spend("300.00", Channel.UPI, TransactionType.SelfTransfer))       // excluded
            repo.save(spend("700.00", Channel.CreditCard, TransactionType.CardRepayment)) // excluded

            val spend = repo.spendByMethod().associateBy { it.channel }
            assertEquals(140000L, spend.getValue("CreditCard").grossSpendMinor)
            assertEquals(15000L, spend.getValue("CreditCard").refundMinor)
            assertEquals(125000L, spend.getValue("CreditCard").netSpendMinor)   // 1400 - 150
            assertEquals(2, spend.getValue("CreditCard").spendCount)            // repayment not counted
            assertEquals(25000L, spend.getValue("UPI").netSpendMinor)           // self-transfer excluded
            assertEquals(1, spend.getValue("UPI").spendCount)
            assertNull("income methods are not spends", spend["BankTransfer"])

            val income = repo.incomeByMethod().associateBy { it.channel }
            assertEquals(500000L, income.getValue("BankTransfer").incomeMinor)
            assertNull("refund is not income", income["CreditCard"])
            assertNull("spend-only method has no income", income["UPI"])
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun non_expense_entries_are_excluded_from_spend_but_remain_in_history() = runBlocking {
        val repo = repository()
        val now = System.currentTimeMillis()
        try {
            val expense = repo.save(ManualInput("100.00", Direction.Debit, now,
                TransactionType.MerchantPayment, channel = Channel.UPI))
            val investment = repo.save(ManualInput("200.00", Direction.Debit, now,
                TransactionType.Other, channel = Channel.UPI, spendType = SpendingType.Investment))
            val family = repo.save(ManualInput("300.00", Direction.Debit, now,
                TransactionType.Other, channel = Channel.BankTransfer, ownership = SpendingOwnership.ForOther,
                spendType = SpendingType.TransferToFamily))
            val self = repo.save(ManualInput("400.00", Direction.Debit, now,
                TransactionType.Other, channel = Channel.BankTransfer, spendType = SpendingType.TransferToSelf))
            val investmentCredit = repo.save(ManualInput("50.00", Direction.Credit, now,
                TransactionType.Other, channel = Channel.BankTransfer, spendType = SpendingType.Investment))

            val snapshot = repo.snapshot()
            assertEquals(5, snapshot.rows.size)
            assertEquals("10000", snapshot.debit.toString())
            assertEquals("5000", snapshot.credit.toString())
            assertEquals("10000", snapshot.dailyDebit.toString())
            assertEquals("0", snapshot.paidForOthers.toString())
            assertEquals("0", snapshot.outstandingRepayments.toString())
            assertEquals(setOf(expense, investmentCredit), repo.snapshot(filter = "Personal").rows.map { it.id }.toSet())

            val spends = repo.spendByMethod().associateBy { it.channel }
            assertEquals(10000L, spends.getValue("UPI").netSpendMinor)
            assertNull(spends["BankTransfer"])
            assertEquals(5000L, repo.incomeByMethod().associateBy { it.channel }.getValue("BankTransfer").incomeMinor)

            val overview = repo.spendOverview()
            assertEquals(10000L, overview.totalSpendMinor)
            assertEquals(listOf(expense), overview.recentSpends.map { it.id })

            assertEquals("Investment", repo.get(investment)!!.spendType)
            assertEquals("TransferToFamily", repo.get(family)!!.spendType)
            assertEquals("TransferToSelf", repo.get(self)!!.spendType)
            assertEquals("Expense", repo.get(investmentCredit)!!.spendType)
            assertNull(repaymentSummary(repo.get(family)!!))
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun category_spends_aggregates_eligible_expenses_by_category() = runBlocking {
        val repo = repository()
        val now = System.currentTimeMillis()
        try {
            val food1 = repo.save(ManualInput("300.00", Direction.Debit, now,
                TransactionType.MerchantPayment, channel = Channel.UPI, category = "Food"))
            val food2 = repo.save(ManualInput("150.00", Direction.Debit, now,
                TransactionType.MerchantPayment, channel = Channel.Card, category = "Food"))
            val travel = repo.save(ManualInput("200.00", Direction.Debit, now,
                TransactionType.MerchantPayment, channel = Channel.UPI, category = "Travel"))
            // Non-expense should be excluded
            repo.save(ManualInput("500.00", Direction.Debit, now,
                TransactionType.Other, channel = Channel.UPI, category = "Food", spendType = SpendingType.Investment))
            // Self-transfer should be excluded
            repo.save(ManualInput("400.00", Direction.Debit, now,
                TransactionType.SelfTransfer, channel = Channel.UPI, category = "Food"))

            val catSpends = repo.categorySpends()
            assertEquals(2, catSpends.size)

            val top = catSpends[0]
            assertEquals("Food", top.category)
            assertEquals(45000L, top.totalMinor)
            assertEquals(2, top.count)
            assertEquals(listOf(food1, food2).toSet(), top.transactions.map { it.id }.toSet())

            val second = catSpends[1]
            assertEquals("Travel", second.category)
            assertEquals(20000L, second.totalMinor)
            assertEquals(1, second.count)
            assertEquals(listOf(travel), second.transactions.map { it.id })
        } finally { repo.eraseAll(); repo.close() }
    }

    @Test fun month_category_breakdown_classifies_major_groups_and_sub_groups() = runBlocking {
        val repo = repository()
        val now = System.currentTimeMillis()
        try {
            repo.save(ManualInput("300.00", Direction.Debit, now,
                TransactionType.MerchantPayment, channel = Channel.UPI, category = "Food"))
            repo.save(ManualInput("120.00", Direction.Debit, now,
                TransactionType.MerchantPayment, channel = Channel.UPI, category = "Groceries"))
            repo.save(ManualInput("1000.00", Direction.Debit, now,
                TransactionType.Other, channel = Channel.BankTransfer, category = "Stocks", spendType = SpendingType.Investment))
            repo.save(ManualInput("5000.00", Direction.Credit, now,
                TransactionType.SalaryIncome, channel = Channel.BankTransfer, category = "Salary"))

            val breakdown = repo.monthCategoryBreakdown()

            // Spends
            assertEquals(42000L, breakdown.spends.totalMinor)
            assertEquals(2, breakdown.spends.count)
            assertEquals(2, breakdown.spends.subGroups.size)
            assertEquals("Food", breakdown.spends.subGroups[0].name)
            assertEquals(30000L, breakdown.spends.subGroups[0].totalMinor)
            assertEquals("Groceries", breakdown.spends.subGroups[1].name)
            assertEquals(12000L, breakdown.spends.subGroups[1].totalMinor)

            // Investment
            assertEquals(100000L, breakdown.investment.totalMinor)
            assertEquals(1, breakdown.investment.count)
            assertEquals(1, breakdown.investment.subGroups.size)
            assertEquals("Stocks", breakdown.investment.subGroups[0].name)

            // Income
            assertEquals(500000L, breakdown.income.totalMinor)
            assertEquals(1, breakdown.income.count)
            assertEquals(1, breakdown.income.subGroups.size)
            assertEquals("Salary", breakdown.income.subGroups[0].name)
        } finally { repo.eraseAll(); repo.close() }
    }
}
