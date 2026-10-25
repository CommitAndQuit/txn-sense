package `in`.financeministry.app.data

import `in`.financeministry.app.core.model.*
import java.math.BigInteger
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class RepaymentPortion(val expenseId: String, val memberId: String?, val amountMinor: Long,
    val expectedOutstanding: Long, val expectedUpdatedAt: Long)

/** A proposal only. Integer paise; stable oldest-first order; never spills into unselected expenses. */
fun planPersonRepayment(person: PersonRepaymentBalance, amountMinor: Long, expenseIds: Set<String>): List<RepaymentPortion> {
    require(amountMinor > 0) { "Enter a positive repayment amount." }
    require(expenseIds.isNotEmpty() && expenseIds.all { id -> person.expenses.any { it.expense.id == id } }) { "Choose expenses to settle." }
    val expenses = person.expenses.filter { it.expense.id in expenseIds && it.outstandingMinor > 0 }
        .sortedWith(compareBy<PersonExpenseBalance> { it.expense.effectiveTimestamp }.thenBy { it.expense.id })
    require(BigInteger.valueOf(amountMinor) <= expenses.fold(BigInteger.ZERO) { total, entry -> total + BigInteger.valueOf(entry.outstandingMinor) }) {
        "Repayment exceeds the selected unpaid balance."
    }
    var remaining = amountMinor
    return expenses.mapNotNull { entry ->
        val used = minOf(remaining, entry.outstandingMinor)
        remaining -= used
        if (used == 0L) null else RepaymentPortion(entry.expense.id, entry.memberId, used, entry.outstandingMinor, entry.expense.updatedAt)
    }
}

/** Revalidates the preview under the ledger lock, then saves every allocation in one DB transaction. */
suspend fun TransactionRepository.recordPersonRepayment(personId: String, portions: List<RepaymentPortion>,
    receivedAt: Long, incomingId: String? = null, operationId: String = UUID.randomUUID().toString()): List<RepaymentEntity> = withLedger { db ->
    require(portions.isNotEmpty() && portions.map { it.expenseId }.distinct().size == portions.size) { "Choose distinct expenses." }
    require(runCatching { UUID.fromString(operationId).toString() == operationId }.getOrDefault(false)) { "Invalid settlement identity." }
    val dao = db.transactions()
    val operationKey = "settlement:$operationId"
    val request = JSONArray().put(personId).put(receivedAt).put(incomingId.orEmpty())
        .put(JSONArray(portions.map { JSONArray(listOf(it.expenseId, it.memberId.orEmpty(), it.amountMinor)) })).toString()
    dao.metadata(operationKey)?.let { saved ->
        val record = JSONObject(saved)
        require(record.getString("request") == request) { "This settlement was already recorded. Check repayment history before starting another." }
        val ids = record.getJSONArray("ids")
        val current = dao.repayments().associateBy { it.id }
        val entries = (0 until ids.length()).map { requireNotNull(current[ids.getString(it)]) { "Settlement was already changed or undone. Check repayment history." } }
        require(entries.size == portions.size && entries.zip(portions).all { (entry, part) ->
            entry.expenseId == part.expenseId && entry.memberId == part.memberId && entry.amountMinor == part.amountMinor && entry.receivedAt == receivedAt
        }) { "Settlement was already changed. Check repayment history." }
        return@withLedger entries
    }
    val reversed = dao.reversedOriginals().toSet()
    val summary = RepaymentAccounting.summary(dao.repaymentCandidates(), dao.repayments(), 0, Long.MAX_VALUE, reversed)
        .copy(shares = dao.groupShares(), members = dao.members())
    val person = requireNotNull(RepaymentAccounting.people(summary).firstOrNull { it.id == personId }) { "Balance changed. Close and reopen the person." }
    val total = portions.fold(0L) { sum, part -> require(part.amountMinor > 0); Math.addExact(sum, part.amountMinor) }
    ManualInput(java.math.BigDecimal.valueOf(total, 2).toPlainString(), Direction.Credit, receivedAt, TransactionType.Other).validate()
    val credit = incomingId?.let { requireNotNull(dao.get(it)) { "Incoming payment no longer exists." } }
    if (credit != null) require(credit.id !in reversed && credit.effectiveTimestamp == receivedAt) { "Receipt changed. Choose it again." }
    var allocated = credit?.let { dao.allocationsFrom(it.id).fold(0L) { sum, entry -> Math.addExact(sum, entry.amountMinor) } } ?: 0L
    val expenses = portions.map { part ->
        val entry = requireNotNull(person.expenses.singleOrNull { it.expense.id == part.expenseId && it.memberId == part.memberId }) { "This expense does not belong to this person's balance." }
        require(entry.outstandingMinor == part.expectedOutstanding && entry.expense.updatedAt == part.expectedUpdatedAt) { "Balance changed since preview. Close and reopen to review it again." }
        RepaymentAccounting.validateAllocation(entry.expense, credit, part.amountMinor, allocated)
        GroupSplitAccounting.validateRepayment(dao.sharesFor(part.expenseId), dao.repaymentsFor(part.expenseId), part.memberId, part.amountMinor)
        allocated = Math.addExact(allocated, part.amountMinor)
        entry.expense
    }
    val now = System.currentTimeMillis()
    val receiptId = credit?.id ?: UUID.randomUUID().toString()
    val result = portions.map { part -> RepaymentEntity(UUID.randomUUID().toString(), part.expenseId,
        receiptId, part.amountMinor, receivedAt, person.name, if (credit == null) "Cash" else "Linked", now, part.memberId) }
    db.runInTransaction {
        if (credit == null) dao.insert(TransactionEntity(receiptId, sourceType = "Manual", sourceTimestamp = receivedAt,
            effectiveTimestamp = receivedAt, amountMinor = total, direction = "Credit", status = "Successful",
            channel = "CashManual", transactionType = "Other", counterpartyLabel = person.name,
            reviewState = "Confirmed", createdAt = now, updatedAt = now))
        result.forEach(dao::addRepayment)
        expenses.forEach { updateRepaymentAggregate(dao, it, now) }
        dao.metadata(LedgerMetadataEntity(operationKey, JSONObject().put("request", request)
            .put("ids", JSONArray(result.map { it.id })).toString()))
    }
    revision.value++
    result
}

/** Undo is all-or-nothing and refuses edited/removed entries. Receipts are deliberately retained. */
suspend fun TransactionRepository.undoPersonRepayment(entries: List<RepaymentEntity>) = withLedger { db ->
    require(entries.isNotEmpty() && entries.map { it.id }.distinct().size == entries.size) { "No settlement to undo." }
    val dao = db.transactions()
    val current = dao.repayments().associateBy { it.id }
    require(entries.all { current[it.id] == it }) { "Repayment history changed. Review it before unlinking." }
    val expenses = entries.map { it.expenseId }.distinct().map { requireNotNull(dao.get(it)) }
    db.runInTransaction {
        entries.forEach { dao.deleteRepayment(it.id) }
        expenses.forEach { updateRepaymentAggregate(dao, it, System.currentTimeMillis()) }
    }
    revision.value++
}
