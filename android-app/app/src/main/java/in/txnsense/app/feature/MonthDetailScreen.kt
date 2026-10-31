package `in`.txnsense.app.feature

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import java.time.YearMonth
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalLocale
import kotlin.math.roundToInt
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.ui.LocalBrand

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MonthDetailScreen(
    repository: TransactionRepository,
    month: YearMonth,
    onBack: () -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null,
) {
    val brand = LocalBrand.current
    val months = remember(month) {
        (0..5).map { month.minusMonths(it.toLong()) }.reversed()
    }
    val data = remember { mutableStateOf<List<MonthBar>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(months) {
        val list = months.map { ym ->
            val amount = runCatching { repository.monthSpendTotal(ym.atDay(1)) }.getOrNull() ?: 0L
            MonthBar(ym, amount)
        }
        data.value = list
    }

    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(brand.bgTop, brand.bgBottom)))) {
        // Top bar with back
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("←", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.clickable { onBack() }.padding(8.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text("Spend Comparison", style = MaterialTheme.typography.titleLarge)
        }
        // Bar chart
        MonthBarChart(
            data = data.value,
            modifier = Modifier.weight(1f).padding(16.dp),
            maxAmount = data.value.maxOfOrNull { it.amount } ?: 0L
        )
    }
}

@Composable
private fun MonthBarChart(
    data: List<MonthBar>,
    modifier: Modifier = Modifier,
    maxAmount: Long
) {
    val brand = LocalBrand.current
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / data.size
            data.forEachIndexed { i, bar ->
                val barHeight = if (maxAmount == 0L) 0f else (bar.amount.toFloat() / maxAmount) * size.height
                val left = i * barWidth
                val top = size.height - barHeight
                drawRoundRect(
                    color = brand.lime,
                    topLeft = Offset(left + barWidth * 0.05f, top),
                    size = Size(barWidth * 0.9f, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        // Month labels
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { bar ->
                Text(
                    bar.ym.month.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale),
                    fontSize = 12.sp
                )
            }
        }
    }
}

data class MonthBar(val ym: YearMonth, val amount: Long)
