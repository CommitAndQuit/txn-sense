@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package `in`.txnsense.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import `in`.txnsense.app.data.CategorySubGroup
import `in`.txnsense.app.data.MajorGroup
import `in`.txnsense.app.data.MajorGroupSummary
import `in`.txnsense.app.data.MonthCategoryBreakdown
import `in`.txnsense.app.data.TransactionRepository
import `in`.txnsense.app.money
import `in`.txnsense.app.ui.LocalBrand
import java.time.YearMonth
import java.time.format.TextStyle

internal data class CategoryVisual(val label: String, val badge: String, val color: Color, val onColor: Color)

internal fun categoryVisualFor(name: String): CategoryVisual = when (name.trim()) {
    "Food" -> CategoryVisual("Food", "FD", Color(0xFFFF9800), Color.White)
    "Groceries" -> CategoryVisual("Groceries", "GR", Color(0xFF689F38), Color.White)
    "Shopping" -> CategoryVisual("Shopping", "SH", Color(0xFFE91E63), Color.White)
    "Bills" -> CategoryVisual("Bills", "BL", Color(0xFF0288D1), Color.White)
    "Travel" -> CategoryVisual("Travel", "TR", Color(0xFF00897B), Color.White)
    "Entertainment" -> CategoryVisual("Entertainment", "EN", Color(0xFF8E24AA), Color.White)
    "Health" -> CategoryVisual("Health", "HL", Color(0xFFD32F2F), Color.White)
    "Education" -> CategoryVisual("Education", "ED", Color(0xFF3949AB), Color.White)
    "Cash" -> CategoryVisual("Cash", "CS", Color(0xFF43A047), Color.White)
    "Random" -> CategoryVisual("Random", "RD", Color(0xFF7E57C2), Color.White)
    "Salary" -> CategoryVisual("Salary", "SL", Color(0xFF2E7D32), Color.White)
    "Investment" -> CategoryVisual("Investment", "IV", Color(0xFF00ACC1), Color.White)
    "Stocks" -> CategoryVisual("Stocks", "ST", Color(0xFF0288D1), Color.White)
    "Mutual Funds" -> CategoryVisual("Mutual Funds", "MF", Color(0xFF00897B), Color.White)
    "Crypto" -> CategoryVisual("Crypto", "CR", Color(0xFFFFB300), Color.Black)
    "General", "General Income" -> CategoryVisual(name.trim(), "GN", Color(0xFF546E7A), Color.White)
    else -> CategoryVisual(name.ifBlank { "Other" }, name.trim().take(2).uppercase().ifBlank { "OT" }, Color(0xFF546E7A), Color.White)
}

private enum class GroupFilterTab(val label: String) {
    Spends("Spends"),
    Investment("Investment"),
    Income("Income"),
    All("All Groups")
}

/**
 * Screen showing major group (Spends, Investment, Income) and sub-group category breakdown for a month.
 */
