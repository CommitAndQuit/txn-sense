package `in`.financeministry.app

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import `in`.financeministry.app.core.model.*
import `in`.financeministry.app.data.*
import `in`.financeministry.app.ui.FinanceLedgerTheme
import `in`.financeministry.app.ui.MonthlyMathSheet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class CompactMonthlyBreakdownUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun explanations_are_optional_and_totals_survive_theme_and_large_text_changes() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = TransactionRepository(app, "compact_month_${UUID.randomUUID()}")
        try {
            runBlocking {
                val expense = repo.save(ManualInput("124500.75", Direction.Debit, System.currentTimeMillis(),
                    TransactionType.Other, ownership = SpendingOwnership.Group, groupLabel = "Demo group", personalShare = "102400.50"))
                repo.recordRepayment(expense, "20850", System.currentTimeMillis(), "Demo person")
                repo.save(ManualInput("350.75", Direction.Credit, System.currentTimeMillis(), TransactionType.Refund))
            }
            val before = runBlocking { repo.snapshot() }
            var dark by mutableStateOf(false)
            val larger = InstrumentationRegistry.getArguments().getString("largeText") == "true"
            if (larger) assertTrue("Large-text run requires the emulator's actual Android font scale", app.resources.configuration.fontScale >= 1.5f)
            var openedCategories = 0
            rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
            rule.setContent {
                FinanceLedgerTheme(dark) {
                    Surface(Modifier.fillMaxSize()) {
                        MonthlyMathSheet(repo, before, {}, { openedCategories++ })
                    }
                }
            }
            for (mode in listOf(false, true)) {
                rule.runOnIdle { dark = mode }
                rule.waitForIdle()
                rule.waitUntil(15000) { rule.onAllNodes(hasText("Refunds received") and hasText("₹350.75")).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Your spending = money out − others’ shares.").assertDoesNotExist()
                rule.onNode(hasText("Others’ share") and hasText("− ₹22,100.25")).assertExists()
                rule.onNode(hasText("Still owed · all dates") and hasText("₹1,250.25")).assertExists()
                rule.onNodeWithText("Got it").assertIsDisplayed()
                rule.mainClock.advanceTimeBy(500)
                rule.waitForIdle()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val bitmap = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
                try {
                    File(instrumentation.targetContext.getExternalFilesDir("compact-month"),
                        "month-${if (mode) "dark" else "light"}${if (larger) "-large" else ""}.png")
                        .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally { bitmap.recycle() }
                rule.onNodeWithText("How it’s calculated").performScrollTo().performClick()
                rule.onNodeWithText("Your spending = money out − others’ shares.").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("How it’s calculated").performScrollTo().performClick()
                rule.onNodeWithText("Your spending = money out − others’ shares.").assertDoesNotExist()
                rule.onNodeWithText("See spending by category").performScrollTo().performClick()
            }
            assertEquals(2, openedCategories)
            assertEquals(before, runBlocking { repo.snapshot() })
        } finally { runBlocking { repo.eraseAll(); repo.close() } }
    }
}
