@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package `in`.txnsense.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.money
import `in`.txnsense.app.ui.LocalBrand
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Composable
fun MonthDetailScreen(
    repository: TransactionRepository,
    month: YearMonth,
    onBack: () -> Unit,
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null,
) {
    val brand = LocalBrand.current
    val scheme = MaterialTheme.colorScheme
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

    val selectedAmount = data.value.find { it.ym == month }?.amount ?: 0L
    val avgAmount = if (data.value.isNotEmpty()) data.value.sumOf { it.amount } / data.value.size else 0L
    val locale = LocalConfiguration.current.locales[0]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(brand.bgTop, brand.bgBottom)))
    ) {
        BackHandler { onBack() }

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "←",
                    fontSize = 26.sp,
                    color = scheme.onSurface,
                    modifier = Modifier
                        .clickableMenu(onBack)
                        .padding(8.dp)
                        .semantics { contentDescription = "Back" }
                )
                Column {
                    Text(
                        "Spend Trends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        "6-month comparison",
                        fontSize = 12.sp,
                        color = brand.textSecondary
                    )
                }
            }

            // Summary hero card on top gradient background
            val glassShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(glassShape)
                    .background(Brush.verticalGradient(listOf(brand.glassTop, brand.glassBottom)))
                    .border(1.dp, brand.glassBorder, glassShape)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}",
                    fontSize = 13.sp,
                    color = brand.textSecondary
                )
                Text(
                    money(selectedAmount),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
                Text(
                    "6-month average: ${money(avgAmount)}",
                    fontSize = 12.sp,
                    color = brand.textSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            // White card layout sliding up from the bottom (consistent with wider theme)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(scheme.surface)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                    item(key = "chart_container") {
                        Text(
                            "Monthly Spend Trend",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(14.dp))

                        // Chart canvas box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(brand.hairline.copy(alpha = 0.3f))
                                .padding(vertical = 14.dp, horizontal = 10.dp)
                        ) {
                            MonthBarChart(
                                data = data.value,
                                selectedMonth = month,
                                maxAmount = data.value.maxOfOrNull { it.amount } ?: 0L
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Monthly Breakdown",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Month breakdown list, ordered from newest to oldest
                    itemsIndexed(data.value.reversed(), key = { _, bar -> bar.ym.toString() }) { index, bar ->
                        val isSelected = bar.ym == month
                        val prevMonthBar = data.value.getOrNull(data.value.indexOf(bar) - 1)
                        val diff = if (prevMonthBar != null) bar.amount - prevMonthBar.amount else null
                        val pct = if (prevMonthBar != null && prevMonthBar.amount > 0) {
                            ((diff ?: 0L) * 100f / prevMonthBar.amount).roundToInt()
                        } else null

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "${bar.ym.month.getDisplayName(TextStyle.FULL, locale)} ${bar.ym.year}",
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = scheme.onSurface
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(brand.navy)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "Current",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = brand.onAccentLight
                                            )
                                        }
                                    }
                                }

                                Text(
                                    money(bar.amount),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface
                                )
                            }

                            if (pct != null && diff != null) {
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    val isIncrease = diff > 0
                                    val trendColor = if (isIncrease) scheme.error else Color(0xFF2E7D32)
                                    val prefix = if (isIncrease) "+" else ""
                                    Text(
                                        "$prefix$pct% vs prior month",
                                        fontSize = 11.sp,
                                        color = trendColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (index < data.value.size - 1) {
                                HorizontalDivider(
                                    color = brand.hairline,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthBarChart(
    data: List<MonthBar>,
    selectedMonth: YearMonth,
    modifier: Modifier = Modifier,
    maxAmount: Long
) {
    val brand = LocalBrand.current
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { bar ->
            val barHeightFraction = if (maxAmount == 0L) 0f else bar.amount.toFloat() / maxAmount
            val isSelected = bar.ym == selectedMonth
            val barColor = if (isSelected) brand.navy else brand.teal

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                // The amount on top
                Text(
                    text = money(bar.amount),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                // The bar
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight(barHeightFraction.coerceAtLeast(0.02f))
                            .width(22.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barColor)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // The month label
                Text(
                    bar.ym.month.getDisplayName(TextStyle.SHORT, locale),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) scheme.onSurface else brand.textSecondary
                )
            }
        }
    }
}

data class MonthBar(val ym: YearMonth, val amount: Long)
