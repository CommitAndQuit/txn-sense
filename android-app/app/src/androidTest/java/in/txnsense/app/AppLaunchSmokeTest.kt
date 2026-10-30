package `in`.txnsense.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchSmokeTest {
    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchRendersPrivateAlphaShell() {
        activityRule.onNodeWithText("Your spending by payment method").assertIsDisplayed()
        activityRule.onNodeWithTag("home_refresh").assertIsDisplayed()
    }
}
