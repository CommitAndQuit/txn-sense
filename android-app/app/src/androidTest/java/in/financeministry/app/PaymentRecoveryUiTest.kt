package `in`.financeministry.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import `in`.financeministry.app.feature.PaymentRecoveryPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PaymentRecoveryUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun recovery_requires_an_explicit_choice_and_routes_each_action_once() {
        val actions = mutableListOf<String>()
        rule.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PaymentRecoveryPanel({ actions += "add" }, { actions += "capture" }, { actions += "import" })
            }
        } }
        rule.runOnIdle { assertEquals(emptyList<String>(), actions) }
        rule.onNodeWithText("Add a payment manually").performScrollTo().performClick()
        rule.onNodeWithText("Check SMS capture").performScrollTo().performClick()
        rule.onNodeWithText("Import past messages").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("add", "capture", "import"), actions) }
    }
}
