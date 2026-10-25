package app.airgap.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstRunScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun continueNeedsTheNoBackupAcknowledgement() {
        var continued = false
        rule.setContent { AirgapTheme { FirstRunScreen(onContinue = { continued = true }) } }

        rule.onNodeWithText("There's no backup.").performScrollTo()
        rule.onNodeWithText("Continue").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("I understand there's no backup").performScrollTo().performClick()
        rule.onNodeWithText("Continue").assertIsEnabled().performClick()
        assertTrue(continued)
    }
}
