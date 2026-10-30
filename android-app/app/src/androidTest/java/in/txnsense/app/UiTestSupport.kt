package `in`.txnsense.app

import androidx.compose.ui.test.*

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule

/**
 * The home screen opens Settings from its gear. The detailed ledger is only rendered while it has
 * content (settings, a form, a selected transaction, or the review queue), so open it through the
 * real review-queue entry point and switch to the full transaction list.
 */
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openLedger() {
    activityRule.scenario.onActivity { it.requestReview() }
    try {
        waitUntil(15_000) { onAllNodesWithText("+ Add transaction").fetchSemanticsNodes().isNotEmpty() }
    } catch (failure: Throwable) {
        throw AssertionError("Ledger did not open after requestReview(). UI: ${onRoot().printToString()}", failure)
    }
    onNode(hasScrollToNodeAction()).performScrollToNode(hasText("All"))
    waitUntil(15_000) { onAllNodesWithText("All").fetchSemanticsNodes().isNotEmpty() }
    onNodeWithText("All").performClick()
    waitUntil(15_000) {
        onAllNodesWithText("Transactions").fetchSemanticsNodes().isNotEmpty() &&
            onAllNodesWithText("+ Add transaction").fetchSemanticsNodes().isNotEmpty()
    }
}

/** The home screen's gear opens the settings page directly. */
internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openSettings() {
    onNodeWithText("⚙").performClick()
    waitUntil(15_000) { onAllNodesWithText("SMS and past messages").fetchSemanticsNodes().isNotEmpty() }
}
