package `in`.financeministry.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.financeministry.app.data.*
import `in`.financeministry.app.money
import kotlin.math.roundToInt
import java.math.BigInteger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun spendingPeriodLabel(month: LocalDate, today: LocalDate = LocalDate.now()): String =
    if (month.withDayOfMonth(1) == today.withDayOfMonth(1)) "This month · your spending"
    else "${month.format(DateTimeFormatter.ofPattern("MMM uuuu"))} · your spending"

@Composable
fun LedgerMonthButton(label:String,onClick:()->Unit) {
    TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) {
        Text(label,color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.labelMedium.copy(fontSize=13.sp))
        Spacer(Modifier.width(7.dp));LedgerIcon("down",Modifier.size(16.dp),MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerHomeSummary(snapshot:LedgerSnapshot,owedCountLabel:String,onMonth:()->Unit,onBreakdown:()->Unit,onPeople:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        LedgerMonthButton(snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM uuuu")),onMonth)
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            LedgerIcon("lock",Modifier.size(13.dp),colors.onSurfaceVariant)
            Text("Only on your phone",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
        }
    }
    val slices = remember(snapshot.spendingCategories) { spendingChartSlices(snapshot.spendingCategories) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("Your spending",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
        Text(money(snapshot.personalSpend),style=MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings="tnum"))
        Text(spendingPeriodLabel(snapshot.selectedMonth),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
    }
    if (slices.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top=8.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        slices.take(3).forEach { slice ->
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp),
                modifier=Modifier.semantics(mergeDescendants=true) {}) {
                Box(Modifier.size(7.dp).background(spendingCategoryColor(slice.label), CircleShape))
                Text("${slice.label} ${(slice.fraction * 100).roundToInt()}%",style=MaterialTheme.typography.bodySmall,
                    color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
    TextButton(onClick=onBreakdown,modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription="How totals work"},contentPadding=PaddingValues(0.dp)) {
        Text("Spending breakdown",style=MaterialTheme.typography.labelMedium);Spacer(Modifier.width(4.dp));LedgerIcon("arrow",Modifier.size(15.dp))
    }
    Row(Modifier.fillMaxWidth().background(colors.surfaceContainer,RoundedCornerShape(17.dp)).padding(horizontal=16.dp,vertical=15.dp)) {
        FlowMetric("Money out","out",snapshot.debit,Modifier.weight(1f))
        VerticalDivider(Modifier.height(45.dp),color=colors.outlineVariant)
        FlowMetric("Money in","incoming",snapshot.credit,Modifier.weight(1f).padding(start=20.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top=4.dp).clickable(onClick=onPeople).semantics {contentDescription="Open repayments"}
        .padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).background(colors.secondaryContainer,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
            LedgerIcon("people",tint=colors.primary)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Still owed to you",style=MaterialTheme.typography.labelLarge.copy(fontSize=13.sp))
            Text("${compactMoney(snapshot.selectedMonthOutstandingRepayments)} unpaid from ${snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMM"))} · $owedCountLabel",
                style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
        }
        Text(compactMoney(snapshot.outstandingRepayments),style=MaterialTheme.typography.titleMedium.copy(fontSize=19.sp,fontFeatureSettings="tnum"))
        LedgerIcon("chevron",Modifier.size(16.dp),colors.onSurfaceVariant)
    }
    HorizontalDivider(color=colors.outlineVariant)
}

@Composable private fun FlowMetric(label:String,icon:String,amount:BigInteger,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            LedgerIcon(icon,Modifier.size(13.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(compactMoney(amount),style=MaterialTheme.typography.titleMedium.copy(fontSize=19.sp,letterSpacing=(-.4).sp,fontFeatureSettings="tnum"))
    }
}

@Composable fun LedgerReviewCue(text:String,onClick:()->Unit,icon:String="review") {
    Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=5.dp).background(MaterialTheme.colorScheme.tertiaryContainer,RoundedCornerShape(10.dp))
        .clickable(onClick=onClick).heightIn(min=48.dp).padding(horizontal=12.dp,vertical=11.dp),
        horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        LedgerIcon(icon,Modifier.size(17.dp),MaterialTheme.colorScheme.onTertiaryContainer)
        Text(text,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onTertiaryContainer)
        LedgerIcon("chevron",Modifier.size(16.dp),MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MonthlyMathSheet(repository:TransactionRepository,snapshot:LedgerSnapshot,onDismiss:()->Unit,onCategories:()->Unit) {
    val revision by repository.revision.collectAsState()
    var showCalculation by remember { mutableStateOf(false) }
    var refunds by remember { mutableStateOf<BigInteger?>(null) }
    var refundLoadFailed by remember { mutableStateOf(false) }
    var refundRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(snapshot.selectedMonth,revision,refundRetry) {
        refunds = null; refundLoadFailed = false
        try {
            refunds=repository.spendingInsights(snapshot.selectedMonth, snapshot.selectedMonth.plusMonths(1).minusDays(1)).refundsReceived
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { refundLoadFailed = true }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,dragHandle=null,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=17.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Your month, explained",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=onDismiss,modifier=Modifier.semantics {contentDescription="Close breakdown"}) {LedgerIcon("close")}
            }
            Column(Modifier.weight(1f,false).verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM uuuu")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background,RoundedCornerShape(18.dp)).padding(18.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Your spending",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    LedgerAmount(snapshot.personalSpend)
                    if (snapshot.debit.signum() > 0) {
                        val personalFraction = java.math.BigDecimal(snapshot.personalSpend).divide(java.math.BigDecimal(snapshot.debit),
                            6,java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f,1f)
                        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=6.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .semantics {contentDescription="Money out split: your spending ${compactMoney(snapshot.personalSpend)}, others’ share ${compactMoney(snapshot.paidForOthers)}"}) {
                            if (personalFraction > 0f) Box(Modifier.weight(personalFraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                            if (personalFraction < 1f) Box(Modifier.weight(1f-personalFraction).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                        }
                    }
                    MonthlyCalculationRow("Money out",compactMoney(snapshot.debit))
                    MonthlyCalculationRow("Others’ share","− "+compactMoney(snapshot.paidForOthers))
                }
                TextButton(onClick=onCategories,contentPadding=PaddingValues(0.dp)) {Text("See spending by category");Spacer(Modifier.width(4.dp));LedgerIcon("arrow",Modifier.size(15.dp))}
                if (LocalDensity.current.fontScale > 1.3f) {
                    MonthlySupplement("Still owed · all dates",compactMoney(snapshot.outstandingRepayments),"people")
                    MonthlySupplement("Refunds received",refunds?.let(::compactMoney) ?: if (refundLoadFailed) "Unavailable" else "Loading…","incoming")
                } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MonthlySupplement("Still owed · all dates",compactMoney(snapshot.outstandingRepayments),"people",Modifier.weight(1f))
                    MonthlySupplement("Refunds received",refunds?.let(::compactMoney) ?: if (refundLoadFailed) "Unavailable" else "Loading…","incoming",Modifier.weight(1f))
                }
                if (refundLoadFailed) {
                    Text("Could not load refunds. Your other totals are unchanged.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refundRetry++ }) { Text("Retry refunds") }
                }
                TextButton(onClick={showCalculation=!showCalculation},contentPadding=PaddingValues(0.dp),
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {stateDescription=if(showCalculation) "Expanded" else "Collapsed"}) {
                    LedgerIcon("info",Modifier.size(17.dp));Spacer(Modifier.width(8.dp))
                    Text("How it’s calculated",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                    LedgerIcon(if(showCalculation) "close" else "down",Modifier.size(16.dp))
                }
                if (showCalculation) Column(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Your spending = money out − others’ shares.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Repayments reduce what you’re owed. Refunds stay in money in; neither changes your share of an expense.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Self transfers, card bill payments and payments awaiting review are excluded.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("SMS dates use message timestamps unless edited. These are recorded totals, not your bank balance.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Button(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).padding(bottom=18.dp)) {Text("Got it")}
        }
    }
}
@Composable private fun MonthlyCalculationRow(label:String,amount:String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {}.padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount,style=MaterialTheme.typography.labelLarge.copy(fontFeatureSettings="tnum"))
    }
}
@Composable private fun MonthlySupplement(label:String,amount:String,icon:String,modifier:Modifier=Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background,RoundedCornerShape(14.dp))
        .semantics(mergeDescendants=true) {}.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        LedgerIcon(icon,Modifier.size(18.dp),MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount,style=MaterialTheme.typography.titleMedium.copy(fontFeatureSettings="tnum"))
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MatchingTotalsSheet(snapshot:LedgerSnapshot,onDismiss:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss,dragHandle=null,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top=17.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Matching totals",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick=onDismiss,modifier=Modifier.semantics {contentDescription="Close matching totals"}) {
                    LedgerIcon("close")
                }
            }
            Column(Modifier.weight(1f,false).verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("${snapshot.resultCount} matching transaction${if(snapshot.resultCount==1L)"" else "s"} · " +
                    snapshot.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM uuuu")),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                MathRow("Money out",compactMoney(snapshot.resultDebit))
                MathRow("Money in",compactMoney(snapshot.resultCredit))
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                MathRow("Your spending",compactMoney(snapshot.resultPersonalSpend))
                Text("The same accounting rules as Home, applied to your current filters. Payments needing review remain excluded. These totals are not your bank balance.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun MathRow(label:String,amount:String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {}.padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Text(amount,style=MaterialTheme.typography.titleSmall)
    }
}
