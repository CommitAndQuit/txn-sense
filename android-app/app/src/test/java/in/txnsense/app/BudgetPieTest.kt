package `in`.txnsense.app

import `in`.txnsense.app.feature.calculateBudgetPieLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetPieTest {

    @Test
    fun under_budget_displays_single_ring_and_accurate_percentage() {
        val zero = calculateBudgetPieLayout(0f, isOverBudget = false)
        assertEquals(1, zero.ringCount)
        assertEquals(0, zero.percentage)
        assertEquals(listOf(0f), zero.ringProgresses)

        val half = calculateBudgetPieLayout(0.5f, isOverBudget = false)
        assertEquals(1, half.ringCount)
        assertEquals(50, half.percentage)
        assertEquals(listOf(0.5f), half.ringProgresses)

        val full = calculateBudgetPieLayout(1.0f, isOverBudget = false)
        assertEquals(1, full.ringCount)
        assertEquals(100, full.percentage)
        assertEquals(listOf(1.0f), full.ringProgresses)
    }

    @Test
    fun over_budget_displays_multiple_turns() {
        // 140% -> 1 full turn + 0.4 on second turn
        val over1 = calculateBudgetPieLayout(1.4f, isOverBudget = true)
        assertEquals(2, over1.ringCount)
        assertEquals(140, over1.percentage)
        assertEquals(1.0f, over1.ringProgresses[0], 0.001f)
        assertEquals(0.4f, over1.ringProgresses[1], 0.001f)

        // 200% -> 2 full turns
        val over2 = calculateBudgetPieLayout(2.0f, isOverBudget = true)
        assertEquals(2, over2.ringCount)
        assertEquals(200, over2.percentage)
        assertEquals(1.0f, over2.ringProgresses[0], 0.001f)
        assertEquals(1.0f, over2.ringProgresses[1], 0.001f)

        // 260% -> 2 full turns + 0.6 on third turn
        val over3 = calculateBudgetPieLayout(2.6f, isOverBudget = true)
        assertEquals(3, over3.ringCount)
        assertEquals(260, over3.percentage)
        assertEquals(1.0f, over3.ringProgresses[0], 0.001f)
        assertEquals(1.0f, over3.ringProgresses[1], 0.001f)
        assertEquals(0.6f, over3.ringProgresses[2], 0.001f)

        // 450% -> capped to 3 rings, innermost showing fractional progress
        val overLarge = calculateBudgetPieLayout(4.5f, isOverBudget = true)
        assertEquals(3, overLarge.ringCount)
        assertEquals(450, overLarge.percentage)
        assertEquals(1.0f, overLarge.ringProgresses[0], 0.001f)
        assertEquals(1.0f, overLarge.ringProgresses[1], 0.001f)
        assertEquals(0.5f, overLarge.ringProgresses[2], 0.001f)
    }
}
