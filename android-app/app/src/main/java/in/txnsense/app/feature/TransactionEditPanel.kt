package `in`.txnsense.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.txnsense.app.core.model.*
import `in`.txnsense.app.data.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Inline editable panel for a transaction.
 * - Collapsed by default with quick reclassify chips (Investment, Transfer to self) and an Edit button.
 * - Expanded state shows all editable fields.
 * - Compact mode for NeedsReview transactions (only category, ownership, spendType).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditPanel(
    repository: TransactionRepository,
    transaction: TransactionEntity,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val isReview = transaction.reviewState == "NeedsReview"
    var expanded by remember { mutableStateOf(false) }
    var compact by remember(transaction.id, isReview) { mutableStateOf(isReview) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Draft state
    val formatter = remember { DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT) }
    var amount by remember { mutableStateOf(transaction.amountMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() } ?: "") }
    var direction by remember { mutableStateOf(transaction.direction ?: "Debit") }
    var status by remember { mutableStateOf(transaction.status ?: "Successful") }
    var type by remember { mutableStateOf(transaction.transactionType ?: "Other") }
    var channel by remember { mutableStateOf(transaction.channel ?: "CashManual") }
    val initialDate = remember {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(transaction.effectiveTimestamp), ZoneId.systemDefault()).format(formatter)
    }
    var date by remember { mutableStateOf(initialDate) }
    var label by remember { mutableStateOf(transaction.counterpartyLabel ?: "") }
    var notes by remember { mutableStateOf(transaction.userNotes ?: "") }
    var hint by remember { mutableStateOf(transaction.maskedAccountHint?.takeLast(4) ?: "") }
    var category by remember { mutableStateOf(transaction.category ?: "Other") }
    var ownership by remember { mutableStateOf(transaction.ownership ?: "Personal") }
    var spendType by remember {
        mutableStateOf(transaction.spendType ?: if (transaction.transactionType == "SelfTransfer" || transaction.ownership == "SelfTransfer") "TransferToSelf" else "Expense")
    }
    var groupLabel by remember { mutableStateOf(transaction.groupLabel ?: "") }
    var personalShare by remember { mutableStateOf(transaction.personalShareMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() } ?: "") }
    var repaid by remember { mutableStateOf(transaction.repaidMinor?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it, 2).toPlainString() } ?: "") }
    var sourceId by remember { mutableStateOf(transaction.paymentSourceId) }
    var sources by remember { mutableStateOf<List<PaymentSourceEntity>>(emptyList()) }
    var optional by remember { mutableStateOf(false) }

    // Track dirty state
    val initialFields = remember(transaction.id) {
        listOf(amount, direction, status, type, channel, date, label, notes, hint, category, ownership, spendType, groupLabel, personalShare, repaid, sourceId)
    }
    val dirty = initialFields != listOf(amount, direction, status, type, channel, date, label, notes, hint, category, ownership, spendType, groupLabel, personalShare, repaid, sourceId)

    fun chooseSpendType(option: String) {
        spendType = option
        when (option) {
            "TransferToSelf" -> { ownership = "SelfTransfer"; type = "SelfTransfer" }
            "Investment", "TransferToFamily" -> {
                if (ownership == "SelfTransfer") ownership = "Personal"
                if (type == "SelfTransfer") type = "Other"
                groupLabel = ""; personalShare = ""; repaid = ""
            }
            else -> {
                if (ownership == "SelfTransfer") ownership = "Personal"
                if (type == "SelfTransfer") type = "Other"
            }
        }
    }

    LaunchedEffect(transaction.id) { sources = repository.paymentSources() }

    val pickerTheme = if (androidx.compose.foundation.isSystemInDarkTheme()) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Collapsed state: quick reclassify chips + Edit button
        if (!expanded) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Quick reclassify chips row
                    if (direction != "Credit") {
                        Text("Quick reclassify:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Investment chip
                            FilterChip(
                                selected = spendType == "Investment",
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                val newOwnership = if (spendType == "Investment") "Personal" else ownership
                                                val newType = if (spendType == "Investment") "Other" else type
                                                repository.classify(
                                                    transaction.id, category, SpendingOwnership.valueOf(newOwnership),
                                                    groupLabel, personalShare, repaid, SpendingType.valueOf("Investment")
                                                )
                                                onSave()
                                            } catch (e: IllegalArgumentException) { error = e.message ?: "Check the fields." }
                                            catch (_: Exception) { error = "Could not save. Please retry." }
                                            finally { busy = false }
                                        }
                                    }
                                },
                                label = { Text("Investment") }
                            )
                            // Transfer to self chip
                            FilterChip(
                                selected = spendType == "TransferToSelf",
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                repository.classify(
                                                    transaction.id, category, SpendingOwnership.valueOf("SelfTransfer"),
                                                    groupLabel, personalShare, repaid, SpendingType.valueOf("TransferToSelf")
                                                )
                                                onSave()
                                            } catch (e: IllegalArgumentException) { error = e.message ?: "Check the fields." }
                                            catch (_: Exception) { error = "Could not save. Please retry." }
                                            finally { busy = false }
                                        }
                                    }
                                },
                                label = { Text("Transfer to self") }
                            )
                            // Transfer to family chip
                            FilterChip(
                                selected = spendType == "TransferToFamily",
                                onClick = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                repository.classify(
                                                    transaction.id, category, SpendingOwnership.valueOf("Personal"),
                                                    groupLabel, personalShare, repaid, SpendingType.valueOf("TransferToFamily")
                                                )
                                                onSave()
                                            } catch (e: IllegalArgumentException) { error = e.message ?: "Check the fields." }
                                            catch (_: Exception) { error = "Could not save. Please retry." }
                                            finally { busy = false }
                                        }
                                    }
                                },
                                label = { Text("Transfer to family") }
                            )
                        }
                    }
                    // Edit button to expand
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { expanded = true }) { Text("Edit all details") }
                    }
                }
            }
        }

        // Expanded state: full edit form
        if (expanded) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (compact) "Categorize transaction" else "Edit transaction", style = MaterialTheme.typography.titleLarge)

                    if (compact && isReview) {
                        Text("${`in`.txnsense.app.money(transaction.amountMinor)} · ${transaction.counterpartyLabel ?: friendly(transaction.direction)}", style = MaterialTheme.typography.titleMedium)
                        Text("Still needs review. Saving labels will not confirm the payment.", style = MaterialTheme.typography.bodySmall)
                    }

                    if (!compact) {
                        Text("${friendly(transaction.sourceType ?: "Manual")} · Stored only on this device", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it },
                            label = { Text("Amount (INR)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (amount.isBlank()) Text("Enter the transaction amount.", color = MaterialTheme.colorScheme.error)

                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Debit", "Credit", "Transfer").forEach { option ->
                                FilterChip(
                                    selected = direction == option,
                                    onClick = {
                                        direction = option
                                        if (option == "Credit" && spendType !in listOf("Expense", "TransferToSelf")) spendType = "Expense"
                                    },
                                    label = { Text(friendly(option)) }
                                )
                            }
                        }
                        if (direction == "Unknown") Text("Choose money out, money in, or transfer.", color = MaterialTheme.colorScheme.error)

                        OutlinedTextField(
                            value = label, onValueChange = { label = it.take(60) },
                            label = { Text("Label (optional)") },
                            supportingText = { Text("A short description, not personal or account details.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Choice("Category", category, listOf("Other", "Food", "Travel", "Shopping", "Bills", "Health", "Education", "Entertainment", "Cash")) { category = it }

                    if (direction != "Credit") {
                        Text("How should this count?", style = MaterialTheme.typography.labelLarge)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Expense", "Investment", "TransferToSelf", "TransferToFamily").forEach { option ->
                                FilterChip(
                                    selected = spendType == option,
                                    onClick = { chooseSpendType(option) },
                                    label = { Text(friendly(option)) }
                                )
                            }
                        }
                        if (spendType != "Expense") Text("This stays in your ledger but is not counted as spending.", style = MaterialTheme.typography.bodySmall)
                    }

                    if (spendType == "Expense") {
                        Text("Who was this payment for?", style = MaterialTheme.typography.labelLarge)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Personal", "ForOther", "Group").forEach { option ->
                                FilterChip(
                                    selected = ownership == option,
                                    onClick = {
                                        ownership = option
                                        if (option !in listOf("ForOther", "Group")) repaid = ""
                                        if (option != "Group") { groupLabel = ""; personalShare = "" }
                                    },
                                    label = { Text(friendly(option)) }
                                )
                            }
                        }
                        if (ownership == "Group") {
                            OutlinedTextField(groupLabel, { groupLabel = it.take(40) }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(personalShare, { personalShare = it }, label = { Text("Your share (INR)") }, supportingText = { Text("The rest is tracked as money others owe you.") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        }
                        if (ownership == "ForOther" || ownership == "Group") {
                            OutlinedTextField(repaid, { repaid = it }, label = { Text("Already repaid (INR, optional)") }, supportingText = { Text("Update this when people repay you.") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                        }
                    }

                    if (compact) {
                        TextButton(onClick = { compact = false }) { Text("Edit all details") }
                    }

                    if (!compact) {
                        val selectedDate = LocalDateTime.parse(date, formatter)
                        TextButton(onClick = {
                            android.app.DatePickerDialog(context, pickerTheme, { _, year, month, day ->
                                date = LocalDateTime.parse(date, formatter).withYear(year).withMonth(1).withDayOfMonth(1).withMonth(month + 1).withDayOfMonth(day).format(formatter)
                            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
                        }) { Text("Date: ${selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))}") }
                        TextButton(onClick = {
                            android.app.TimePickerDialog(context, pickerTheme, { _, hour, minute -> date = LocalDateTime.parse(date, formatter).withHour(hour).withMinute(minute).format(formatter) }, selectedDate.hour, selectedDate.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                        }) { Text("Time: ${selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}") }

                        Choice("Payment method", channel, Channel.entries.filter { it !in listOf(Channel.Unknown, Channel.Other) }.map { it.name }) { chosen ->
                            channel = chosen
                            if (sources.none { it.id == sourceId && it.channel == chosen }) sourceId = null
                        }
                        if (sources.any { it.active && it.channel == channel } || sourceId != null) SourceChoice(sourceId, channel, sources) { sourceId = it }

                        if (status == "Unknown") Choice("Payment status — please check", status, TransactionStatus.entries.map { it.name }) { status = it }
                        if (type == "Unknown") Choice("Transaction type — please choose", type, TransactionType.entries.map { it.name }) {
                            type = it
                            if (it == "SelfTransfer") chooseSpendType("TransferToSelf")
                            else if (spendType == "TransferToSelf") { spendType = "Expense"; ownership = "Personal" }
                        }

                        TextButton(onClick = { optional = !optional }) { Text(if (optional) "Fewer details" else "More details") }
                        if (optional) {
                            if (type != "Unknown") Choice("Transaction type", type, TransactionType.entries.map { it.name }) {
                                type = it
                                if (it == "SelfTransfer") chooseSpendType("TransferToSelf")
                                else if (spendType == "TransferToSelf") { spendType = "Expense"; ownership = "Personal" }
                            }
                            if (status != "Unknown") Choice("Payment status", status, TransactionStatus.entries.map { it.name }) { status = it }
                            OutlinedTextField(hint, { hint = it }, label = { Text("Account last 4 digits (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                            Text("Use short labels and notes. Do not paste SMS, OTPs, personal contact names, UPI IDs or full account numbers. Text stays encrypted on this device.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!busy) {
                                    if (compact && isReview) {
                                        busy = true
                                        scope.launch {
                                            try {
                                                repository.classify(transaction.id, category, SpendingOwnership.valueOf(ownership),
                                                    groupLabel, personalShare, repaid, SpendingType.valueOf(spendType))
                                                onSave()
                                            } catch (e: IllegalArgumentException) { error = e.message ?: "Check the fields." }
                                            catch (_: Exception) { error = "Could not save. Please retry." }
                                            finally { busy = false }
                                        }
                                    } else {
                                        val input = try {
                                            val editedTimestamp = LocalDateTime.parse(date, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            val timestamp = if (date == initialDate) transaction.effectiveTimestamp else editedTimestamp
                                            ManualInput(amount, Direction.valueOf(direction), timestamp,
                                                TransactionType.valueOf(type), TransactionStatus.valueOf(status), Channel.valueOf(channel), label, notes, hint,
                                                category, SpendingOwnership.valueOf(ownership), groupLabel, personalShare, repaid, sourceId,
                                                SpendingType.valueOf(spendType)).also { it.validate() }
                                        } catch (e: IllegalArgumentException) { error = e.message ?: "Check the fields."; null }
                                        catch (_: java.time.DateTimeException) { error = "Enter a valid date and time as yyyy-MM-dd HH:mm."; null }
                                        if (input != null) {
                                            busy = true
                                            scope.launch {
                                                try { repository.save(input, transaction.id, java.util.UUID.randomUUID().toString()); onSave() }
                                                catch (_: Exception) { error = "Could not save. Please retry." }
                                                finally { busy = false }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (busy) "Saving…" else "Save") }
                        OutlinedButton(onClick = { expanded = false; onCancel() }, enabled = !busy) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

// Reuse helpers from TransactionForm
@Composable
private fun Choice(label: String, value: String, options: List<String>, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${friendly(value)}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(friendly(option)) }, onClick = { onChoose(option); expanded = false }) }
        }
    }
}

@Composable
private fun SourceChoice(value: String?, channel: String, sources: List<PaymentSourceEntity>, onChoose: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSource = sources.firstOrNull { it.id == value }
    val selected = selectedSource?.let { "${it.nickname}${if (it.active) "" else " (inactive)"}" } ?: "Not set"
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Payment source: $selected") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Not set") }, onClick = { onChoose(null); expanded = false })
            sources.filter { it.active && it.channel == channel }.forEach { source -> DropdownMenuItem(text = { Text(source.nickname) }, onClick = { onChoose(source.id); expanded = false }) }
        }
    }
}