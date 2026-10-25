package app.airgap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.airgap.nfc.CardRead
import app.airgap.vault.Card
import app.airgap.vault.CardKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardEditScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var saved: Card? = null
    private var tapUsed = false

    private fun show(nfcStatus: NfcStatus = NfcStatus.UNAVAILABLE) {
        rule.setContent {
            AirgapTheme {
                CardEditScreen(
                    existing = null,
                    initialDraft = CardDraft(),
                    onDraftChange = {},
                    nfcStatus = nfcStatus,
                    cardTap = CardTap.Waiting,
                    onCardTapUsed = { tapUsed = true },
                    onOpenNfcSettings = {},
                    onSave = { saved = it },
                    onDelete = {},
                    onCancel = {},
                )
            }
        }
    }

    private fun type(label: String, text: String) = rule.onNodeWithText(label).performScrollTo().performTextInput(text)

    @Test
    fun mistypedNumberIsCaughtAndNotSaved() {
        show()
        type("Card number", "4111111111111112")
        type("Name on card", "TEST")
        type("MM", "03")
        type("YY", "29")
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Check the card number").assertIsDisplayed()
        assertNull(saved)
    }

    @Test
    fun numberIsGroupedWhileTypingAndStoredAsDigits() {
        show()
        type("Card number", "4111111111111111")
        rule.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
        type("Name on card", "TEST BUYER")
        type("MM", "03")
        type("YY", "29")
        type("Bill due day (optional)", "5")
        type("Bank helpline", "18001234")
        rule.onNodeWithText("Save").performClick()

        val card = saved!!
        assertEquals("4111111111111111", card.number)
        assertEquals(3, card.expiryMonth)
        assertEquals(2029, card.expiryYear)
        assertEquals(5, card.dueDay)
        assertEquals("18001234", card.helpline)
        assertEquals("BLOCK 1111", card.defaultBlockSms)
    }

    @Test
    fun dueDayIsOnlyForCreditCards() {
        show()
        rule.onNodeWithText("Bill due day (optional)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Debit").performScrollTo().performClick()
        assertEquals(0, rule.onAllNodes(hasText("Bill due day (optional)")).fetchSemanticsNodes().size)
    }

    @Test
    fun tappedCardFillsNumberExpiryAndName() {
        var tap by mutableStateOf<CardTap>(CardTap.Waiting)
        rule.setContent {
            AirgapTheme {
                CardEditScreen(
                    existing = null,
                    initialDraft = CardDraft(),
                    onDraftChange = {},
                    nfcStatus = NfcStatus.READY,
                    cardTap = tap,
                    onCardTapUsed = { tapUsed = true; tap = CardTap.Waiting },
                    onOpenNfcSettings = {},
                    onSave = { saved = it },
                    onDelete = {},
                    onCancel = {},
                )
            }
        }
        rule.onNodeWithText("Hold your card flat against the back of the phone to fill its number and expiry.").assertIsDisplayed()
        tap = CardTap.Read(CardRead("5555555555554444", 5, 2028, "AAYUSH AGRAWAL"))
        rule.waitForIdle()

        assertTrue(tapUsed)
        rule.onNodeWithText("5555 5555 5555 4444").assertIsDisplayed()
        rule.onNodeWithText("AAYUSH AGRAWAL").assertIsDisplayed()
        rule.onNodeWithText("Card read. Add the CVV yourself: cards don't share it.").assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        assertEquals(5, saved!!.expiryMonth)
        assertEquals(2028, saved!!.expiryYear)
        assertEquals(CardKind.CREDIT, saved!!.kind)
    }

    @Test
    fun nfcOffOffersSettings() {
        show(nfcStatus = NfcStatus.OFF)
        rule.onNodeWithText("Open NFC settings").assertIsDisplayed()
    }

}
