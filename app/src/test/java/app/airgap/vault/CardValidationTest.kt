package app.airgap.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class CardValidationTest {

    @Test
    fun `luhn accepts known test numbers`() {
        listOf(
            "4111111111111111",
            "5555555555554444",
            "2223003122003222",
            "378282246310005",
            "6011111111111117",
            "36227206271667",
            "3530111333300000",
        ).forEach { assertTrue(it, CardValidation.isLuhnValid(it)) }
    }

    @Test
    fun `luhn rejects typos, bad lengths and non-digits`() {
        assertFalse(CardValidation.isLuhnValid("4111111111111112"))
        assertFalse(CardValidation.isLuhnValid("41111111111"))
        assertFalse(CardValidation.isLuhnValid("4111 1111 1111 1111"))
        assertFalse(CardValidation.isLuhnValid(""))
    }

    @Test
    fun `detects networks from prefixes`() {
        assertEquals(CardNetwork.VISA, CardNetwork.detect("4111111111111111"))
        assertEquals(CardNetwork.MASTERCARD, CardNetwork.detect("5555555555554444"))
        assertEquals(CardNetwork.MASTERCARD, CardNetwork.detect("2223003122003222"))
        assertEquals(CardNetwork.AMEX, CardNetwork.detect("378282246310005"))
        assertEquals(CardNetwork.DISCOVER, CardNetwork.detect("6011111111111117"))
        assertEquals(CardNetwork.RUPAY, CardNetwork.detect("6521000000000000"))
        assertEquals(CardNetwork.RUPAY, CardNetwork.detect("6070000000000000"))
        assertEquals(CardNetwork.DINERS, CardNetwork.detect("36227206271667"))
        assertEquals(CardNetwork.JCB, CardNetwork.detect("3530111333300000"))
        assertEquals(CardNetwork.UNKNOWN, CardNetwork.detect("9999"))
        assertEquals(CardNetwork.UNKNOWN, CardNetwork.detect(""))
    }

    @Test
    fun `cvv is optional but must match network length`() {
        assertTrue(CardValidation.isCvvValid("", CardNetwork.VISA))
        assertTrue(CardValidation.isCvvValid("123", CardNetwork.VISA))
        assertFalse(CardValidation.isCvvValid("1234", CardNetwork.VISA))
        assertTrue(CardValidation.isCvvValid("1234", CardNetwork.AMEX))
        assertFalse(CardValidation.isCvvValid("12a", CardNetwork.VISA))
    }

    @Test
    fun `formats numbers in printed groups`() {
        assertEquals("4111 1111 1111 1111", CardValidation.formatNumber("4111111111111111"))
        assertEquals("3782 822463 10005", CardValidation.formatNumber("378282246310005"))
        assertEquals("6521 0000 0000 0000 123", CardValidation.formatNumber("6521000000000000123"))
    }

    @Test
    fun `expiry is checked by month`() {
        val now = YearMonth.of(2026, 10)
        assertFalse(CardValidation.isExpired(10, 2026, now))
        assertTrue(CardValidation.isExpired(9, 2026, now))
        assertEquals("03/29", CardValidation.formatExpiry(3, 2029))
    }

    @Test
    fun `toString never contains the full number`() {
        val card = Card("id", "", "A B", "4111111111111111", 1, 2030, "123", CardKind.CREDIT)
        assertFalse(card.toString().contains("4111111111111111"))
    }
}
