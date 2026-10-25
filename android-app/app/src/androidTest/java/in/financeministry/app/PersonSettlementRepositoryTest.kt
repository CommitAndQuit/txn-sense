package `in`.financeministry.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class PersonSettlementRepositoryTest {
    private fun repo() = TransactionRepository(ApplicationProvider.getApplicationContext<Application>(), "settlement_${UUID.randomUUID()}")
    private suspend fun seed(r: TransactionRepository): PersonRepaymentBalance {
        val alice = r.addLocalMember("Alice")
        val bob = r.addLocalMember("Bob")
        repeat(2) { index ->
            val id = r.save(ManualInput("900", Direction.Debit, System.currentTimeMillis() - (2-index)*86400000L,
                TransactionType.Other, label = "Dinner $index", ownership = SpendingOwnership.Group, groupLabel = "Friends", personalShare = "300"))
            r.saveGroupSplit(id, 30000, listOf(MemberShareInput(alice.id, 30000), MemberShareInput(bob.id, 30000)), r.get(id)!!.updatedAt)
        }
        return RepaymentAccounting.people(r.repaymentSummary(LocalDate.now())).single { it.name == "Alice" }
    }
    @Test fun cash_batch_creates_one_receipt_and_undo_keeps_receipt_and_other_person_unchanged() = runBlocking {
        val r = repo()
        try {
            val person = seed(r)
            val plan = planPersonRepayment(person, 45000, person.expenses.map { it.expense.id }.toSet())
            assertEquals(listOf(30000L, 15000L), plan.map { it.amountMinor })
            assertEquals("Dinner 0", person.expenses.first { it.expense.id == plan.first().expenseId }.expense.counterpartyLabel)
            val result = r.recordPersonRepayment(person.id, plan, System.currentTimeMillis())
            assertEquals(1, result.map { it.incomingId }.distinct().size)
            assertEquals(45000L, r.snapshot().credit.toLong())
            assertEquals(60000L, r.snapshot().personalSpend.toLong())
            val people = RepaymentAccounting.people(r.repaymentSummary(LocalDate.now()))
            assertEquals(15000L, people.single { it.name == "Alice" }.outstanding.toLong())
            assertEquals(60000L, people.single { it.name == "Bob" }.outstanding.toLong())
            r.undoPersonRepayment(result)
            assertEquals(120000L, r.snapshot().outstandingRepayments.toLong())
            assertEquals(45000L, r.snapshot().credit.toLong())
            assertNotNull(r.get(result.first().incomingId!!))
        } finally { r.eraseAll(); r.close() }
    }
    @Test fun linked_batch_reuses_receipt_and_rejects_repeat_or_insufficient_credit_atomically() = runBlocking {
        val r = repo()
        try {
            val person = seed(r)
            val credit = r.save(ManualInput("400", Direction.Credit, System.currentTimeMillis(), TransactionType.Other))
            val date = r.get(credit)!!.effectiveTimestamp
            val tooMuch = planPersonRepayment(person, 45000, person.expenses.map { it.expense.id }.toSet())
            try { r.recordPersonRepayment(person.id, tooMuch, date, credit); fail("Must reject insufficient receipt") } catch (_: IllegalArgumentException) { }
            assertTrue(person.expenses.all { r.repaymentsFor(it.expense.id).isEmpty() })
            val plan = planPersonRepayment(person, 40000, person.expenses.map { it.expense.id }.toSet())
            val result = r.recordPersonRepayment(person.id, plan, date, credit)
            assertEquals(40000L, r.snapshot().credit.toLong())
            try { r.recordPersonRepayment(person.id, plan, date, credit); fail("Must reject stale plan") } catch (_: IllegalArgumentException) { }
            r.editRepayment(result.last().id, "50", date, "Alice")
            try { r.undoPersonRepayment(result); fail("Must reject changed allocation") } catch (_: IllegalArgumentException) { }
            assertEquals(35000L, person.expenses.sumOf { r.repaymentsFor(it.expense.id).sumOf { entry -> entry.amountMinor } })
        } finally { r.eraseAll(); r.close() }
    }
    @Test fun preview_rejects_overpayment_and_only_uses_selected_expenses() = runBlocking {
        val r = repo()
        try {
            val person = seed(r)
            val selected = setOf(person.expenses.first().expense.id)
            assertEquals(selected, planPersonRepayment(person, 80, selected).map { it.expenseId }.toSet())
            try { planPersonRepayment(person, 30001, selected); fail("Must not spill into unselected expense") } catch (_: IllegalArgumentException) { }
            try { planPersonRepayment(person, 0, selected); fail("Must reject zero") } catch (_: IllegalArgumentException) { }
        } finally { r.eraseAll(); r.close() }
    }
    @Test fun interrupted_cash_save_retries_same_operation_without_creating_a_second_receipt() = runBlocking {
        val r = repo()
        try {
            val person = seed(r)
            val operation = UUID.randomUUID().toString()
            val date = System.currentTimeMillis()
            val plan = planPersonRepayment(person, 10000, person.expenses.map { it.expense.id }.toSet())
            val saved = r.recordPersonRepayment(person.id, plan, date, operationId = operation)
            assertEquals(saved, r.recordPersonRepayment(person.id, plan, date, operationId = operation))
            assertEquals(10000L, r.snapshot().credit.toLong())
            r.undoPersonRepayment(saved)
            try { r.recordPersonRepayment(person.id, plan, date, operationId = operation); fail("An undone operation must not run again") } catch (_: IllegalArgumentException) { }
            assertEquals(10000L, r.snapshot().credit.toLong())
        } finally { r.eraseAll(); r.close() }
    }
}
