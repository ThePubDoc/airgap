package app.airgap.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyCheckTest {

    @get:Rule
    val rule = createComposeRule()

    /** The real installed app, as Android reports it: the claim the screen makes must hold. */
    @Test
    fun installedAppHasNoNetworkPermissionAndNoBackups() {
        val report = PrivacyReport.from(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals(emptyList<String>(), report.networkPermissions)
        assertTrue(report.noNetworkAccess)
        assertFalse(report.backupAllowed)
        assertNotNull(report.signingSha256)
        assertTrue(report.permissions.any { it.name == "android.permission.NFC" })
    }

    @Test
    fun cleanReportSaysNoInternetAccess() {
        rule.setContent { AirgapTheme { PrivacyCheckScreen(report(network = emptyList()), onBack = {}) } }
        rule.onNodeWithText("✓ No internet access").assertIsDisplayed()
        rule.onNodeWithText("Backups turned off").assertIsDisplayed()
    }

    @Test
    fun tamperedBuildIsFlagged() {
        rule.setContent { AirgapTheme { PrivacyCheckScreen(report(network = listOf("android.permission.INTERNET")), onBack = {}) } }
        rule.onNodeWithText("✗ Network permission found").assertIsDisplayed()
    }

    private fun report(network: List<String>) = PrivacyReport(
        permissions = listOf(PermissionLine("android.permission.NFC", "Read a card you tap on the phone", granted = true)),
        networkPermissions = network,
        backupAllowed = false,
        debuggable = false,
        signingSha256 = "BD:35",
        versionName = "0.2.0",
        isAutofillService = false,
    )
}
