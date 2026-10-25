package `in`.financeministry.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.money
import `in`.financeministry.app.transactionTime
import `in`.financeministry.app.ui.LedgerTextInput
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSettlementSheet(repository: TransactionRepository, person: PersonRepaymentBalance,
    onDismiss: () -> Unit, onSaved: (List<RepaymentEntity>) -> Unit) {
    // Keep the reviewed balances stable. Save independently revalidates against current storage.
    val original = remember(person.id) { person }
    val operationId = rememberSaveable(person.id) { java.util.UUID.randomUUID().toString() }
    var selected by rememberSaveable(person.id) { mutableStateOf(ArrayList(original.expenses.filter { it.outstandingMinor > 0 }.map { it.expense.id })) }
    var amount by rememberSaveable(person.id) { mutableStateOf("") }
    var cash by rememberSaveable(person.id) { mutableStateOf(false) }
    var incomingId by rememberSaveable(person.id) { mutableStateOf<String?>(null) }
    var date by rememberSaveable(person.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var credits by remember { mutableStateOf<List<Pair<TransactionEntity, Long>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<List<RepaymentPortion>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val pickerTheme = `in`.financeministry.app.ui.ledgerPickerTheme()
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } - 60.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = {
        if (!busy) {
            if (choosing) choosing = false
            else if (amount.isNotBlank() || incomingId != null) discard = true
            else onDismiss()
        }
    }
    LaunchedEffect(person.id) {
        try { credits = repository.availableRepaymentCredits(); loaded = true }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load receipts. Close and retry." }
    }
    ModalBottomSheet(onDismissRequest = { close(); scope.launch { sheetState.show() } }, sheetState = sheetState,
        sheetGesturesEnabled = false, dragHandle = null, modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().heightIn(max = height.coerceAtLeast(48.dp)).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (choosing) "Choose receipt" else if (preview != null) "Review allocation" else "Settle with ${person.name}",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = close, enabled = !busy) { Text(if (choosing) "Back" else "Close") }
            }
            if (choosing) {
                LedgerTextInput(query, { query = it.take(60) }, label = "Search receipts", modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                LazyColumn(Modifier.weight(1f, false), contentPadding = PaddingValues(20.dp)) {
                    val matches = credits.filter { query.isBlank() || it.first.counterpartyLabel.orEmpty().contains(query.trim(), true) }
                    if (matches.isEmpty()) item { Text("No available receipts. Add a received payment in Activity or choose Received cash.") }
                    items(matches, key = { it.first.id }) { (row, available) ->
                        TextButton(onClick = {
                            incomingId = row.id; cash = false; date = row.effectiveTimestamp
                            val owed = original.expenses.filter { it.expense.id in selected }.fold(BigInteger.ZERO) { n, entry -> n + BigInteger.valueOf(entry.outstandingMinor) }
                            amount = BigDecimal(owed.min(BigInteger.valueOf(available)), 2).toPlainString()
                            choosing = false; focus.clearFocus(); error = null
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(row.counterpartyLabel ?: "Incoming payment")
                                Text("${money(available)} available · ${transactionTime(row.effectiveTimestamp)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${person.name} owes ${money(original.outstanding)} in this view", style = MaterialTheme.typography.titleMedium)
                    val plan = preview
                    if (plan == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(!cash, { cash = false; error = null }, label = { Text("Recorded payment") }, enabled = !busy)
                            FilterChip(cash, { cash = true; incomingId = null; date = System.currentTimeMillis(); error = null }, label = { Text("Received cash") }, enabled = !busy)
                        }
                        if (!cash) {
                            TextButton(onClick = { choosing = true; focus.clearFocus() }, enabled = loaded) { Text("Choose recorded receipt") }
                            credits.firstOrNull { it.first.id == incomingId }?.let { Text("${it.first.counterpartyLabel ?: "Incoming payment"} · ${money(it.second)} available") }
                        }
                        LedgerTextInput(amount, { amount = it; error = null }, label = "Amount received (INR)", amountStyle = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (cash) TextButton(onClick = {
                            val local = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
                            android.app.DatePickerDialog(context, pickerTheme, { _, y, m, d ->
                                date = local.withYear(y).withMonth(1).withDayOfMonth(1).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli()
                            }, local.year, local.monthValue - 1, local.dayOfMonth).show()
                        }) { Text("Received: ${transactionTime(date)}") }
                        Text("Include expenses · oldest unpaid first", style = MaterialTheme.typography.labelLarge)
                        original.expenses.filter { it.outstandingMinor > 0 }.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(entry.expense.id in selected, { checked ->
                                    selected = ArrayList(if (checked) selected + entry.expense.id else selected - entry.expense.id); error = null
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(entry.expense.counterpartyLabel ?: "Expense")
                                    Text("${money(entry.outstandingMinor)} unpaid · ${transactionTime(entry.expense.effectiveTimestamp)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        Text("${money(plan.sumOf { it.amountMinor })} across ${plan.size} expense${if (plan.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
                        plan.forEach { part ->
                            val expense = original.expenses.first { it.expense.id == part.expenseId }.expense
                            ListItem(headlineContent = { Text(expense.counterpartyLabel ?: "Expense") },
                                supportingContent = { Text("${money(part.expectedOutstanding - part.amountMinor)} remains unpaid") },
                                trailingContent = { Text(money(part.amountMinor)) })
                        }
                        Text("Still owed after saving: ${money(original.outstanding - BigInteger.valueOf(plan.sumOf { it.amountMinor }))}")
                        Text(if (cash) "Creates one cash receipt. Your spending stays unchanged." else "Uses the selected receipt. No additional money-in is created.", style = MaterialTheme.typography.bodySmall)
                        Text("Undo unlinks these allocations but keeps the receipt. Individual allocations remain in each expense's repayment history.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { preview = null }) { Text("Change allocation") }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(enabled = !busy && (cash || loaded && incomingId != null), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), onClick = {
                        focus.clearFocus()
                        val plan = preview
                        if (plan == null) {
                            try {
                                val minor = ManualInput(amount, Direction.Credit, date, TransactionType.Other).amountMinor()
                                if (!cash) require(minor <= requireNotNull(credits.firstOrNull { it.first.id == incomingId }).second) { "Amount exceeds the available receipt." }
                                preview = planPersonRepayment(original, minor, selected.toSet()); error = null
                            } catch (e: IllegalArgumentException) { error = e.message ?: "Check amount and expenses." }
                        } else {
                            busy = true
                            scope.launch {
                                try { onSaved(repository.recordPersonRepayment(original.id, plan, date, incomingId.takeUnless { cash }, operationId)); onDismiss() }
                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (e: IllegalArgumentException) { error = e.message ?: "Balance changed. Close and retry." }
                                catch (_: Exception) { error = "Could not save. Check repayment history before retrying." }
                                finally { busy = false }
                            }
                        }
                    }) { Text(if (busy) "Saving…" else if (preview == null) "Preview allocation" else "Confirm settlement") }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard settlement draft?") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}
