package app.airgap.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.airgap.vault.Card
import app.airgap.vault.CardColor
import app.airgap.vault.CardKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardListScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val card = Card(
        id = "c1",
        nickname = "HDFC Regalia",
        holderName = "TEST BUYER",
        number = "4111111111111111",
        expiryMonth = 3,
        expiryYear = 2029,
        cvv = "123",
        kind = CardKind.CREDIT,
        color = CardColor.NAVY,
        dueDay = 5,
        helpline = "18002026161",
        blockSmsNumber = "7308080808",
    )

    private fun show(vararg cards: Card) = rule.setContent {
        AirgapTheme {
            CardListScreen(cards = cards.toList(), onAdd = {}, onEdit = {}, onCopy = { _, _ -> }, onLock = {})
        }
    }

    @Test
    fun cardsStayMaskedUntilTapped() {
        show(card)
        rule.onNodeWithText("•••• 1111").assertIsDisplayed()
        assertEquals(0, rule.onAllNodes(hasText("4111 1111 1111 1111")).fetchSemanticsNodes().size)
        rule.onNodeWithText("•••• 1111").performClick()
        rule.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
        rule.onNodeWithText("CVV 123").assertIsDisplayed()
        rule.onNodeWithText("Bill due on the 5th").assertIsDisplayed()
    }

    @Test
    fun lostCardDialogOffersCallAndBlockSms() {
        show(card)
        rule.onNodeWithText("•••• 1111").performClick()
        rule.onNodeWithText("Lost card?").performClick()
        rule.onNodeWithText("Call bank: 18002026161").assertIsDisplayed()
        rule.onNodeWithText("Text \"BLOCK 1111\" to 7308080808").assertIsDisplayed()
    }

    @Test
    fun lostCardDialogExplainsWhenNoNumbersSaved() {
        show(card.copy(helpline = "", blockSmsNumber = ""))
        rule.onNodeWithText("•••• 1111").performClick()
        rule.onNodeWithText("Lost card?").performClick()
        rule.onNode(hasText("No bank numbers saved for this card yet", substring = true)).assertIsDisplayed()
    }
}
