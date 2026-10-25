package `in`.financeministry.app

import `in`.financeministry.app.data.CategorySpending
import `in`.financeministry.app.ui.spendingChartSlices
import `in`.financeministry.app.ui.spendingPeriodLabel
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class SpendingChartTest {
    @Test fun period_label_matches_month_and_year_not_day_of_month() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals("This month · your spending", spendingPeriodLabel(LocalDate.of(2026, 10, 1), today))
        assertFalse(spendingPeriodLabel(LocalDate.of(2025, 10, 1), today).startsWith("This month"))
        assertTrue(spendingPeriodLabel(LocalDate.of(2025, 10, 1), today).contains("2025"))
        assertFalse(spendingPeriodLabel(LocalDate.of(2026, 9, 1), today).startsWith("This month"))
    }
    private fun category(name: String, amount: Long) = CategorySpending(name, BigInteger.valueOf(amount), emptyList())

    @Test fun groups_tail_without_losing_paise_or_confusing_uncategorized() {
        val slices = spendingChartSlices(listOf(category("A", 60), category("B", 50), category("C", 40),
            category("D", 30), category("E", 20), category("Other", 10), category("F", 1)))
        assertEquals(6, slices.size)
        assertEquals(BigInteger.valueOf(211), slices.fold(BigInteger.ZERO) { sum, item -> sum + item.amount })
        assertEquals("Other categories", slices.last().label)
        assertEquals(listOf("Other", "F"), slices.last().categories)
        assertEquals(BigInteger.valueOf(11), slices.last().amount)
    }

    @Test fun sorts_deterministically_and_does_not_draw_zero_shares() {
        val slices = spendingChartSlices(listOf(category("Z", 20), category("A", 20), category("Owed", 0)))
        assertEquals(listOf("A", "Z"), slices.map { it.label })
        assertEquals(0.5f, slices.first().fraction, 0.0001f)
        assertTrue(spendingChartSlices(emptyList()).isEmpty())
        assertTrue(spendingChartSlices(listOf(category("Owed", 0))).isEmpty())
    }

    @Test fun huge_totals_keep_finite_proportions() {
        val huge = BigInteger.TEN.pow(80)
        val slices = spendingChartSlices(listOf(CategorySpending("A", huge, emptyList()), CategorySpending("B", huge, emptyList())))
        assertEquals(0.5f, slices.first().fraction, 0.0001f)
    }
}
