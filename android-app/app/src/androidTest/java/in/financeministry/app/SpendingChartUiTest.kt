package `in`.financeministry.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import `in`.financeministry.app.data.CategorySpending
import `in`.financeministry.app.ui.SpendingDonut
import `in`.financeministry.app.ui.FinanceLedgerTheme
import `in`.financeministry.app.ui.LedgerHomeSummary
import `in`.financeministry.app.data.LedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.io.File
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry

class SpendingChartUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun selecting_category_exposes_amount_and_opens_exact_filter() {
        var opened = emptyList<String>()
        rule.setContent {
            MaterialTheme {
                SpendingDonut(listOf(CategorySpending("Food", BigInteger.valueOf(60000), emptyList()),
                    CategorySpending("Bills", BigInteger.valueOf(40000), emptyList())), onOpen = { opened = it })
            }
        }
        rule.onNodeWithContentDescription("Select Food, 60% of spending").performClick()
        rule.onNodeWithText("₹600.00").assertExists()
        rule.onNodeWithText("View Food expenses").performClick()
        assertEquals(listOf("Food"), opened)
    }

    @Test fun grouped_tail_opens_all_and_only_its_categories() {
        var opened = emptyList<String>()
        val categories = listOf("A" to 60L, "B" to 50L, "C" to 40L, "D" to 30L,
            "E" to 20L, "Other" to 10L, "F" to 1L).map { (name, amount) ->
            CategorySpending(name, BigInteger.valueOf(amount), emptyList())
        }
        rule.setContent { MaterialTheme { SpendingDonut(categories, onOpen = { opened = it }) } }
        rule.onNodeWithContentDescription("Select Other categories, 5% of spending").performClick()
        rule.onNodeWithText("₹0.11").assertExists()
        rule.onNodeWithText("View Other categories expenses").performClick()
        assertEquals(listOf("Other", "F"), opened)
    }

    @Test fun empty_spending_does_not_offer_fake_segments() {
        rule.setContent { MaterialTheme { SpendingDonut(emptyList(), onOpen = {}) } }
        rule.onNodeWithText("No spending to chart yet").assertExists()
        rule.onNodeWithText("View expenses").assertDoesNotExist()
    }

    @Test fun home_keeps_cash_flow_separate_in_light_and_dark() {
        val categories = listOf("Food" to 420000L, "Grocery" to 300000L, "Bills" to 220000L,
            "Transport" to 140000L, "Entertainment" to 100000L, "Other" to 40000L)
            .map { (name, amount) -> CategorySpending(name, BigInteger.valueOf(amount), emptyList()) }
        val snapshot = LedgerSnapshot(emptyList(), BigInteger.valueOf(1500000), BigInteger.valueOf(5000000),
            personalSpend = BigInteger.valueOf(1220000), outstandingRepayments = BigInteger.valueOf(300000),
            selectedMonthOutstandingRepayments = BigInteger.valueOf(100000), spendingCategories = categories)
        var dark by mutableStateOf(false)
        var largeText by mutableStateOf(false)
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (largeText) 1.5f else 1f)) {
            FinanceLedgerTheme(dark) { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                LedgerHomeSummary(snapshot, "2 people", {}, {}, {})
            } } }
            }
        }
        for ((mode, large) in listOf(false to false, true to false, true to true)) {
            rule.runOnIdle { dark = mode; largeText = large }
            rule.onNodeWithText("Money out").assertExists()
            rule.onNodeWithText("Money in").assertExists()
            rule.onNodeWithText("View expenses").assertDoesNotExist()
            rule.onNodeWithText("Spending breakdown").assertExists()
            rule.onNodeWithContentDescription("Category spending overview").assertDoesNotExist()
            rule.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(instrumentation.targetContext.getExternalFilesDir("chart-review"), if (large) "home-large-text.png" else if (mode) "home-dark.png" else "home-light.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
