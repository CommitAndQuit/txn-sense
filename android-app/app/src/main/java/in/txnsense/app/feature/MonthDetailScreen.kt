package `in`.txnsense.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.money
import `in`.txnsense.app.ui.Brand
import `in`.txnsense.app.ui.LocalBrand
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

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
    LaunchedEffect(months) {
        val list = months.map { ym ->
            val amount = runCatching { repository.monthSpendTotal(ym.atDay(1)) }.getOrNull() ?: 0L
            MonthBar(ym, amount)
        }
        data.value = list
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(brand.bgTop, brand.bgBottom)))) {
        BackHandler { onBack() }
        
        Text("Spend Comparison", style = MaterialTheme.typography.titleLarge, modifier = Modifier.statusBarsPadding().padding(16.dp))
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MonthBarChart(
                data = data.value,
                modifier = Modifier.heightIn(min = 250.dp, max = 350.dp).padding(horizontal = 16.dp),
                maxAmount = data.value.maxOfOrNull { it.amount } ?: 0L
            )
        }
    }
}

@Composable
private fun MonthBarChart(
    data: List<MonthBar>,
    modifier: Modifier = Modifier,
    maxAmount: Long
) {
    val brand = LocalBrand.current
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { bar ->
            val barHeightFraction = if (maxAmount == 0L) 0f else bar.amount.toFloat() / maxAmount
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                // The bar
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight(barHeightFraction.coerceAtLeast(0.01f))
                            .width(20.dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(brand.lime)
                    )
                }
                // The number
                Text(
                    text = money(bar.amount),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = brand.onAccentDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // The label
                Text(
                    bar.ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 10.sp
                )
            }
        }
    }
}

data class MonthBar(val ym: YearMonth, val amount: Long)