@Composable
fun MonthCategorySpendsScreen(
    repository: TransactionRepository,
    month: YearMonth,
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit = {},
    sharedScope: SharedTransitionScope? = null,
    animatedScope: AnimatedVisibilityScope? = null,
) {
    val brand = LocalBrand.current
    val scheme = MaterialTheme.colorScheme
    val revision by repository.revision.collectAsState()
    val eraseGen by repository.eraseGeneration.collectAsState()

    var breakdown by remember(month) { mutableStateOf<MonthCategoryBreakdown?>(null) }
    LaunchedEffect(month, revision, eraseGen) {
        breakdown = runCatching { repository.monthCategoryBreakdown(month.atDay(1)) }.getOrNull()
    }

    BackHandler(enabled = true) { onBack() }

    val monthLabel = "${month.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])} ${month.year}"
    val spends = breakdown?.spends ?: MajorGroupSummary(MajorGroup.Spends, 0L, 0)
    val investment = breakdown?.investment ?: MajorGroupSummary(MajorGroup.Investment, 0L, 0)
    val income = breakdown?.income ?: MajorGroupSummary(MajorGroup.Income, 0L, 0)

    var selectedTab by remember { mutableStateOf(GroupFilterTab.Spends) }
    var expandedItemKey by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(brand.bgTop, brand.bgBottom)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header bar with back navigation
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
                        "Category Breakdown",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        monthLabel,
                        fontSize = 13.sp,
                        color = brand.textSecondary
                    )
                }
            }

            // Major Group Summary Hero Cards (Spends, Investment, Income)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MajorGroupHeroCard(
                    title = "Spends",
                    amount = money(spends.totalMinor),
                    count = "${spends.count} txns",
                    isSelected = selectedTab == GroupFilterTab.Spends,
                    accentColor = brand.navy,
                    modifier = Modifier.weight(1f)
                ) { selectedTab = GroupFilterTab.Spends }

                MajorGroupHeroCard(
                    title = "Investment",
                    amount = money(investment.totalMinor),
                    count = "${investment.count} txns",
                    isSelected = selectedTab == GroupFilterTab.Investment,
                    accentColor = Color(0xFF00ACC1),
                    modifier = Modifier.weight(1f)
                ) { selectedTab = GroupFilterTab.Investment }

                MajorGroupHeroCard(
                    title = "Income",
                    amount = money(income.totalMinor),
                    count = "${income.count} txns",
                    isSelected = selectedTab == GroupFilterTab.Income,
                    accentColor = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                ) { selectedTab = GroupFilterTab.Income }
            }

            // Filter Tabs row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupFilterTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brand.navy,
                            selectedLabelColor = brand.onAccentLight
                        )
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // White sheet containing the sub-groups list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(scheme.surface)
            ) {
                val groupsToRender = when (selectedTab) {
                    GroupFilterTab.Spends -> listOf(spends)
                    GroupFilterTab.Investment -> listOf(investment)
                    GroupFilterTab.Income -> listOf(income)
                    GroupFilterTab.All -> listOf(spends, investment, income)
                }

                val hasAnyData = groupsToRender.any { it.subGroups.isNotEmpty() }

                if (!hasAnyData) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions recorded for ${selectedTab.label.lowercase()} this month.",
                            color = brand.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp,
                            bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        )
                    ) {
                        groupsToRender.forEach { majorGroup ->
                            if (selectedTab == GroupFilterTab.All) {
                                item(key = "header_${majorGroup.group.name}") {
                                    MajorGroupSectionHeader(majorGroup)
                                }
                            }

                            if (majorGroup.subGroups.isEmpty()) {
                                item(key = "empty_${majorGroup.group.name}") {
                                    Text(
                                        "No ${majorGroup.group.label.lowercase()} this month.",
                                        fontSize = 13.sp,
                                        color = brand.textSecondary,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = majorGroup.subGroups,
                                    key = { _, item -> "${majorGroup.group.name}_${item.name}" }
                                ) { index, subGroup ->
                                    val itemKey = "${majorGroup.group.name}_${subGroup.name}"
                                    val isExpanded = expandedItemKey == itemKey
                                    val visual = categoryVisualFor(subGroup.name)
                                    val fraction = if (majorGroup.totalMinor > 0) {
                                        (subGroup.totalMinor.toFloat() / majorGroup.totalMinor).coerceIn(0f, 1f)
                                    } else 0f
                                    val percent = (fraction * 100).toInt()

                                    SubGroupRow(
                                        subGroup = subGroup,
                                        visual = visual,
                                        percent = percent,
                                        fraction = fraction,
                                        isExpanded = isExpanded,
                                        isLast = index == majorGroup.subGroups.lastIndex,
                                        onToggleExpand = {
                                            expandedItemKey = if (isExpanded) null else itemKey
                                        },
                                        onOpenTransaction = onOpenTransaction
                                    )
                                }
                            }

                            if (selectedTab == GroupFilterTab.All) {
                                item(key = "spacer_${majorGroup.group.name}") {
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MajorGroupHeroCard(
    title: String,
    amount: String,
    count: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val brand = LocalBrand.current
    val scheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .clip(cardShape)
            .background(if (isSelected) scheme.surface else scheme.surface.copy(alpha = 0.65f))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accentColor else brand.glassBorder,
                shape = cardShape
            )
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = brand.textSecondary)
        Text(
            amount,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(count, fontSize = 10.sp, color = brand.textSecondary)
    }
}

@Composable
private fun MajorGroupSectionHeader(group: MajorGroupSummary) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            group.group.label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = brand.textSecondary,
            letterSpacing = 1.sp
        )
        Text(
            money(group.totalMinor),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface
        )
    }
}

@Composable
private fun SubGroupRow(
    subGroup: CategorySubGroup,
    visual: CategoryVisual,
    percent: Int,
    fraction: Float,
    isExpanded: Boolean,
    isLast: Boolean,
    onToggleExpand: () -> Unit,
    onOpenTransaction: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val brand = LocalBrand.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggleExpand() }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category badge tile
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(visual.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    visual.badge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = visual.onColor
                )
            }

            // Category title, amount, and proportion bar
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        visual.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Text(
                        money(subGroup.totalMinor),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Progress bar track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(brand.hairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(visual.color)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${subGroup.count} ${if (subGroup.count == 1) "transaction" else "transactions"}",
                        fontSize = 12.sp,
                        color = brand.textSecondary
                    )
                    Text(
                        "$percent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = brand.textSecondary
                    )
                }
            }

            // Expand / collapse arrow
            Text(
                if (isExpanded) "▴" else "▾",
                fontSize = 16.sp,
                color = brand.textSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Expanded transaction list for this sub-group
        if (isExpanded && subGroup.transactions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
            ) {
                subGroup.transactions.forEachIndexed { txnIndex, txn ->
                    TransactionRow(txn) { onOpenTransaction(txn.id) }
                    if (txnIndex < subGroup.transactions.lastIndex) {
                        HorizontalDivider(color = brand.hairline)
                    }
                }
            }
        }

        if (!isLast) {
            HorizontalDivider(color = brand.hairline, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
