package app.airgap.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardFieldClassifierTest {

    private fun byText(vararg texts: String) = CardFieldClassifier.classify(emptyList(), null, texts.toList())

    @Test
    fun `android autofill hints win`() {
        assertEquals(CardField.NUMBER, CardFieldClassifier.classify(listOf("creditCardNumber"), null, listOf("cvv")))
        assertEquals(CardField.SECURITY_CODE, CardFieldClassifier.classify(listOf("creditCardSecurityCode"), null, emptyList()))
        assertEquals(CardField.EXPIRY_MONTH, CardFieldClassifier.classify(listOf("creditCardExpirationMonth"), null, emptyList()))
        assertEquals(CardField.EXPIRY_YEAR, CardFieldClassifier.classify(listOf("creditCardExpirationYear"), null, emptyList()))
        assertEquals(CardField.EXPIRY_DATE, CardFieldClassifier.classify(listOf("creditCardExpirationDate"), null, emptyList()))
    }

    @Test
    fun `html autocomplete tokens are recognised, including sections`() {
        assertEquals(CardField.NUMBER, CardFieldClassifier.classify(emptyList(), "cc-number", emptyList()))
        assertEquals(CardField.NUMBER, CardFieldClassifier.classify(emptyList(), "section-pay billing cc-number", emptyList()))
        assertEquals(CardField.EXPIRY_DATE, CardFieldClassifier.classify(emptyList(), "cc-exp", emptyList()))
        assertEquals(CardField.EXPIRY_MONTH, CardFieldClassifier.classify(emptyList(), "cc-exp-month", emptyList()))
        assertEquals(CardField.EXPIRY_YEAR, CardFieldClassifier.classify(emptyList(), "cc-exp-year", emptyList()))
        assertEquals(CardField.SECURITY_CODE, CardFieldClassifier.classify(emptyList(), "cc-csc", emptyList()))
        assertEquals(CardField.HOLDER_NAME, CardFieldClassifier.classify(emptyList(), "cc-name", emptyList()))
    }

    @Test
    fun `unlabelled fields are matched by id, name and placeholder`() {
        assertEquals(CardField.NUMBER, byText("et_card_number"))
        assertEquals(CardField.NUMBER, byText("cardNumber"))
        assertEquals(CardField.NUMBER, byText("ccnum"))
        assertEquals(CardField.NUMBER, byText("Card no"))
        assertEquals(CardField.SECURITY_CODE, byText("et_cvv"))
        assertEquals(CardField.SECURITY_CODE, byText("CVC"))
        assertEquals(CardField.SECURITY_CODE, byText("Security code"))
        assertEquals(CardField.EXPIRY_DATE, byText("MM / YY"))
        assertEquals(CardField.EXPIRY_DATE, byText("expiry_date"))
        assertEquals(CardField.EXPIRY_DATE, byText("Valid thru"))
        assertEquals(CardField.EXPIRY_DATE, byText("Expiry"))
        assertEquals(CardField.EXPIRY_MONTH, byText("exp_month"))
        assertEquals(CardField.EXPIRY_MONTH, byText("MM"))
        assertEquals(CardField.EXPIRY_YEAR, byText("expiryYear"))
        assertEquals(CardField.EXPIRY_YEAR, byText("YYYY"))
        assertEquals(CardField.HOLDER_NAME, byText("Name on card"))
        assertEquals(CardField.HOLDER_NAME, byText("cardholder"))
    }

    @Test
    fun `ordinary fields are left alone`() {
        assertNull(byText("email"))
        assertNull(byText("password"))
        assertNull(byText("first_name"))
        assertNull(byText("company"))
        assertNull(byText("search"))
        assertNull(byText("expand"))
        assertNull(byText("panel_title"))
        assertNull(byText(""))
    }
}
