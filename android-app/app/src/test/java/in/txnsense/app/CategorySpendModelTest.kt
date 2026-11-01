package `in`.txnsense.app

import `in`.txnsense.app.data.*
import `in`.txnsense.app.feature.categoryVisualFor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CategorySpendModelTest {

    @Test
    fun category_visual_mappings_match_expected_labels_and_badges() {
        val food = categoryVisualFor("Food")
        assertEquals("Food", food.label)
        assertEquals("FD", food.badge)

        val groceries = categoryVisualFor("Groceries")
        assertEquals("Groceries", groceries.label)
        assertEquals("GR", groceries.badge)

        val random = categoryVisualFor("Random")
        assertEquals("Random", random.label)
        assertEquals("RD", random.badge)

        val shopping = categoryVisualFor("Shopping")
        assertEquals("Shopping", shopping.label)
        assertEquals("SH", shopping.badge)

        val bills = categoryVisualFor("Bills")
        assertEquals("Bills", bills.label)
        assertEquals("BL", bills.badge)

        val travel = categoryVisualFor("Travel")
        assertEquals("Travel", travel.label)
        assertEquals("TR", travel.badge)

        val entertainment = categoryVisualFor("Entertainment")
        assertEquals("Entertainment", entertainment.label)
        assertEquals("EN", entertainment.badge)

        val health = categoryVisualFor("Health")
        assertEquals("Health", health.label)
        assertEquals("HL", health.badge)

        val education = categoryVisualFor("Education")
        assertEquals("Education", education.label)
        assertEquals("ED", education.badge)

        val cash = categoryVisualFor("Cash")
        assertEquals("Cash", cash.label)
        assertEquals("CS", cash.badge)

        val salary = categoryVisualFor("Salary")
        assertEquals("Salary", salary.label)
        assertEquals("SL", salary.badge)

        val investment = categoryVisualFor("Investment")
        assertEquals("Investment", investment.label)
        assertEquals("IV", investment.badge)

        val other = categoryVisualFor("Other")
        assertEquals("Other", other.label)
        assertEquals("OT", other.badge)

        val custom = categoryVisualFor("CustomCategory")
        assertEquals("CustomCategory", custom.label)
        assertEquals("CU", custom.badge)

        val blank = categoryVisualFor("  ")
        assertEquals("Other", blank.label)
        assertEquals("OT", blank.badge)
    }

    @Test
    fun major_groups_and_sub_groups_data_classes_hold_aggregates_properly() {
        val sub1 = CategorySubGroup(name = "Food", totalMinor = 150000L, count = 5)
        val sub2 = CategorySubGroup(name = "Groceries", totalMinor = 50000L, count = 2)
        val spends = MajorGroupSummary(
            group = MajorGroup.Spends,
            totalMinor = 200000L,
            count = 7,
            subGroups = listOf(sub1, sub2)
        )
        val investment = MajorGroupSummary(
            group = MajorGroup.Investment,
            totalMinor = 100000L,
            count = 1,
            subGroups = listOf(CategorySubGroup("Stocks", 100000L, 1))
        )
        val income = MajorGroupSummary(
            group = MajorGroup.Income,
            totalMinor = 500000L,
            count = 1,
            subGroups = listOf(CategorySubGroup("Salary", 500000L, 1))
        )

        val breakdown = MonthCategoryBreakdown(
            month = LocalDate.of(2026, 11, 1),
            spends = spends,
            investment = investment,
            income = income
        )

        assertEquals(MajorGroup.Spends, breakdown.spends.group)
        assertEquals(200000L, breakdown.spends.totalMinor)
        assertEquals(2, breakdown.spends.subGroups.size)
        assertEquals("Food", breakdown.spends.subGroups[0].name)
        assertEquals("Groceries", breakdown.spends.subGroups[1].name)

        assertEquals(MajorGroup.Investment, breakdown.investment.group)
        assertEquals(100000L, breakdown.investment.totalMinor)

        assertEquals(MajorGroup.Income, breakdown.income.group)
        assertEquals(500000L, breakdown.income.totalMinor)
    }
}
