package `in`.financeministry.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.financeministry.app.data.CategorySpending
import `in`.financeministry.app.money
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class SpendingSlice(val label: String, val amount: BigInteger, val categories: List<String>, val fraction: Float)

/** Presentation only: retain exact minor units; collapse the tail, never accounting records. */
fun spendingChartSlices(categories: List<CategorySpending>): List<SpendingSlice> {
    val positive = categories.filter { it.amount.signum() > 0 }
        .sortedWith(compareByDescending<CategorySpending> { it.amount }.thenBy { it.category })
    val total = positive.fold(BigInteger.ZERO) { sum, item -> sum + item.amount }
    if (total.signum() == 0) return emptyList()
    val groups = positive.take(5).map { listOf(it) } + if (positive.size > 5) listOf(positive.drop(5)) else emptyList()
    return groups.map { group ->
        val amount = group.fold(BigInteger.ZERO) { sum, item -> sum + item.amount }
        SpendingSlice(if (group.size == 1) group.single().category else "Other categories", amount,
            group.map { it.category }, BigDecimal(amount).divide(BigDecimal(total), 12, RoundingMode.HALF_UP).toFloat())
    }
}

/** Stable across sort order and months. Text and selection state also convey meaning. */
fun spendingCategoryColor(category: String): Color {
    val palette = listOf(Color(0xFF627CE5), Color(0xFFCF8748), Color(0xFFAA79C9),
        Color(0xFF4B9BB4), Color(0xFFCC7587), Color(0xFF858C9E))
    val index = when (category) {
        "Food" -> 0; "Grocery" -> 1; "Entertainment" -> 2; "Bills" -> 3
        "Transport" -> 4; "Other", "Other categories" -> 5
        else -> Math.floorMod(category.hashCode(), palette.size)
    }
    return palette[index]
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingDonut(categories: List<CategorySpending>, onOpen: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val slices = remember(categories) { spendingChartSlices(categories) }
    var selectedCategories by remember(categories) { mutableStateOf<List<String>?>(null) }
    val selectedSlice = slices.firstOrNull { it.categories == selectedCategories }
    val total = slices.fold(BigInteger.ZERO) { sum, item -> sum + item.amount }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val summaryBelow = LocalDensity.current.fontScale > 1.2f ||
        (selectedSlice?.label?.length ?: 0) > 18 || money(selectedSlice?.amount ?: total).length > 14
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (slices.isEmpty()) {
            Text("No spending to chart yet", modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = 204.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(204.dp).semantics { contentDescription = "Spending by category. Select a category using the labels below." }
                    .pointerInput(slices) {
                        detectTapGestures { point ->
                            val x = point.x - size.width / 2f
                            val y = point.y - size.height / 2f
                            val radius = size.width / 2f - 16.dp.toPx()
                            if (hypot(x, y) in (radius - 20.dp.toPx())..(radius + 20.dp.toPx())) {
                                val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360).toFloat()
                                var end = 0f
                                val hit = slices.firstOrNull { end += it.fraction * 360; angle < end } ?: slices.last()
                                selectedCategories = if (selectedCategories == hit.categories) null else hit.categories
                            }
                        }
                    }) {
                    val inset = 16.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(19.dp.toPx()))
                    var start = -90f
                    slices.forEach { slice ->
                        val sweep = slice.fraction * 360f
                        val gap = if (slices.size == 1) 0f else minOf(2.5f, sweep * .15f)
                        drawArc(spendingCategoryColor(slice.label).copy(alpha = if (selectedSlice == null || selectedSlice == slice) 1f else .28f),
                            start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize,
                            style = Stroke(if (selectedSlice == slice) 25.dp.toPx() else 19.dp.toPx()))
                        start += sweep
                    }
                }
                if (!summaryBelow) Column(Modifier.widthIn(max = 142.dp).padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(selectedSlice?.label ?: "Your spending", style = MaterialTheme.typography.labelMedium)
                    Text(money(selectedSlice?.amount ?: total), style = MaterialTheme.typography.titleLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(selectedSlice?.let { "${(it.fraction * 100).roundToInt()}% of spending" } ?: "Your share only",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (summaryBelow) Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selectedSlice?.label ?: "Your spending", style = MaterialTheme.typography.labelMedium)
                Text(money(selectedSlice?.amount ?: total), style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(selectedSlice?.let { "${(it.fraction * 100).roundToInt()}% of spending" } ?: "Your share only",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                slices.forEach { slice ->
                    val isSelected = selectedSlice == slice
                    TextButton(onClick = { selectedCategories = if (isSelected) null else slice.categories },
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Select ${slice.label}, ${(slice.fraction * 100).roundToInt()}% of spending"
                            selected = isSelected
                        }, colors = ButtonDefaults.textButtonColors(containerColor = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
                        Box(Modifier.size(8.dp).background(spendingCategoryColor(slice.label), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(slice.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            TextButton(onClick = { onOpen(selectedSlice?.categories ?: categories.filter { it.amount.signum() > 0 }.map { it.category }) }) {
                Text(selectedSlice?.let { "View ${it.label} expenses" } ?: "View expenses")
                Spacer(Modifier.width(6.dp)); LedgerIcon("arrow", Modifier.size(16.dp))
            }
        }
    }
}
